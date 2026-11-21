package com.mle.vpn.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileFailoverPlannerTest {
    private fun profile(id: String) = ImportedProfile(
        id = id,
        protocol = ProfileProtocol.VLESS,
        displayName = id,
        host = "$id.example.com",
        port = 443,
        sourceUri = "vless://11111111-2222-3333-4444-555555555555@$id.example.com:443?security=tls"
    )

    @Test
    fun selectedProfileIsAlwaysTriedFirst() {
        val ordered = ProfileFailoverPlanner.order(
            listOf(profile("a"), profile("b"), profile("c")),
            selectedProfileId = "b"
        )
        assertEquals(listOf("b", "a", "c"), ordered.map { it.id })
    }

    @Test
    fun failoverIsBoundedToThreeProfilesByDefault() {
        val ordered = ProfileFailoverPlanner.order(
            listOf(profile("a"), profile("b"), profile("c"), profile("d")),
            selectedProfileId = "a"
        )
        assertEquals(listOf("a", "b", "c"), ordered.map { it.id })
    }

    @Test
    fun supportsTenCandidatesWhenRequested() {
        val profiles = (1..12).map { profile("node-$it") }
        val ordered = ProfileFailoverPlanner.order(
            profiles,
            selectedProfileId = "node-7",
            maxAttempts = 10
        )
        assertEquals(10, ordered.size)
        assertEquals("node-7", ordered.first().id)
    }
    @Test
    fun duplicateProfileIdsAreRemoved() {
        val ordered = ProfileFailoverPlanner.order(
            listOf(profile("a"), profile("a"), profile("b")),
            selectedProfileId = "a"
        )
        assertEquals(listOf("a", "b"), ordered.map { it.id })
    }

    @Test
    fun rejectsUnsafeAttemptLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileFailoverPlanner.order(listOf(profile("a")), "a", maxAttempts = 0)
        }
    }
}
