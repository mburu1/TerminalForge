package com.terminalforge.hardware

/** Read-only health/status boundary for terminal peripherals. */
interface TerminalStatusProvider {
    suspend fun getStatus(): TerminalStatus
}

data class TerminalStatus(
    val reader: DeviceStatus,
    val printer: DeviceStatus,
    val simulator: Boolean = true
)

enum class DeviceStatus { READY, DISCONNECTED, BUSY, OUT_OF_PAPER, FAULT }
