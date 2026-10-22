package com.terminalforge.hardware

import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

enum class HardwareOperation { CARD_READ, RECEIPT_PRINT, STATUS_CHECK }
enum class SimulatedDeviceFault { NONE, DISCONNECTED, TIMEOUT, OUT_OF_PAPER, OPERATOR_CANCELLED, DEVICE_FAULT }

data class SimulatorConfiguration(
    val cardReadFault: SimulatedDeviceFault = SimulatedDeviceFault.NONE,
    val printerFault: SimulatedDeviceFault = SimulatedDeviceFault.NONE,
    val statusFault: SimulatedDeviceFault = SimulatedDeviceFault.NONE,
    val cardEntryMethod: CardEntryMethod = CardEntryMethod.CONTACTLESS
)

/** Predictable, configurable peripheral simulator. It never performs real payment or PIN operations. */
class SimulatedTerminalHardware(
    private val configuration: SimulatorConfiguration = SimulatorConfiguration()
) : CardReader, ReceiptPrinter, TerminalStatusProvider {

    override suspend fun readCard(): CardReadResult {
        coroutineContext.ensureActive()
        throwConfiguredFault(configuration.cardReadFault, HardwareOperation.CARD_READ)
        return CardReadResult.CardDetected(configuration.cardEntryMethod)
    }

    override suspend fun print(receipt: Receipt): PrintResult {
        coroutineContext.ensureActive()
        // Validate input at the adapter boundary even when invoked outside the UI.
        require(receipt.transactionId.isNotBlank())
        throwConfiguredFault(configuration.printerFault, HardwareOperation.RECEIPT_PRINT)
        return PrintResult.Printed
    }

    override suspend fun getStatus(): TerminalStatus {
        coroutineContext.ensureActive()
        throwConfiguredFault(configuration.statusFault, HardwareOperation.STATUS_CHECK)
        return TerminalStatus(
            reader = statusFor(configuration.cardReadFault, DeviceStatus.READY),
            printer = statusFor(configuration.printerFault, DeviceStatus.READY)
        )
    }

    private fun statusFor(fault: SimulatedDeviceFault, ready: DeviceStatus): DeviceStatus =
        when (fault) {
            SimulatedDeviceFault.NONE -> ready
            SimulatedDeviceFault.DISCONNECTED -> DeviceStatus.DISCONNECTED
            SimulatedDeviceFault.OUT_OF_PAPER -> DeviceStatus.OUT_OF_PAPER
            SimulatedDeviceFault.TIMEOUT, SimulatedDeviceFault.OPERATOR_CANCELLED,
            SimulatedDeviceFault.DEVICE_FAULT -> DeviceStatus.FAULT
        }

    private fun throwConfiguredFault(fault: SimulatedDeviceFault, operation: HardwareOperation) {
        when (fault) {
            SimulatedDeviceFault.NONE -> Unit
            SimulatedDeviceFault.OPERATOR_CANCELLED ->
                throw CancellationException("Simulated operator cancellation during $operation.")
            else -> throw HardwareOperationException(operation, fault)
        }
    }
}

class HardwareOperationException(
    val operation: HardwareOperation,
    val fault: SimulatedDeviceFault
) : IllegalStateException("Simulated hardware fault '$fault' during '$operation'.")
