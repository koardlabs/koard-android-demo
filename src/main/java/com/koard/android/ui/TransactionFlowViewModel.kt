package com.koard.android.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.koard.android.R
import com.koardlabs.merchant.sdk.KoardMerchantSdk
import com.koardlabs.merchant.sdk.domain.AmountType
import com.koardlabs.merchant.sdk.domain.KoardTransactionActionStatus
import com.koardlabs.merchant.sdk.domain.PaymentBreakdown
import com.koardlabs.merchant.sdk.domain.Surcharge
import com.koardlabs.merchant.sdk.domain.KoardTransactionFinalStatus
import com.koardlabs.merchant.sdk.domain.KoardTransactionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

class TransactionFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val koardSdk = KoardMerchantSdk.getInstance()

    private val _uiState = MutableStateFlow(TransactionFlowUiState())
    val uiState: StateFlow<TransactionFlowUiState> = _uiState.asStateFlow()

    fun onAmountChanged(newAmount: String) {
        _uiState.update {
            it.copy(amount = newAmount, errorMessage = null)
        }
    }

    fun onTaxAmountChanged(newAmount: String) {
        _uiState.update {
            if (it.taxType == AmountType.PERCENTAGE) {
                it.copy(taxAmountPercentage = newAmount, errorMessage = null)
            } else {
                it.copy(taxAmountFixed = newAmount, errorMessage = null)
            }
        }
    }

    fun onTaxTypeToggled() {
        _uiState.update {
            it.copy(
                taxType = if (it.taxType == AmountType.PERCENTAGE) AmountType.FIXED else AmountType.PERCENTAGE,
                errorMessage = null
            )
        }
    }

    fun onTipAmountChanged(newAmount: String) {
        _uiState.update {
            if (it.tipType == AmountType.PERCENTAGE) {
                it.copy(tipAmountPercentage = newAmount, errorMessage = null)
            } else {
                it.copy(tipAmountFixed = newAmount, errorMessage = null)
            }
        }
    }

    fun onTipTypeToggled() {
        _uiState.update {
            it.copy(
                tipType = if (it.tipType == AmountType.PERCENTAGE) AmountType.FIXED else AmountType.PERCENTAGE,
                errorMessage = null
            )
        }
    }

    fun onSurchargeStateChanged(newState: SurchargeState) {
        _uiState.update {
            it.copy(surchargeState = newState, errorMessage = null)
        }
    }

    fun onSurchargeAmountChanged(newAmount: String) {
        _uiState.update {
            if (it.surchargeType == AmountType.PERCENTAGE) {
                it.copy(surchargeAmountPercentage = newAmount, errorMessage = null)
            } else {
                it.copy(surchargeAmountFixed = newAmount, errorMessage = null)
            }
        }
    }

    fun onSurchargeTypeToggled() {
        _uiState.update {
            it.copy(
                surchargeType = if (it.surchargeType == AmountType.PERCENTAGE) AmountType.FIXED else AmountType.PERCENTAGE,
                errorMessage = null
            )
        }
    }

    fun startPreauth(activity: Activity) {
        // Check SDK readiness
        val readiness = koardSdk.readinessState.value
        if (!readiness.isReadyForTransactions) {
            _uiState.update {
                it.copy(
                    errorMessage = "Cannot start transaction: ${readiness.getStatusMessage()}"
                )
            }
            return
        }

        val amount = uiState.value.amount.trim()
        if (amount.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = getApplication<Application>().getString(R.string.transaction_error_missing_amount)
                )
            }
            return
        }

        if (uiState.value.isProcessing) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    errorMessage = null,
                    statusMessages = emptyList(),
                    finalStatus = null,
                    transactionId = null,
                    transaction = null
                )
            }
            try {
                // Parse subtotal from string to dollars, then convert to cents
                val subtotalDollars = try {
                    amount.toDouble()
                } catch (e: NumberFormatException) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Invalid amount format"
                        )
                    }
                    return@launch
                }
                val subtotalCents = (subtotalDollars * 100).toInt()

                // Calculate tip
                val tipValue = when (uiState.value.tipType) {
                    AmountType.PERCENTAGE -> {
                        val rate = uiState.value.tipAmount.toDoubleOrNull() ?: 0.0
                        subtotalDollars * (rate / 100.0)
                    }
                    AmountType.FIXED -> uiState.value.tipAmount.toDoubleOrNull() ?: 0.0
                }
                val tipCents = (tipValue * 100).toInt()

                // Calculate tax (on subtotal + tip)
                val taxValue = when (uiState.value.taxType) {
                    AmountType.PERCENTAGE -> {
                        val rate = uiState.value.taxAmount.toDoubleOrNull() ?: 0.0
                        (subtotalDollars + tipValue) * (rate / 100.0)
                    }
                    AmountType.FIXED -> uiState.value.taxAmount.toDoubleOrNull() ?: 0.0
                }
                val taxCents = (taxValue * 100).toInt()

                // Calculate surcharge (on subtotal + tip + tax)
                val surchargeValue = if (uiState.value.surchargeState == SurchargeState.ENABLE) {
                    val baseAmount = subtotalDollars + tipValue + taxValue
                    when (uiState.value.surchargeType) {
                        AmountType.PERCENTAGE -> {
                            val rate = uiState.value.surchargeAmount.toDoubleOrNull() ?: 0.0
                            baseAmount * (rate / 100.0)
                        }
                        AmountType.FIXED -> uiState.value.surchargeAmount.toDoubleOrNull() ?: 0.0
                    }
                } else {
                    0.0
                }
                val surchargeCents = (surchargeValue * 100).toInt()

                // Total amount in cents
                val totalAmountCents = subtotalCents + tipCents + taxCents + surchargeCents

                // Build breakdown
                val breakdown = PaymentBreakdown(
                    subtotal = subtotalCents,
                    taxRate = if (uiState.value.taxType == AmountType.PERCENTAGE)
                        uiState.value.taxAmount.toDoubleOrNull() else null,
                    taxAmount = taxCents,
                    tipAmount = tipCents,
                    tipRate = if (uiState.value.tipType == AmountType.PERCENTAGE)
                        uiState.value.tipAmount.toDoubleOrNull() else null,
                    tipType = if (uiState.value.tipType == AmountType.PERCENTAGE) "percentage" else "fixed",
                    surcharge = Surcharge(
                        amount = if (uiState.value.surchargeType == AmountType.FIXED &&
                                     uiState.value.surchargeState == SurchargeState.ENABLE)
                                   surchargeCents else null,
                        percentage = if (uiState.value.surchargeType == AmountType.PERCENTAGE &&
                                        uiState.value.surchargeState == SurchargeState.ENABLE)
                                       uiState.value.surchargeAmount.toDoubleOrNull() else null,
                        bypass = uiState.value.surchargeState == SurchargeState.BYPASS
                    )
                )

                val eventId = UUID.randomUUID().toString()
                Timber.d("Starting preauth transaction with eventId: $eventId")
                Timber.d("Total Amount: $totalAmountCents cents, Breakdown: $breakdown")

                koardSdk.preauth(
                    activity = activity,
                    amount = totalAmountCents,
                    breakdown = breakdown,
                    currency = "USD",
                    eventId = eventId
                ).onEach { response ->
                    handleTransactionResponse(response)
                }.launchIn(viewModelScope)
            } catch (t: Throwable) {
                Timber.e(t, "Preauth transaction failed")

                // Build detailed error message
                val message = buildString {
                    append("Preauth Failed")

                    // Add exception message
                    t.message?.let {
                        append("\n\n")
                        append(it)
                    }

                    // Add exception type
                    append("\n\nError Type: ${t.javaClass.simpleName}")

                    // If it's a KoardException, show more details
                    if (t is com.koardlabs.merchant.sdk.domain.exception.KoardException) {
                        append("\nError: ${t.error.shortMessage}")
                        t.error.errorType?.let { errorType ->
                            append("\nCategory: ${errorType.javaClass.simpleName}")
                        }
                    }

                    // If we have no message at all, add generic error
                    if (t.message == null) {
                        append("\n\n")
                        append(getApplication<Application>().getString(R.string.transaction_generic_error))
                    }
                }

                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = message)
                }
            }
        }
    }

    fun startTransaction(activity: Activity) {
        // Check SDK readiness
        val readiness = koardSdk.readinessState.value
        if (!readiness.isReadyForTransactions) {
            _uiState.update {
                it.copy(
                    errorMessage = "Cannot start transaction: ${readiness.getStatusMessage()}"
                )
            }
            return
        }

        val amount = uiState.value.amount.trim()
        if (amount.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = getApplication<Application>().getString(R.string.transaction_error_missing_amount)
                )
            }
            return
        }

        if (uiState.value.isProcessing) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    errorMessage = null,
                    statusMessages = emptyList(),
                    finalStatus = null,
                    transactionId = null,
                    transaction = null
                )
            }
            try {
                // Parse subtotal from string to dollars, then convert to cents
                val subtotalDollars = try {
                    amount.toDouble()
                } catch (e: NumberFormatException) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Invalid amount format"
                        )
                    }
                    return@launch
                }
                val subtotalCents = (subtotalDollars * 100).toInt()

                // Calculate tip
                val tipValue = when (uiState.value.tipType) {
                    AmountType.PERCENTAGE -> {
                        val rate = uiState.value.tipAmount.toDoubleOrNull() ?: 0.0
                        subtotalDollars * (rate / 100.0)
                    }
                    AmountType.FIXED -> uiState.value.tipAmount.toDoubleOrNull() ?: 0.0
                }
                val tipCents = (tipValue * 100).toInt()

                // Calculate tax (on subtotal + tip)
                val taxValue = when (uiState.value.taxType) {
                    AmountType.PERCENTAGE -> {
                        val rate = uiState.value.taxAmount.toDoubleOrNull() ?: 0.0
                        (subtotalDollars + tipValue) * (rate / 100.0)
                    }
                    AmountType.FIXED -> uiState.value.taxAmount.toDoubleOrNull() ?: 0.0
                }
                val taxCents = (taxValue * 100).toInt()

                // Calculate surcharge (on subtotal + tip + tax)
                val surchargeValue = if (uiState.value.surchargeState == SurchargeState.ENABLE) {
                    val baseAmount = subtotalDollars + tipValue + taxValue
                    when (uiState.value.surchargeType) {
                        AmountType.PERCENTAGE -> {
                            val rate = uiState.value.surchargeAmount.toDoubleOrNull() ?: 0.0
                            baseAmount * (rate / 100.0)
                        }
                        AmountType.FIXED -> uiState.value.surchargeAmount.toDoubleOrNull() ?: 0.0
                    }
                } else {
                    0.0
                }
                val surchargeCents = (surchargeValue * 100).toInt()

                // Total amount in cents
                val totalAmountCents = subtotalCents + tipCents + taxCents + surchargeCents

                // Build breakdown
                val breakdown = PaymentBreakdown(
                    subtotal = subtotalCents,
                    taxRate = if (uiState.value.taxType == AmountType.PERCENTAGE)
                        uiState.value.taxAmount.toDoubleOrNull() else null,
                    taxAmount = taxCents,
                    tipAmount = tipCents,
                    tipRate = if (uiState.value.tipType == AmountType.PERCENTAGE)
                        uiState.value.tipAmount.toDoubleOrNull() else null,
                    tipType = if (uiState.value.tipType == AmountType.PERCENTAGE) "percentage" else "fixed",
                    surcharge = Surcharge(
                        amount = if (uiState.value.surchargeType == AmountType.FIXED &&
                                     uiState.value.surchargeState == SurchargeState.ENABLE)
                                   surchargeCents else null,
                        percentage = if (uiState.value.surchargeType == AmountType.PERCENTAGE &&
                                        uiState.value.surchargeState == SurchargeState.ENABLE)
                                       uiState.value.surchargeAmount.toDoubleOrNull() else null,
                        bypass = uiState.value.surchargeState == SurchargeState.BYPASS
                    )
                )

                val eventId = UUID.randomUUID().toString()
                Timber.d("Starting sale transaction with eventId: $eventId")
                Timber.d("Total Amount: $totalAmountCents cents, Breakdown: $breakdown")

                koardSdk.sale(
                    activity = activity,
                    amount = totalAmountCents,
                    breakdown = breakdown,
                    currency = "USD",
                    eventId = eventId
                ).onEach { response ->
                    handleTransactionResponse(response)
                }.launchIn(viewModelScope)
            } catch (t: Throwable) {
                Timber.e(t, "Transaction failed")

                // Build detailed error message
                val message = buildString {
                    append("Transaction Failed")

                    // Add exception message
                    t.message?.let {
                        append("\n\n")
                        append(it)
                    }

                    // Add exception type
                    append("\n\nError Type: ${t.javaClass.simpleName}")

                    // If it's a KoardException, show more details
                    if (t is com.koardlabs.merchant.sdk.domain.exception.KoardException) {
                        append("\nError: ${t.error.shortMessage}")
                        t.error.errorType?.let { errorType ->
                            append("\nCategory: ${errorType.javaClass.simpleName}")
                        }
                    }

                    // If we have no message at all, add generic error
                    if (t.message == null) {
                        append("\n\n")
                        append(getApplication<Application>().getString(R.string.transaction_generic_error))
                    }
                }

                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = message)
                }
            }
        }
    }

    fun onMissingActivity() {
        _uiState.update {
            it.copy(
                errorMessage = getApplication<Application>().getString(R.string.transaction_activity_missing)
            )
        }
    }

    fun onDismissTransactionResult() {
        _uiState.update {
            it.copy(
                isProcessing = false,
                finalStatus = null,
                statusMessages = emptyList(),
                transactionId = null,
                transaction = null,
                errorMessage = null,
                showSurchargeConfirmation = false
            )
        }
    }

    fun onSurchargeOverrideAmountChanged(newAmount: String) {
        _uiState.update { it.copy(surchargeOverrideAmount = newAmount) }
    }

    fun onSurchargeOverrideTypeToggled() {
        _uiState.update {
            it.copy(
                surchargeOverrideType = if (it.surchargeOverrideType == AmountType.PERCENTAGE) {
                    AmountType.FIXED
                } else {
                    AmountType.PERCENTAGE
                }
            )
        }
    }

    fun onConfirmSurcharge(confirm: Boolean) {
        val transactionId = _uiState.value.transactionId ?: return
        val transaction = _uiState.value.transaction ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isConfirmingSurcharge = true, errorMessage = null) }

            try {
                // Check if user provided surcharge override
                val overrideAmount = _uiState.value.surchargeOverrideAmount.trim()
                val breakdown: com.koardlabs.merchant.sdk.domain.PaymentBreakdown?
                val totalAmount: Int?

                if (overrideAmount.isNotBlank()) {
                    // User overrode the surcharge - calculate new breakdown
                    val overrideValue = overrideAmount.toDoubleOrNull()
                    if (overrideValue == null) {
                        _uiState.update {
                            it.copy(
                                isConfirmingSurcharge = false,
                                errorMessage = "Invalid surcharge amount"
                            )
                        }
                        return@launch
                    }

                    val subtotal = transaction.subtotal
                    val tip = transaction.tipAmount
                    val tax = transaction.taxAmount

                    val newSurcharge = if (_uiState.value.surchargeOverrideType == AmountType.PERCENTAGE) {
                        // Percentage of subtotal
                        ((subtotal.toDouble() * overrideValue) / 100.0).toInt()
                    } else {
                        // Fixed amount in cents
                        (overrideValue * 100).toInt()
                    }

                    totalAmount = subtotal + tip + tax + newSurcharge

                    breakdown = com.koardlabs.merchant.sdk.domain.PaymentBreakdown(
                        subtotal = subtotal,
                        tipAmount = tip,
                        tipType = if (_uiState.value.surchargeOverrideType == AmountType.PERCENTAGE) "percentage" else "fixed",
                        taxAmount = tax,
                        surcharge = com.koardlabs.merchant.sdk.domain.Surcharge(
                            amount = if (_uiState.value.surchargeOverrideType == AmountType.FIXED) newSurcharge else null,
                            percentage = if (_uiState.value.surchargeOverrideType == AmountType.PERCENTAGE) overrideValue else null
                        )
                    )
                } else {
                    // No override - just confirm as is
                    breakdown = null
                    totalAmount = null
                }

                val result = koardSdk.confirm(
                    transactionId = transactionId,
                    confirm = confirm,
                    breakdown = breakdown,
                    amount = totalAmount
                )

                if (result.isSuccess) {
                    val updatedTransaction = result.getOrNull()
                    Timber.d("Surcharge confirmation succeeded: $confirm")
                    _uiState.update {
                        it.copy(
                            transaction = updatedTransaction,
                            showSurchargeConfirmation = false,
                            isConfirmingSurcharge = false,
                            finalStatus = if (confirm) "Surcharge Accepted" else "Surcharge Declined",
                            surchargeOverrideAmount = ""
                        )
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to confirm surcharge"
                    Timber.e("Surcharge confirmation failed: $error")
                    _uiState.update {
                        it.copy(
                            isConfirmingSurcharge = false,
                            errorMessage = error
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error confirming surcharge")
                _uiState.update {
                    it.copy(
                        isConfirmingSurcharge = false,
                        errorMessage = e.message ?: "Error confirming surcharge"
                    )
                }
            }
        }
    }

    private fun handleTransactionResponse(response: KoardTransactionResponse) {
        // Format reader status as clean sentence
        val statusMessage = formatReaderStatus(response.readerStatus)

        Timber.d("Transaction response: readerStatus=${response.readerStatus}, action=${response.actionStatus}")

        when (response.actionStatus) {
            KoardTransactionActionStatus.OnProgress -> {
                _uiState.update {
                    it.copy(
                        statusMessages = listOf(statusMessage),
                        errorMessage = null
                    )
                }
            }

            KoardTransactionActionStatus.OnFailure -> {
                // Status code 12 is not an actual failure - just a progress status
                if (response.statusCode == 12) {
                    _uiState.update {
                        it.copy(
                            statusMessages = listOf(statusMessage)
                        )
                    }
                    return@handleTransactionResponse
                }

                // Build detailed error message with all available information
                val failureMessage = buildString {
                    append("Transaction Failed")

                    // Add display message if available
                    response.displayMessage?.let {
                        append("\n\n")
                        append(it)
                    }

                    // Add status code if available
                    response.statusCode?.let {
                        append("\n\nStatus Code: $it")
                    }

                    // Add final status if available
                    response.finalStatus?.let {
                        append("\nFinal Status: ${resolveFinalStatusLabel(it)}")
                    }

                    // Add reader status if available and not unknown
                    if (response.readerStatus != com.koardlabs.merchant.sdk.domain.KoardReaderStatus.unknown) {
                        append("\nReader Status: ${formatReaderStatus(response.readerStatus)}")
                    }

                    // If we have no details at all, add generic message
                    if (response.displayMessage == null && response.statusCode == null && response.finalStatus == null) {
                        append("\n\n")
                        append(getApplication<Application>().getString(R.string.transaction_failure_generic))
                    }
                }

                _uiState.update {
                    it.copy(
                        statusMessages = listOf(failureMessage),
                        errorMessage = failureMessage,
                        isProcessing = false
                    )
                }
            }

            KoardTransactionActionStatus.OnComplete -> {
                // Check if surcharge confirmation is needed
                if (response.transaction?.status == com.koardlabs.merchant.sdk.domain.KoardTransactionStatus.SURCHARGE_PENDING) {
                    Timber.d("Transaction requires surcharge confirmation")
                    val txn = response.transaction
                    _uiState.update {
                        it.copy(
                            statusMessages = listOf(statusMessage),
                            transactionId = txn?.transactionId,
                            transaction = txn,
                            showSurchargeConfirmation = true,
                            errorMessage = null,
                            isProcessing = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            statusMessages = listOf(statusMessage),
                            finalStatus = resolveFinalStatusLabel(response.finalStatus),
                            transactionId = response.transaction?.transactionId ?: response.transactionId,
                            transaction = response.transaction,
                            errorMessage = null,
                            isProcessing = false
                        )
                    }
                }
            }

            else -> {
                _uiState.update {
                    it.copy(
                        statusMessages = listOf(statusMessage)
                    )
                }
            }
        }
    }

    private fun formatReaderStatus(readerStatus: com.koardlabs.merchant.sdk.domain.KoardReaderStatus): String {
        return readerStatus.toString()
    }

    private fun resolveFinalStatusLabel(finalStatus: KoardTransactionFinalStatus?): String? {
        finalStatus ?: return null
        val app = getApplication<Application>()
        return when (finalStatus) {
            KoardTransactionFinalStatus.Approve -> app.getString(R.string.transaction_final_status_approve)
            KoardTransactionFinalStatus.Abort -> app.getString(R.string.transaction_final_status_abort)
            KoardTransactionFinalStatus.Decline -> app.getString(R.string.transaction_final_status_decline)
            KoardTransactionFinalStatus.Failure -> app.getString(R.string.transaction_final_status_failure)
            KoardTransactionFinalStatus.AltService -> app.getString(R.string.transaction_final_status_alt_service)
            is KoardTransactionFinalStatus.Unknown -> finalStatus.rawStatus
        }
    }
}

data class TransactionFlowUiState(
    val amount: String = "",
    val taxAmountPercentage: String = "",
    val taxAmountFixed: String = "",
    val taxType: AmountType = AmountType.PERCENTAGE,
    val tipAmountPercentage: String = "",
    val tipAmountFixed: String = "",
    val tipType: AmountType = AmountType.PERCENTAGE,
    val surchargeState: SurchargeState = SurchargeState.OFF,
    val surchargeAmountPercentage: String = "",
    val surchargeAmountFixed: String = "",
    val surchargeType: AmountType = AmountType.PERCENTAGE,
    val paymentType: String = DEFAULT_PAYMENT_TYPE,
    val isProcessing: Boolean = false,
    val statusMessages: List<String> = emptyList(),
    val finalStatus: String? = null,
    val transactionId: String? = null,
    val transaction: com.koardlabs.merchant.sdk.domain.KoardTransaction? = null,
    val errorMessage: String? = null,
    val showSurchargeConfirmation: Boolean = false,
    val isConfirmingSurcharge: Boolean = false,
    val surchargeOverrideAmount: String = "",
    val surchargeOverrideType: AmountType = AmountType.PERCENTAGE
) {
    val taxAmount: String
        get() = if (taxType == AmountType.PERCENTAGE) taxAmountPercentage else taxAmountFixed

    val tipAmount: String
        get() = if (tipType == AmountType.PERCENTAGE) tipAmountPercentage else tipAmountFixed

    val surchargeAmount: String
        get() = if (surchargeType == AmountType.PERCENTAGE) surchargeAmountPercentage else surchargeAmountFixed

    val calculatedTotal: Double
        get() {
            val subtotal = amount.toDoubleOrNull() ?: 0.0

            // Calculate tip
            val tipValue = when (tipType) {
                AmountType.PERCENTAGE -> {
                    val rate = tipAmount.toDoubleOrNull() ?: 0.0
                    subtotal * (rate / 100.0)
                }
                AmountType.FIXED -> tipAmount.toDoubleOrNull() ?: 0.0
            }

            // Calculate tax (on subtotal + tip)
            val taxValue = when (taxType) {
                AmountType.PERCENTAGE -> {
                    val rate = taxAmount.toDoubleOrNull() ?: 0.0
                    (subtotal + tipValue) * (rate / 100.0)
                }
                AmountType.FIXED -> taxAmount.toDoubleOrNull() ?: 0.0
            }

            // Calculate surcharge (on subtotal + tip + tax)
            val surchargeValue = if (surchargeState == SurchargeState.ENABLE) {
                val baseAmount = subtotal + tipValue + taxValue
                when (surchargeType) {
                    AmountType.PERCENTAGE -> {
                        val rate = surchargeAmount.toDoubleOrNull() ?: 0.0
                        baseAmount * (rate / 100.0)
                    }
                    AmountType.FIXED -> surchargeAmount.toDoubleOrNull() ?: 0.0
                }
            } else {
                0.0
            }

            return subtotal + tipValue + taxValue + surchargeValue
        }
}


enum class SurchargeState {
    OFF,
    BYPASS,
    ENABLE
}

private const val DEFAULT_PAYMENT_TYPE = "Payment"
