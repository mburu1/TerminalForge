package com.terminalforge.data.payment

import com.terminalforge.domain.payment.PaymentRequest
import com.terminalforge.domain.payment.PaymentResult
import com.terminalforge.domain.transaction.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class DeterministicMockProcessorTest {
    private fun request() = PaymentRequest("txn-001", "idem-001", Money(1250, "KES"))

    @Test fun approvedScenarioReturnsStableSyntheticReference() {
        val processor = DeterministicMockProcessor()
        val expected = PaymentResult.Approved("MOCK-txn-001")
        assertEquals(expected, processor.process(request()))
        assertEquals(expected, processor.process(request()))
    }

    @Test fun declinedScenarioIsExplicit() {
        assertEquals(PaymentResult.Declined("MOCK_DECLINED"),
            DeterministicMockProcessor(DeterministicMockProcessor.Scenario.DECLINED).process(request()))
    }

    @Test fun failedScenarioIsExplicit() {
        assertEquals(PaymentResult.Failed("MOCK_PROCESSOR_FAILURE"),
            DeterministicMockProcessor(DeterministicMockProcessor.Scenario.FAILED).process(request()))
    }

    @Test fun unknownOutcomeDoesNotMasqueradeAsDecline() {
        assertEquals(PaymentResult.UnknownOutcome("MOCK_CONFIRMATION_UNAVAILABLE"),
            DeterministicMockProcessor(DeterministicMockProcessor.Scenario.UNKNOWN_OUTCOME).process(request()))
    }
}
