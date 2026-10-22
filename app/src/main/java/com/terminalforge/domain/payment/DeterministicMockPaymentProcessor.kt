package com.terminalforge.domain.payment
/** Deterministic simulator; never contacts a network or handles real credentials. */
class DeterministicMockPaymentProcessor(private val scenario:Scenario=Scenario.APPROVED):PaymentProcessor {
 enum class Scenario { APPROVED, DECLINED, FAILED }
 override fun process(request:PaymentRequest):ProcessorResult=when(scenario) {
  Scenario.APPROVED -> ProcessorResult.Approved("MOCK-${request.transactionId}")
  Scenario.DECLINED -> ProcessorResult.Declined("MOCK_DECLINED")
  Scenario.FAILED -> ProcessorResult.Failed("MOCK_PROCESSOR_FAILURE")
 }
}
