package com.terminalforge.domain.transaction

import com.terminalforge.data.payment.DeterministicMockProcessor
import com.terminalforge.data.repository.InMemoryTransactionRepository
import com.terminalforge.domain.payment.PaymentProcessor
import com.terminalforge.domain.payment.PaymentRequest
import com.terminalforge.domain.payment.PaymentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulateSaleUseCaseTest {
    private fun useCase(
        processor: PaymentProcessor = DeterministicMockProcessor(),
        repository: InMemoryTransactionRepository = InMemoryTransactionRepository()
    ) = SimulateSaleUseCase(processor, repository,
        TransactionIdProvider { "txn-test-001" }, SaleClock { 1234L })

    @Test fun approvedSalePersistsFinalTransaction() {
        val repository = InMemoryTransactionRepository()
        val result = useCase(repository = repository).execute(Money(1250, "KES"), "idem-001")
        assertTrue(result is SaleResult.Completed)
        val transaction = (result as SaleResult.Completed).transaction
        assertEquals(TransactionState.APPROVED, transaction.state)
        assertEquals(OperationType.SALE, transaction.operationType)
        assertEquals(1250L, transaction.amount.minorUnits)
        assertEquals("KES", transaction.amount.currency)
        assertEquals(1234L, transaction.createdAtEpochMillis)
        assertEquals(TransactionState.APPROVED, repository.findById(transaction.id)?.state)
        assertEquals(1, repository.findAll().size)
    }

    @Test fun declinedSalePersistsDeclined() {
        val repository = InMemoryTransactionRepository()
        val result = useCase(DeterministicMockProcessor(DeterministicMockProcessor.Scenario.DECLINED), repository)
            .execute(Money(1250, "KES"), "idem-declined")
        assertTrue(result is SaleResult.Declined)
        assertEquals(TransactionState.DECLINED, repository.findAll().single().state)
    }

    @Test fun failedSalePersistsFailed() {
        val repository = InMemoryTransactionRepository()
        val result = useCase(DeterministicMockProcessor(DeterministicMockProcessor.Scenario.FAILED), repository)
            .execute(Money(1250, "KES"), "idem-failed")
        assertTrue(result is SaleResult.Failed)
        assertEquals(TransactionState.FAILED, repository.findAll().single().state)
    }

    @Test fun unknownOutcomeIsNotDeclined() {
        val repository = InMemoryTransactionRepository()
        val result = useCase(DeterministicMockProcessor(DeterministicMockProcessor.Scenario.UNKNOWN_OUTCOME), repository)
            .execute(Money(1250, "KES"), "idem-unknown")
        assertTrue(result is SaleResult.UnknownOutcome)
        assertEquals(TransactionState.UNKNOWN_OUTCOME, repository.findAll().single().state)
        assertFalse(repository.findAll().single().state == TransactionState.DECLINED)
    }

    @Test fun duplicateIdempotencyKeyDoesNotCallProcessorTwice() {
        val repository = InMemoryTransactionRepository()
        var calls = 0
        val processor = PaymentProcessor {
            calls++
            PaymentResult.Approved("MOCK-once")
        }
        val useCase = useCase(processor, repository)
        useCase.execute(Money(1250, "KES"), "idem-same")
        val second = useCase.execute(Money(1250, "KES"), "idem-same")
        assertEquals(1, calls)
        assertTrue(second is SaleResult.Completed)
        assertEquals(1, repository.findAll().size)
    }

    @Test fun invalidAmountIsRejectedBeforeProcessorInvocation() {
        var calls = 0
        val processor = PaymentProcessor { _: PaymentRequest ->
            calls++
            PaymentResult.Approved("MOCK-never")
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            useCase(processor).execute(Money(0, "KES"), "idem-zero")
        }
        assertEquals(0, calls)
    }

    @Test fun processorExceptionBecomesUnknownOutcome() {
        val repository = InMemoryTransactionRepository()
        val result = useCase(PaymentProcessor { throw IllegalStateException("synthetic failure") }, repository)
            .execute(Money(1250, "KES"), "idem-exception")
        assertTrue(result is SaleResult.UnknownOutcome)
        assertEquals(TransactionState.UNKNOWN_OUTCOME, repository.findAll().single().state)
    }
}
