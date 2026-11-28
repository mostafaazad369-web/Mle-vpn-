package com.mle.vpn.engine

/**
 * Applies sing-box per-application routing rules to Android's VPN Builder.
 * A failed rule must abort TUN establishment: silently skipping it could route
 * traffic outside the policy the user selected.
 */
internal class ApplicationRuleException(message: String) : IllegalStateException(message)

internal fun applyPackageRules(
    include: Iterator<String>,
    exclude: Iterator<String>,
    addAllowed: (String) -> Unit,
    addDisallowed: (String) -> Unit
) {
    while (include.hasNext()) {
        val packageName = include.next()
        try {
            addAllowed(packageName)
        } catch (_: Exception) {
            throw ApplicationRuleException("Android rejected an included application rule.")
        }
    }
    while (exclude.hasNext()) {
        val packageName = exclude.next()
        try {
            addDisallowed(packageName)
        } catch (_: Exception) {
            throw ApplicationRuleException("Android rejected an excluded application rule.")
        }
    }
}
