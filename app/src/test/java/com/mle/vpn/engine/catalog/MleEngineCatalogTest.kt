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
    fun importantEnginesHaveDedicatedCatalogEntries() {
        val ids = MleEngineCatalog.items.map { it.id }.toSet()
        assertTrue("npv" in ids)
        assertTrue("openvpn" in ids)
        assertTrue("gereh" in ids)
        assertTrue("warp" in ids)
    }
}
