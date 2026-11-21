package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenVpnProfilePreflightTest {
    private val fingerprint = "SHA256:" + List(32) { "A0" }.joinToString(":")
    private val valid = """
        client
        dev tun
        proto udp
        remote vpn.example.net 443 udp
        remote backup.example.net 1194 tcp-client
        remote-cert-tls server
        peer-fingerprint $fingerprint
        auth-user-pass
        nobind
    """.trimIndent()

    @Test
    fun acceptsVerifiedClientProfileAndSummarizesRemotes() {
        val summary = OpenVpnProfilePreflight.inspect(valid)
        assertEquals(2, summary.remotes.size)
        assertEquals(OpenVpnRemote("vpn.example.net", 443, "udp"), summary.remotes[0])
        assertEquals(OpenVpnRemote("backup.example.net", 1194, "tcp-client"), summary.remotes[1])
        assertTrue(summary.usesPeerFingerprint)
        assertFalse(summary.hasCa)
        assertFalse(summary.hasInlineCredentials)
        assertTrue(summary.requiresCredentials)
    }

    @Test
    fun acceptsInlineCredentialsButNeverReturnsTheirContents() {
        val profile = valid.replace(
            "auth-user-pass",
            "<auth-user-pass>\nuser@example.net\nsuper-secret\n</auth-user-pass>"
        )
        val summary = OpenVpnProfilePreflight.inspect(profile)
        assertTrue(summary.hasInlineCredentials)
        assertFalse(summary.requiresCredentials)
        assertFalse(summary.toString().contains("super-secret"))
    }

    @Test
    fun rejectsScriptsPluginsAndManagementDirectives() {
        for (line in listOf("up /tmp/run.sh", "plugin evil.so", "management 127.0.0.1 5555", "script-security 2")) {
            kotlin.runCatching { OpenVpnProfilePreflight.inspect(valid + "\n" + line) }
                .onSuccess { throw AssertionError("Unsafe directive accepted: $line") }
        }
    }

    @Test
    fun rejectsUnknownDirectivesRatherThanSilentlyDroppingThem() {
        kotlin.runCatching { OpenVpnProfilePreflight.inspect(valid + "\nfast-io") }
            .onSuccess { throw AssertionError("Unknown directive was accepted") }
    }

    @Test
    fun rejectsExternalCertificateAndCredentialFiles() {
        for (line in listOf("ca ca.crt", "cert client.crt", "key client.key", "auth-user-pass credentials.txt")) {
            kotlin.runCatching { OpenVpnProfilePreflight.inspect(valid + "\n" + line) }
                .onSuccess { throw AssertionError("External file reference accepted: $line") }
        }
    }

    @Test
    fun rejectsCompressionAndProfilesWithoutServerAuthentication() {
        for (line in listOf("compress lz4", "comp-lzo", "allow-compression yes")) {
            kotlin.runCatching { OpenVpnProfilePreflight.inspect(valid + "\n" + line) }
                .onSuccess { throw AssertionError("Compression directive accepted: $line") }
        }
        val noTlsVerification = valid.lines().filterNot { it.startsWith("remote-cert-tls") || it.startsWith("peer-fingerprint") }.joinToString("\n")
        kotlin.runCatching { OpenVpnProfilePreflight.inspect(noTlsVerification) }
            .onSuccess { throw AssertionError("Profile without peer verification accepted") }
    }

    @Test
    fun rejectsInvalidRemotePortAndMissingTunMode() {
        val badPort = valid.replace("vpn.example.net 443", "vpn.example.net 70000")
        val noTun = valid.replace("dev tun", "dev tap")
        assertTrue(kotlin.runCatching { OpenVpnProfilePreflight.inspect(badPort) }.isFailure)
        assertTrue(kotlin.runCatching { OpenVpnProfilePreflight.inspect(noTun) }.isFailure)
    }

    @Test
    fun enforcesProfileSizeLimit() {
        assertTrue(kotlin.runCatching {
            OpenVpnProfilePreflight.inspect(valid + "\n# " + "x".repeat(OpenVpnProfilePreflight.MAX_BYTES))
        }.isFailure)
    }
}
