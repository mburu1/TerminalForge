package com.terminalforge.hardware

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.fail

class SimulatedTerminalHardwareTest {
    @Test fun defaultSimulatorReadsContactlessAndPrintsReceipt() = runBlocking {
        val hardware = SimulatedTerminalHardware()
        assertEquals(CardReadResult.CardDetected(CardEntryMethod.CONTACTLESS), hardware.readCard())
        assertEquals(PrintResult.Printed, hardware.print(Receipt("tx-1", listOf("TerminalForge", "KES 10.00"))))
        assertEquals(TerminalStatus(DeviceStatus.READY, DeviceStatus.READY), hardware.getStatus())
    }

    @Test fun cardReaderDisconnectionIsConfigurable() = runBlocking {
        val hardware = SimulatedTerminalHardware(
            SimulatorConfiguration(cardReadFault = SimulatedDeviceFault.DISCONNECTED)
        )
        assertEquals(DeviceStatus.DISCONNECTED, hardware.getStatus().reader)
        assertFault(HardwareOperation.CARD_READ, SimulatedDeviceFault.DISCONNECTED) { hardware.readCard() }
    }

    @Test fun printerOutOfPaperIsConfigurable() = runBlocking {
        val hardware = SimulatedTerminalHardware(
            SimulatorConfiguration(printerFault = SimulatedDeviceFault.OUT_OF_PAPER)
        )
        assertEquals(DeviceStatus.OUT_OF_PAPER, hardware.getStatus().printer)
        assertFault(HardwareOperation.RECEIPT_PRINT, SimulatedDeviceFault.OUT_OF_PAPER) {
            hardware.print(Receipt("tx-2", listOf("Receipt")))
        }
    }

    @Test fun timeoutIsReportedAsTypedHardwareFault() = runBlocking {
        val hardware = SimulatedTerminalHardware(
            SimulatorConfiguration(statusFault = SimulatedDeviceFault.TIMEOUT)
        )
        assertFault(HardwareOperation.STATUS_CHECK, SimulatedDeviceFault.TIMEOUT) { hardware.getStatus() }
    }

    @Test fun operatorCancellationPropagatesAsCancellation() = runBlocking {
        val hardware = SimulatedTerminalHardware(
            SimulatorConfiguration(cardReadFault = SimulatedDeviceFault.OPERATOR_CANCELLED)
        )
        try {
            hardware.readCard()
            fail("Expected cancellation")
        } catch (ex: CancellationException) {
            assertTrue(ex.message.orEmpty().contains("operator cancellation"))
        }
    }

    private suspend fun assertFault(
        operation: HardwareOperation,
        fault: SimulatedDeviceFault,
        block: suspend () -> Any?
    ) {
        try {
            block()
            fail("Expected hardware fault")
        } catch (ex: HardwareOperationException) {
            assertEquals(operation, ex.operation)
            assertEquals(fault, ex.fault)
        }
    }
}
