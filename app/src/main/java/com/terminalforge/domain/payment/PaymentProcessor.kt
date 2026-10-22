package com.terminalforge.domain.payment

import com.terminalforge.domain.transaction.Money

data class PaymentRequest(
    val transactionId: String,
    val idempotencyKey: String,
    val amount: Money
)

sealed interface PaymentResult {
    data class Approved(val processorReference: String) : PaymentResult
    data class Declined(val reasonCode: String) : PaymentResult
    data class Failed(val reasonCode: String) : PaymentResult
    data class UnknownOutcome(val reasonCode: String) : PaymentResult
}

/**
 * Port to a payment processor. Implementations must never receive real card data
 * through this educational interface.
 */
fun interface PaymentProcessor {
    fun process(request: PaymentRequest): PaymentResult
}
