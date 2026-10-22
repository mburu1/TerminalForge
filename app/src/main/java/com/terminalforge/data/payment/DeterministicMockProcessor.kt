package com.terminalforge.data.payment

import com.terminalforge.domain.payment.PaymentProcessor
import com.terminalforge.domain.payment.PaymentRequest
import com.terminalforge.domain.payment.PaymentResult

/** Deterministic simulator. APPROVED is the default M2 scenario. */
class DeterministicMockProcessor(
    private val scenario: Scenario = Scenario.APPROVED
) : PaymentProcessor {
    enum class Scenario { APPROVED, DECLINED, FAILED, UNKNOWN_OUTCOME }

    override fun process(request: PaymentRequest): PaymentResult {
        require(request.transactionId.isNotBlank()) { "Transaction ID must not be blank." }
        require(request.idempotencyKey.isNotBlank()) { "Idempotency key must not be blank." }
        require(request.amount.minorUnits > 0) { "Payment amount must be greater than zero." }
        return when (scenario) {
            Scenario.APPROVED -> PaymentResult.Approved("MOCK-${request.transactionId}")
            Scenario.DECLINED -> PaymentResult.Declined("MOCK_DECLINED")
            Scenario.FAILED -> PaymentResult.Failed("MOCK_PROCESSOR_FAILURE")
            Scenario.UNKNOWN_OUTCOME -> PaymentResult.UnknownOutcome("MOCK_CONFIRMATION_UNAVAILABLE")
        }
    }
}
