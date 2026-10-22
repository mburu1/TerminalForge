package com.terminalforge.domain.payment
import com.terminalforge.domain.transaction.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
class DeterministicMockPaymentProcessorTest {
 private val request=PaymentRequest("txn-approved-001",Money(2599,"KES"))
 @Test fun approvedReferenceIsStable() {
  val processor=DeterministicMockPaymentProcessor(); val first=processor.process(request)
  assertEquals(ProcessorResult.Approved("MOCK-txn-approved-001"),first); assertEquals(first,processor.process(request))
 }
 @Test fun declineAndFailureAreTyped() {
  assertEquals(ProcessorResult.Declined("MOCK_DECLINED"),DeterministicMockPaymentProcessor(DeterministicMockPaymentProcessor.Scenario.DECLINED).process(request))
  assertEquals(ProcessorResult.Failed("MOCK_PROCESSOR_FAILURE"),DeterministicMockPaymentProcessor(DeterministicMockPaymentProcessor.Scenario.FAILED).process(request))
 }
 @Test fun zeroAmountRejected() { assertThrows(IllegalArgumentException::class.java) { PaymentRequest("zero",Money(0,"KES")) } }
}
