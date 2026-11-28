package com.mle.vpn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApplicationRuleApplierTest {
    @Test fun appliesAllIncludeAndExcludeRules() {
        val allowed = mutableListOf<String>()
        val denied = mutableListOf<String>()
        applyPackageRules(
            MleStringIterator(listOf("com.example.one", "com.example.two")),
            MleStringIterator(listOf("com.example.three")),
            allowed::add,
            denied::add
        )
        assertEquals(listOf("com.example.one", "com.example.two"), allowed)
        assertEquals(listOf("com.example.three"), denied)
    }

    @Test fun includeRuleFailureAbortsInsteadOfFailingOpen() {
        val error = assertThrows(ApplicationRuleException::class.java) {
            applyPackageRules(
                MleStringIterator(listOf("com.example.blocked")),
                MleStringIterator(emptyList()),
                { throw IllegalArgumentException("platform detail") },
                {}
            )
        }
        assertEquals("Android rejected an included application rule.", error.message)
    }

    @Test fun excludeRuleFailureAbortsInsteadOfFailingOpen() {
        val error = assertThrows(ApplicationRuleException::class.java) {
            applyPackageRules(
                MleStringIterator(emptyList()),
                MleStringIterator(listOf("com.example.excluded")),
                {},
                { throw IllegalArgumentException("platform detail") }
            )
        }
        assertEquals("Android rejected an excluded application rule.", error.message)
    }
}
