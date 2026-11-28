package com.mle.vpn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApplicationRuleApplierTest {
    @Test fun appliesAllIncludeAndExcludeRules() {
        val allowed = mutableListOf<String>()
        val denied = mutableListOf<String>()
        applyPackageRules(
            listOf("com.example.one", "com.example.two").iterator(),
            listOf("com.example.three").iterator(),
            allowed::add,
            denied::add
        )
        assertEquals(listOf("com.example.one", "com.example.two"), allowed)
        assertEquals(listOf("com.example.three"), denied)
    }

    @Test fun includeRuleFailureAbortsInsteadOfFailingOpen() {
        val error = assertThrows(ApplicationRuleException::class.java) {
            applyPackageRules(
                listOf("com.example.blocked").iterator(),
                emptyList<String>().iterator(),
                { throw IllegalArgumentException("platform detail") },
                {}
            )
        }
        assertEquals("Android rejected an included application rule.", error.message)
    }

    @Test fun excludeRuleFailureAbortsInsteadOfFailingOpen() {
        val error = assertThrows(ApplicationRuleException::class.java) {
            applyPackageRules(
                emptyList<String>().iterator(),
                listOf("com.example.excluded").iterator(),
                {},
                { throw IllegalArgumentException("platform detail") }
            )
        }
        assertEquals("Android rejected an excluded application rule.", error.message)
    }
}
