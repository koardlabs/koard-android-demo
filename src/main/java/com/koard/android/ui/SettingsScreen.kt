package com.koard.android.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.koard.android.R
import com.koardlabs.merchant.sdk.KoardMerchantSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Refresh settings every time the screen is displayed
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshSettings()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Kernel App Status - at the top
        KernelStatusRow(uiState.isKernelInstalled)

        Spacer(modifier = Modifier.height(24.dp))

        StatusRow("Developer Mode", if (uiState.isDeveloperModeEnabled) "Enabled ⚠️" else "Disabled")
        StatusRow("SDK Status", uiState.sdkStatusMessage)
        Spacer(modifier = Modifier.height(24.dp))

        // Actions Section
        Text(
            text = "Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.clearEnrollmentData() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isDeviceEnrolled
        ) {
            Text("Clear All Data")
        }

        if (uiState.isDeviceEnrolled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Device is enrolled. Clear All Data is disabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun KernelStatusRow(isInstalled: Boolean) {
    val uriHandler = LocalUriHandler.current
    val playStoreUrl = "https://play.google.com/store/apps/details?id=com.visa.kic.app.kernel"

    if (isInstalled) {
        Text(
            text = "Kernel App is installed ✓",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    } else {
        val annotatedString = buildAnnotatedString {
            withStyle(style = SpanStyle(color = Color.Gray)) {
                append("Kernel app needs to be installed: ")
            }
            pushStringAnnotation(tag = "URL", annotation = playStoreUrl)
            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                append("Install")
            }
            pop()
        }

        ClickableText(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium,
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        uriHandler.openUri(annotation.item)
                    }
            }
        )
    }
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val sdk = KoardMerchantSdk.getInstance()
    private val readinessState = sdk.readinessState
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val isDeveloperMode = sdk.isDeveloperModeEnabled()
        val readinessMessage = readinessState.value.getStatusMessage()
        val isEnrolled = sdk.isDeviceEnrolled

        _uiState.update {
            it.copy(
                isKernelInstalled = sdk.isKernelAppInstalled,
                isDeveloperModeEnabled = isDeveloperMode,
                sdkStatusMessage = readinessMessage,
                isDeviceEnrolled = isEnrolled
            )
        }
    }

    fun clearEnrollmentData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sdk.clearEnrollmentState()
                loadSettings()
            } catch (e: Exception) {
                Timber.w(e, "Failed to clear enrollment data")
            }
        }
    }

    fun refreshSettings() {
        loadSettings()
    }

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sdk.refreshDeviceCertificates()
            } catch (e: Exception) {
                // Ignore errors
            }
            // Reload settings after refresh
            loadSettings()
        }
    }
}

data class SettingsUiState(
    val isKernelInstalled: Boolean = false,
    val isDeveloperModeEnabled: Boolean = false,
    val sdkStatusMessage: String = "Unknown",
    val isDeviceEnrolled: Boolean = false
)
