package com.terminalforge.domain.payment

import com.terminalforge.domain.transaction.Money

/** Android-free port for synthetic/sandbox payment adapters. */
fun interface PaymentProcessor { fun process(request: PaymentRequest): ProcessorResult }

data class PaymentRequest(val transactionId: String, val amount: Money) {
    init {
        require(transactionId.isNotBlank()) { "Transaction ID must not be blank." }
        require(amount.minorUnits > 0) { "Payment amount must be greater than zero." }
    }
}

sealed interface ProcessorResult {
    data class Approved(val processorReference: String) : ProcessorResult {
        init { require(processorReference.isNotBlank()) }
    }
    data class Declined(val reasonCode: String) : ProcessorResult {
        init { require(reasonCode.isNotBlank()) }
    }
    data class Failed(val reasonCode: String) : ProcessorResult {
        init { require(reasonCode.isNotBlank()) }
    }
}
