package com.koard.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koard.android.R
import com.koard.android.ui.theme.KoardAndroidSDKTheme
import com.koard.android.ui.theme.KoardGreen800
import com.koard.android.ui.theme.KoardRed500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFlowScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionFlowViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.process_transaction)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            TransactionFlowContent(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                uiState = uiState,
                onAmountChanged = viewModel::onAmountChanged,
                onTaxAmountChanged = viewModel::onTaxAmountChanged,
                onTaxTypeToggled = viewModel::onTaxTypeToggled,
                onTipAmountChanged = viewModel::onTipAmountChanged,
                onTipTypeToggled = viewModel::onTipTypeToggled,
                onSurchargeStateChanged = viewModel::onSurchargeStateChanged,
                onSurchargeAmountChanged = viewModel::onSurchargeAmountChanged,
                onSurchargeTypeToggled = viewModel::onSurchargeTypeToggled,
                onStartPreauth = {
                    val activity = context.findActivity()
                    if (activity != null) {
                        viewModel.startPreauth(activity)
                    } else {
                        viewModel.onMissingActivity()
                    }
                },
                onStartTransaction = {
                    val activity = context.findActivity()
                    if (activity != null) {
                        viewModel.startTransaction(activity)
                    } else {
                        viewModel.onMissingActivity()
                    }
                }
            )

            // Full-screen transaction processing overlay
            if (uiState.isProcessing || uiState.finalStatus != null) {
                TransactionProcessingOverlay(
                    uiState = uiState,
                    onClose = viewModel::onDismissTransactionResult,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Surcharge confirmation modal
            if (uiState.showSurchargeConfirmation) {
                SurchargeConfirmationModal(
                    uiState = uiState,
                    onOverrideAmountChanged = viewModel::onSurchargeOverrideAmountChanged,
                    onOverrideTypeToggled = viewModel::onSurchargeOverrideTypeToggled,
                    onConfirm = { viewModel.onConfirmSurcharge(true) },
                    onDecline = { viewModel.onConfirmSurcharge(false) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun TransactionFlowContent(
    uiState: TransactionFlowUiState,
    onAmountChanged: (String) -> Unit,
    onTaxAmountChanged: (String) -> Unit,
    onTaxTypeToggled: () -> Unit,
    onTipAmountChanged: (String) -> Unit,
    onTipTypeToggled: () -> Unit,
    onSurchargeStateChanged: (SurchargeState) -> Unit,
    onSurchargeAmountChanged: (String) -> Unit,
    onSurchargeTypeToggled: () -> Unit,
    onStartPreauth: () -> Unit,
    onStartTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .imePadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.transaction_flow_instructions),
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = uiState.amount,
            onValueChange = onAmountChanged,
            label = { Text("Subtotal") },
            placeholder = { Text("0.00") },
            leadingIcon = { Text("$") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Tip input with toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.tipAmount,
                onValueChange = onTipAmountChanged,
                label = { Text("Tip") },
                placeholder = { Text("0.00") },
                leadingIcon = {
                    Text(if (uiState.tipType == com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE) "%" else "$")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onTipTypeToggled) {
                Text(if (uiState.tipType == com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE) "Percentage" else "Fixed")
            }
        }

        // Tax input with toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.taxAmount,
                onValueChange = onTaxAmountChanged,
                label = { Text("Tax") },
                placeholder = { Text("0.00") },
                leadingIcon = {
                    Text(if (uiState.taxType == com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE) "%" else "$")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onTaxTypeToggled) {
                Text(if (uiState.taxType == com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE) "Percentage" else "Fixed")
            }
        }

        // Surcharge state selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Surcharge:", style = MaterialTheme.typography.bodyLarge)
            TextButton(
                onClick = { onSurchargeStateChanged(SurchargeState.OFF) },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = if (uiState.surchargeState == SurchargeState.OFF)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Default")
            }
            TextButton(
                onClick = { onSurchargeStateChanged(SurchargeState.BYPASS) },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = if (uiState.surchargeState == SurchargeState.BYPASS)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Bypass")
            }
            TextButton(
                onClick = { onSurchargeStateChanged(SurchargeState.ENABLE) },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = if (uiState.surchargeState == SurchargeState.ENABLE)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Override")
            }
        }

        // Surcharge amount input (visible when enabled)
        if (uiState.surchargeState == SurchargeState.ENABLE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.surchargeAmount,
                    onValueChange = onSurchargeAmountChanged,
                    label = { Text("Surcharge") },
                    placeholder = { Text("0.00") },
                    leadingIcon = {
                        Text(if (uiState.surchargeType == com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE) "%" else "$")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSurchargeTypeToggled) {
                    Text(if (uiState.surchargeType == com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE) "Percentage" else "Fixed")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartPreauth,
                enabled = uiState.amount.isNotBlank() && !uiState.isProcessing,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Preauth ${"$%.2f".format(uiState.calculatedTotal)}")
            }
            Button(
                onClick = onStartTransaction,
                enabled = uiState.amount.isNotBlank() && !uiState.isProcessing,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Sale ${"$%.2f".format(uiState.calculatedTotal)}")
            }
        }

        // Show error messages only when not processing
        if (!uiState.isProcessing && uiState.finalStatus == null) {
            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = KoardRed500,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun TransactionFlowContentPreview() {
    KoardAndroidSDKTheme {
        TransactionFlowContent(
            uiState = TransactionFlowUiState(
                amount = "12.00",
                isProcessing = true,
                statusMessages = listOf(
                    "Tap card on the back of the device",
                    "Processing..."
                ),
                finalStatus = "Approved",
                transactionId = "123456",
                transaction = null
            ),
            onAmountChanged = {},
            onTaxAmountChanged = {},
            onTaxTypeToggled = {},
            onTipAmountChanged = {},
            onTipTypeToggled = {},
            onSurchargeStateChanged = {},
            onSurchargeAmountChanged = {},
            onSurchargeTypeToggled = {},
            onStartPreauth = {},
            onStartTransaction = {}
        )
    }
}

@Composable
private fun SurchargeConfirmationModal(
    uiState: TransactionFlowUiState,
    onOverrideAmountChanged: (String) -> Unit,
    onOverrideTypeToggled: () -> Unit,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transaction = uiState.transaction ?: return
    val originalTotal = transaction.totalAmount
    val surchargeAmountInCents = (transaction.surchargeAmount / 100 * 100).toInt() // Convert dollars to cents
    val totalMinusSurcharge = originalTotal - surchargeAmountInCents

    // Calculate new total if override is provided
    val overrideAmount = uiState.surchargeOverrideAmount.trim()
    val newTotal = if (overrideAmount.isNotBlank()) {
        val overrideValue = overrideAmount.toDoubleOrNull() ?: 0.0
        val newSurcharge = if (uiState.surchargeOverrideType == com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE) {
            ((totalMinusSurcharge.toDouble() * overrideValue) / 100.0).toInt()
        } else {
            (overrideValue * 100).toInt()
        }
        totalMinusSurcharge + newSurcharge
    } else {
        originalTotal
    }

    Surface(
        modifier = modifier,
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            // Title
            Text(
                text = "Surcharge Confirmation Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Transaction details
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Original total (before surcharge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Original Total:", fontWeight = FontWeight.Medium, color = Color.Black)
                    Text("${"$%.2f".format(totalMinusSurcharge / 100.0)}", fontWeight = FontWeight.SemiBold, color = Color.Black)
                }

                // Surcharge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Surcharge:", fontWeight = FontWeight.Medium, color = Color.Black)
                    Text("${"$%.2f".format(surchargeAmountInCents / 100.0)}", fontWeight = FontWeight.SemiBold, color = Color.Black)
                }

                HorizontalDivider()

                // New total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("New Total:", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                    Text(
                        "${"$%.2f".format(newTotal / 100.0)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Override surcharge section
            Text(
                "Override Surcharge (Optional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.surchargeOverrideAmount,
                    onValueChange = onOverrideAmountChanged,
                    label = { Text("Override Amount") },
                    placeholder = { Text("Leave blank to keep original") },
                    leadingIcon = {
                        Text(if (uiState.surchargeOverrideType == com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE) "%" else "$")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOverrideTypeToggled) {
                    Text(
                        if (uiState.surchargeOverrideType == com.koardlabs.merchant.sdk.domain.AmountType.PERCENTAGE) "%" else "$"
                    )
                }
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    color = KoardRed500,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            if (uiState.isConfirmingSurcharge) {
                CircularProgressIndicator(color = KoardGreen800)
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = KoardGreen800
                        )
                    ) {
                        Text("Accept Surcharge", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = KoardRed500
                        ),
                        border = BorderStroke(1.dp, KoardRed500)
                    ) {
                        Text("Decline Surcharge", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionProcessingOverlay(
    uiState: TransactionFlowUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            // Koard logo text
            Text(
                text = "KOARD",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = KoardGreen800,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Total amount being charged
            Text(
                text = "${"$%.2f".format(uiState.calculatedTotal)}",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = KoardGreen800
            )

            // Processing indicator
            if (uiState.isProcessing && uiState.finalStatus == null) {
                CircularProgressIndicator(
                    color = KoardGreen800,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Reader messages - simplified, clean status
            if (uiState.statusMessages.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Show only the last/most recent message
                    Text(
                        text = uiState.statusMessages.last(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = KoardGreen800,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Error message
            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.headlineSmall,
                    color = KoardRed500,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Transaction details when complete
            uiState.finalStatus?.let { finalStatus ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.transaction_result_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = KoardGreen800
                    )

                    uiState.transactionId?.let { id ->
                        Text(
                            text = stringResource(R.string.transaction_result_id, id),
                            style = MaterialTheme.typography.bodyMedium,
                            color = KoardGreen800
                        )
                    }

                    uiState.transaction?.let { transaction ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Status: ${transaction.status}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = KoardGreen800
                        )
                        Text(
                            text = "Brand: ${transaction.cardBrand}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = KoardGreen800
                        )
                        Text(
                            text = "Number ${transaction.card}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KoardGreen800
                        )
                        Text(
                            text = "Total Amount: ${"$%.2f".format(transaction.totalAmount / 100.0)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KoardGreen800
                        )
                        Text(
                            text = "Subtotal: ${"$%.2f".format(transaction.subtotal / 100.0)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KoardGreen800
                        )
                        Text(
                            text = "Tip Amount: ${"$%.2f".format(transaction.tipAmount / 100.0)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KoardGreen800
                        )
                        Text(
                            text = "Tax Amount: ${"$%.2f".format(transaction.taxAmount / 100.0)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KoardGreen800
                        )
                        if (transaction.surchargeApplied) {
                            Text(
                                text = "Surcharge: ${"$%.2f".format(transaction.surchargeAmount / 100.0)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KoardGreen800
                            )
                        }
                    }
                }
            }

            // Close button for error case or completion
            if (!uiState.isProcessing && (uiState.errorMessage != null || uiState.finalStatus != null)) {
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = KoardGreen800
                    )
                ) {
                    Text("Close")
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
