package com.mle.vpn.profile

/**
 * Orders a bounded set of profiles for automatic fallback.
 *
 * The user's explicit selection always goes first. Remaining profiles preserve
 * their saved order; this is intentionally not called "best" because TCP
 * preflight alone cannot prove end-to-end VPN quality.
 */
object ProfileFailoverPlanner {
    fun order(
        profiles: List<ImportedProfile>,
        selectedProfileId: String?,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
    ): List<ImportedProfile> {
        require(maxAttempts in 1..MAX_ALLOWED_ATTEMPTS) {
            "حد تلاش برای اتصال باید بین ۱ تا $MAX_ALLOWED_ATTEMPTS باشد."
        }
        val unique = profiles.distinctBy { it.id }
        val selected = unique.firstOrNull { it.id == selectedProfileId }
        val ordered = if (selected == null) unique else listOf(selected) + unique.filterNot { it.id == selected.id }
        return ordered.take(maxAttempts)
    }

    const val DEFAULT_MAX_ATTEMPTS = 3
    private const val MAX_ALLOWED_ATTEMPTS = 10
}
