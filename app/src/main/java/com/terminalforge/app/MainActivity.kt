package com.terminalforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.terminalforge.data.local.FileTransactionRepository
import com.terminalforge.domain.payment.DeterministicMockPaymentProcessor
import com.terminalforge.domain.reliability.StartupRecovery
import com.terminalforge.domain.sale.ExecuteSale
import com.terminalforge.domain.sale.SaleIdentityProvider
import com.terminalforge.domain.sale.SaleRequest
import com.terminalforge.domain.sale.SaleResult
import com.terminalforge.domain.transaction.Money
import com.terminalforge.domain.transaction.Transaction
import com.terminalforge.domain.transaction.TransactionState
import com.terminalforge.hardware.HardwareOperation
import com.terminalforge.hardware.HardwareOperationException
import com.terminalforge.hardware.Receipt
import com.terminalforge.hardware.SimulatedTerminalHardware
import com.terminalforge.hardware.TerminalStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Date
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = FileTransactionRepository(filesDir.resolve("terminalforge-transactions.snapshot"))
        StartupRecovery(repository).recover()
        val useCase = ExecuteSale(DeterministicMockPaymentProcessor(), repository, object : SaleIdentityProvider {
            override fun newTransactionId() = UUID.randomUUID().toString()
            override fun nowEpochMillis() = System.currentTimeMillis()
        })
        val hardware = SimulatedTerminalHardware()
        setContent { MaterialTheme { PosScreen(repository, useCase, hardware) } }
    }
}

private enum class Page { SALE, HISTORY, DETAIL }

