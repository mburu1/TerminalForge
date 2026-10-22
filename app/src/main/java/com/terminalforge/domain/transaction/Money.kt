package com.terminalforge.domain.transaction

/**
 * Money represented in integer minor units (for example, cents or cents-equivalent).
 * This type deliberately performs no floating-point arithmetic.
 */
data class Money(
    val minorUnits: Long,
    val currency: String
) {
    init {
        require(minorUnits >= 0) { "Money cannot be negative." }
        require(CURRENCY_PATTERN.matches(currency)) {
            "Currency must be a three-letter uppercase code."
        }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        require(minorUnits >= other.minorUnits) { "Money subtraction cannot produce a negative amount." }
        return copy(minorUnits = minorUnits - other.minorUnits)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) { "Cannot combine different currencies." }
    }

    companion object {
        private val CURRENCY_PATTERN = Regex("[A-Z]{3}")
    }
}
