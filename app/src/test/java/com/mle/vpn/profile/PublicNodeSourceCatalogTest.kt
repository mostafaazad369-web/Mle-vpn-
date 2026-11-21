package com.mle.vpn.profile

import org.junit.Assert.assertEquals
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
    fun doesNotAdvertiseUnimplementedWireGuardOrUnlicensedEnginesAsFeeds() {
        assertTrue(PublicNodeSourceCatalog.all.none { it.id.contains("wireguard", ignoreCase = true) })
        assertTrue(PublicNodeSourceCatalog.all.none { it.id.contains("warp", ignoreCase = true) })
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
    fun includesOnlyAdditionalFeedsForProtocolsWithLocalRuntimeAdapters() {
        val ids = PublicNodeSourceCatalog.all.map { it.id }.toSet()
        assertTrue("flux-mg-vmess" in ids)
        assertTrue("flux-mg-shadowsocks" in ids)
        assertTrue("flux-mg-vless" in ids)
        assertTrue("flux-mg-trojan" in ids)
        assertTrue("flux-mg-hy2" in ids)
        assertTrue(ids.none { it.contains("openvpn", true) || it.contains("psiphon", true) || it.contains("tor", true) || it.contains("geph", true) })
    }

    @Test
    fun sourceLookupIsStable() {
        val source = PublicNodeSourceCatalog.all.first()
        assertEquals(source, PublicNodeSourceCatalog.find(source.id))
        assertEquals(null, PublicNodeSourceCatalog.find("not-a-real-source"))
    }
}