@Composable
private fun PosScreen(repository: FileTransactionRepository, executeSale: ExecuteSale, hardware: SimulatedTerminalHardware) {
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.SALE) }
    var amount by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf(repository.findAll().sortedByDescending { it.createdAtEpochMillis }) }
    var selected by remember { mutableStateOf<Transaction?>(null) }
    var terminalStatus by remember { mutableStateOf<TerminalStatus?>(null) }
    fun refresh() {
        rows = repository.findAll().sortedByDescending { it.createdAtEpochMillis }
        selected = selected?.let { old -> rows.firstOrNull { it.id == old.id } }
    }
    LaunchedEffect(hardware) {
        terminalStatus = runCatching { hardware.getStatus() }.getOrNull()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TerminalForge", style = MaterialTheme.typography.headlineMedium)
            Text("SIMULATOR MODE • KES • No live payments", style = MaterialTheme.typography.labelMedium)
            terminalStatus?.let { status ->
                Text("Hardware • Reader: ${status.reader} • Printer: ${status.printer}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { page = Page.SALE; message = null }) { Text("New sale") }
                OutlinedButton(onClick = { refresh(); page = Page.HISTORY }) { Text("History (${rows.size})") }
            }
            HorizontalDivider()
            when (page) {
                Page.SALE -> {
                    Text("Sale entry", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(amount, { amount = it; error = null }, label = { Text("Amount (KES)") },
                        placeholder = { Text("1250.00") }, singleLine = true, enabled = !busy,
                        modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        val minor = try {
                            BigDecimal(amount.trim()).setScale(2, RoundingMode.UNNECESSARY)
                                .movePointRight(2).longValueExact()
                        } catch (_: Exception) { null }
                        if (minor == null || minor <= 0) error = "Enter a positive amount with up to 2 decimal places."
                        else {
                            busy = true; error = null; message = null
                            scope.launch {
                                try {
                                    val card = withContext(Dispatchers.IO) { hardware.readCard() }
                                    val result = withContext(Dispatchers.IO) {
                                        executeSale.execute(SaleRequest(Money(minor, "KES"), "sale-${UUID.randomUUID()}"))
                                    }
                                    val receiptOutcome = if (result is SaleResult.Completed) {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                hardware.print(Receipt(result.transaction.id, listOf(
                                                    "TerminalForge", "Transaction: ${result.transaction.id}",
                                                    "Amount: ${money(result.transaction)}", "Status: APPROVED"
                                                )))
                                            }
                                            "RECEIPT PRINTED"
                                        } catch (ex: CancellationException) {
                                            throw ex
                                        } catch (_: Exception) {
                                            "RECEIPT PRINT FAILED — payment remains approved; reprint or provide a manual receipt"
                                        }
                                    } else null
                                    terminalStatus = runCatching { withContext(Dispatchers.IO) { hardware.getStatus() } }.getOrNull()
                                    refresh()
                                    selected = when (result) {
                                        is SaleResult.Completed -> result.transaction
                                        is SaleResult.Declined -> result.transaction
                                        is SaleResult.Failed -> result.transaction
                                        is SaleResult.Ambiguous -> result.transaction
                                        is SaleResult.IdempotentReplay -> result.transaction
                                    }
                                    message = when (result) {
                                        is SaleResult.Completed -> "APPROVED • ${result.transaction.id} • CARD ${card.method} • ${receiptOutcome}"
                                        is SaleResult.Declined -> "DECLINED • ${result.reasonCode}"
                                        is SaleResult.Failed -> "FAILED • ${result.reasonCode}"
                                        is SaleResult.Ambiguous -> "OUTCOME UNKNOWN • Reconciliation required. Do not retry automatically."
                                        is SaleResult.IdempotentReplay -> "EXISTING TRANSACTION • ${result.transaction.id}"
                                    }
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    // A UI-level failure must not strand the operator in a busy state.
                                    // Never turn an unexpected failure into a successful payment message.
                                    refresh()
                                    error = when (ex) {
                                        is HardwareOperationException -> when (ex.operation) {
                                            HardwareOperation.CARD_READ -> "CARD READ FAILED (${ex.fault}). No sale was submitted."
                                            HardwareOperation.RECEIPT_PRINT -> "Receipt printer failed (${ex.fault}). Check transaction history before retrying."
                                            HardwareOperation.STATUS_CHECK -> "Terminal status is unavailable."
                                        }
                                        is com.terminalforge.domain.sale.IdempotencyConflict ->
                                            "This request conflicts with an existing transaction. Review transaction history."
                                        else -> "Unable to complete the simulated sale. Check transaction history before trying again."
                                    }
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }) { Text("Process simulated sale") }
                    if (busy) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Processing mock transaction…")
                    }
                    message?.let { msg ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Transaction result", style = MaterialTheme.typography.titleMedium)
                                Text(msg)
                                selected?.let { Summary(it) }
                                OutlinedButton(onClick = { if (selected != null) page = Page.DETAIL }) {
                                    Text("View transaction details")
                                }
                            }
                        }
                    }
                }
                Page.HISTORY -> {
                    Text("Transaction history", style = MaterialTheme.typography.titleLarge)
                    if (rows.isEmpty()) Text("No transactions yet. Run a simulated sale to begin.")
                    rows.forEach { tx ->
                        Card(Modifier.fillMaxWidth().clickable { selected = tx; page = Page.DETAIL }) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(money(tx), style = MaterialTheme.typography.titleMedium)
                                Text(tx.state.name, style = MaterialTheme.typography.labelLarge)
                                Text(tx.id, style = MaterialTheme.typography.bodySmall)
                                Text(date(tx.createdAtEpochMillis), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Page.DETAIL -> {
                    Text("Transaction details", style = MaterialTheme.typography.titleLarge)
                    selected?.let { tx ->
                        Summary(tx)
                        Line("Transaction ID", tx.id)
                        Line("Operation", tx.operationType.name)
                        Line("State", tx.state.name)
                        Line("Amount", money(tx))
                        Line("Created", date(tx.createdAtEpochMillis))
                        Line("Idempotency key", tx.idempotencyKey)
                        if (tx.state == TransactionState.UNKNOWN_OUTCOME ||
                            tx.state == TransactionState.RECONCILIATION_REQUIRED) {
                            Text("Outcome is unconfirmed. Reconcile before further action; automatic retries are disabled.",
                                color = MaterialTheme.colorScheme.error)
                        }
                    } ?: Text("Transaction not found.")
                    OutlinedButton(onClick = { page = Page.HISTORY }) { Text("Back to history") }
                }
            }
            Text("Educational simulator only. Synthetic data; no real card data or payment credentials.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun Summary(tx: Transaction) {
    Text(money(tx), style = MaterialTheme.typography.headlineSmall)
    Text("Status: ${tx.state.name}")
}
@Composable private fun Line(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
    }
}
private fun money(tx: Transaction) = "${tx.amount.currency} ${BigDecimal.valueOf(tx.amount.minorUnits, 2).setScale(2).toPlainString()}"
private fun date(value: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
