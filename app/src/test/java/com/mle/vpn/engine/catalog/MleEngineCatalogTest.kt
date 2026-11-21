package com.mle.vpn.engine.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MleEngineCatalogTest {
    @Test
    fun engineIdsAndIconKeysAreUnique() {
        val items = MleEngineCatalog.items
        assertEquals(items.size, items.map { it.id }.toSet().size)
        assertEquals(items.size, items.map { it.iconKey }.toSet().size)
    }

    @Test
    fun existingSingBoxProtocolsAreNotMarkedAsUnimplemented() {
        val implemented = MleEngineCatalog.items
            .filter { it.id in setOf("vless", "vmess", "trojan", "shadowsocks", "wireguard", "hysteria2", "tuic", "outline") }
        assertEquals(8, implemented.size)
        assertTrue(implemented.all { it.integration == Integration.EXISTING_SING_BOX_PATH })
    }

    @Test
    fun importantEnginesHaveDedicatedCatalogEntries() {
        val ids = MleEngineCatalog.items.map { it.id }.toSet()
        assertTrue("npv" in ids)
        assertTrue("openvpn" in ids)
        assertTrue("gereh" in ids)
        assertTrue("warp" in ids)
        assertTrue("tuic" in ids)
        assertTrue("cloudflare-doctor" in ids)
        assertEquals(Integration.DIAGNOSTIC_ONLY, MleEngineCatalog.find("cloudflare-doctor")?.integration)
    }
}
