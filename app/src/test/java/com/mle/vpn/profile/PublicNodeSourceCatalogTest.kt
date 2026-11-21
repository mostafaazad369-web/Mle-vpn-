package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicNodeSourceCatalogTest {
    @Test
    fun hasUniqueSourcesWithPinnedHttpsHostsAndBoundedLimits() {
        val sources = PublicNodeSourceCatalog.all
        assertTrue(sources.isNotEmpty())
        assertEquals(sources.size, sources.map { it.id }.distinct().size)
        sources.forEach { source ->
            assertTrue(source.primaryUrl.startsWith("https://raw.githubusercontent.com/"))
            assertTrue(source.mirrorUrl.startsWith("https://cdn.jsdelivr.net/gh/"))
            assertTrue(source.maxBytes in 1..(2 * 1024 * 1024))
            assertTrue(source.maxNodes in 1..500)
        }
    }

    @Test
    fun includesSupportedWireGuardAndWarpFeedsButNotUnimplementedEngines() {
        val ids = PublicNodeSourceCatalog.all.map { it.id }.toSet()
        assertTrue("amz-morpheus-wg" in ids)
        assertTrue("amz-vcc-wg" in ids)
        assertTrue("amz-argh94-wg" in ids)
        assertTrue("amz-argh94-auto-wg" in ids)
        assertTrue("amz-vcf-wg" in ids)
        assertTrue("amz-submod-wg" in ids)
        assertTrue("amz-warp-links" in ids)
        assertFalse(ids.any { it.contains("psiphon", true) || it.contains("tor", true) || it.contains("geph", true) })
    }

    @Test
    fun distinguishesOnlineNotModifiedValidationFromOfflineStaleCache() {
        val revalidated = PublicSourceFetchResult(
            text = "vless://example",
            fromCache = true,
            sourceUrl = "https://raw.githubusercontent.com/example/repo/main/sub.txt",
            networkValidated = true
        )
        val staleFallback = PublicSourceFetchResult(
            text = "vless://example",
            fromCache = true,
            sourceUrl = "https://raw.githubusercontent.com/example/repo/main/sub.txt",
            networkValidated = false
        )
        assertTrue(revalidated.fromCache)
        assertTrue(revalidated.networkValidated)
        assertTrue(staleFallback.fromCache)
        assertTrue(!staleFallback.networkValidated)
        val freshDownload = PublicSourceFetchResult(
            text = "vless://example",
            fromCache = false,
            sourceUrl = "https://raw.githubusercontent.com/example/repo/main/sub.txt"
        )
        assertTrue(freshDownload.networkValidated)
    }

    @Test
    fun mirrorsMlmFluxFeedsAndOmitsNonCatalogAdditions() {
        val ids = PublicNodeSourceCatalog.all.map { it.id }.toSet()
        assertFalse("flux-mg-vmess" in ids)
        assertFalse("flux-mg-shadowsocks" in ids)
        assertFalse("amz-morpheus-mini" in ids)
        assertFalse("amz-morpheus-lite" in ids)
        assertFalse("amz-fastnodes-top" in ids)
        assertFalse("amz-fastnodes-verified" in ids)
        assertTrue("amz-morpheus-hy2" in ids)
        assertTrue("amz-fastnodes-hy2" in ids)
        assertTrue("amz-vpnconfig-hy2" in ids)
        assertTrue("amz-proxylist-hy2" in ids)
        assertTrue("amz-autoconfig-hy2" in ids)
        assertTrue("amz-vpnclash-hy2" in ids)
        assertTrue("amz-submod-hy2" in ids)
        assertTrue("amz-morpheus-wg" in ids)
        assertTrue("amz-warp-links" in ids)
        assertTrue("flux-mg-vless" in ids)
        assertTrue("flux-mg-trojan" in ids)
        assertTrue("flux-mg-hy2" in ids)
        assertTrue("quickconnect-global" in ids)
        assertTrue("quickconnect-verified" in ids)
        assertTrue("quickconnect-fast" in ids)
        assertTrue("quickconnect-secure" in ids)
        assertTrue("quickconnect-all" in ids)
        assertTrue(ids.none { it.contains("openvpn", true) || it.contains("psiphon", true) || it.contains("tor", true) || it.contains("geph", true) })
    }

    @Test
    fun sourceLookupIsStable() {
        val source = PublicNodeSourceCatalog.all.first()
        assertEquals(source, PublicNodeSourceCatalog.find(source.id))
        assertEquals(null, PublicNodeSourceCatalog.find("not-a-real-source"))
    }
}
