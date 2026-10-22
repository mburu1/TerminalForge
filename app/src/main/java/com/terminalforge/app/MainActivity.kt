package com.terminalforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.terminalforge.data.payment.DeterministicMockProcessor
import com.terminalforge.data.repository.InMemoryTransactionRepository
import com.terminalforge.domain.transaction.Money
import com.terminalforge.domain.transaction.SaleResult
import com.terminalforge.domain.transaction.SimulateSaleUseCase
import java.math.BigDecimal
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { TerminalForgeScreen() } }
    }
}

@Composable
private fun TerminalForgeScreen() {
    val saleUseCase = remember {
        SimulateSaleUseCase(
            paymentProcessor = DeterministicMockProcessor(),
            transactionRepository = InMemoryTransactionRepository()
        )
    }
    var amountInput by remember { mutableStateOf("1250.00") }
    var message by remember { mutableStateOf("Ready to simulate a KES sale.") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("TerminalForge", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Simulator-first Android POS architecture lab",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Synthetic data and mock integrations only. No live payments.",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                label = { Text("Sale amount (KES)") },
                singleLine = true
            )
            Button(
                onClick = {
                    val minorUnits = runCatching {
                        BigDecimal(amountInput.trim())
                            .movePointRight(2)
                            .longValueExact()
                    }.getOrNull()

                    if (minorUnits == null || minorUnits <= 0L) {
                        message = "Enter a valid amount greater than KES 0.00."
                    } else {
                        val result = runCatching {
                            saleUseCase.execute(
                                amount = Money(minorUnits, "KES"),
                                idempotencyKey = UUID.randomUUID().toString()
                            )
                        }.getOrNull()
                        message = when (result) {
                            is SaleResult.Completed -> "APPROVED · KES $amountInput · Ref: ${result.processorReference}"
                            is SaleResult.Declined -> "DECLINED · ${result.reasonCode}"
                            is SaleResult.Failed -> "FAILED · ${result.reasonCode}"
                            is SaleResult.UnknownOutcome -> "UNKNOWN OUTCOME · Reconciliation required"
                            null -> "Sale could not be completed. Check the entered amount."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text("Simulate approved sale") }
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
