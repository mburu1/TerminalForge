package com.terminalforge.domain.transaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test
    fun additionUsesMinorUnits() {
        assertEquals(Money(350, "KES"), Money(100, "KES") + Money(250, "KES"))
    }

    @Test
    fun arithmeticRejectsCurrencyMismatch() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(100, "KES") + Money(100, "USD")
        }
    }

    @Test
    fun subtractionRejectsNegativeResult() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(100, "KES") - Money(101, "KES")
        }
    }

    @Test
    fun constructionRejectsNegativeAmountAndMalformedCurrency() {
        assertThrows(IllegalArgumentException::class.java) { Money(-1, "KES") }
        assertThrows(IllegalArgumentException::class.java) { Money(1, "kes") }
    }
}
