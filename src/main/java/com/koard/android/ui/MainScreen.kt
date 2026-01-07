package com.koard.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koard.android.R
import com.koard.android.ui.theme.KoardAndroidSDKTheme
import com.koard.android.ui.theme.KoardGreen800
import com.koard.android.ui.theme.KoardRed500
import com.koardlabs.merchant.sdk.domain.KoardLocation
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(),
    onNavigateToTransactionFlow: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val sdkReadiness by viewModel.sdkReadiness.collectAsState()
    var showLocationSheet by remember { mutableStateOf(false) }
    var availableLocations by remember { mutableStateOf<List<KoardLocation>>(emptyList()) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MainScreenEffect.ShowLocationSheet -> {
                    availableLocations = effect.locations
                    showLocationSheet = true
                }
            }
        }
    }

    Scaffold { paddingValues ->
        MainScreenContent(
            modifier = modifier.padding(paddingValues),
            isAuthenticated = uiState.isAuthenticated,
            isAuthenticating = uiState.isAuthenticating,
            authenticationError = uiState.authenticationError,
            selectedLocation = uiState.selectedLocation,
            sdkReadiness = sdkReadiness,
            lastTransactionStatusCode = uiState.lastTransactionStatusCode,
            lastTransactionActionStatus = uiState.lastTransactionActionStatus,
            lastTransactionFinalStatus = uiState.lastTransactionFinalStatus,
            isGeneratingCertificates = uiState.isGeneratingCertificates,
            isEnrollingDevice = uiState.isEnrollingDevice,
            hasDeviceCertificates = uiState.hasDeviceCertificates,
            isDeviceEnrolled = uiState.isDeviceEnrolled,
            enrollmentError = uiState.enrollmentError,
            onDispatch = viewModel::onDispatch,
            onNavigateToTransactionFlow = onNavigateToTransactionFlow
        )
    }

    if (showLocationSheet) {
        LocationSelectionSheet(
            locations = availableLocations,
            selectedLocation = uiState.selectedLocation,
            onLocationSelected = { location ->
                showLocationSheet = false
                viewModel.onDispatch(MainScreenIntent.OnLocationSelected(location))
            },
            onDismiss = { showLocationSheet = false }
        )
    }
}

@Composable
private fun MainScreenContent(
    modifier: Modifier = Modifier,
    isAuthenticated: Boolean = false,
    isAuthenticating: Boolean = false,
    authenticationError: String = "",
    selectedLocation: KoardLocation?,
    sdkReadiness: com.koardlabs.merchant.sdk.domain.KoardSdkReadiness = com.koardlabs.merchant.sdk.domain.KoardSdkReadiness(),
    lastTransactionStatusCode: Int? = null,
    lastTransactionActionStatus: String? = null,
    lastTransactionFinalStatus: String? = null,
    isGeneratingCertificates: Boolean = false,
    isEnrollingDevice: Boolean = false,
    hasDeviceCertificates: Boolean = false,
    isDeviceEnrolled: Boolean = false,
    enrollmentError: String? = null,
    onDispatch: (MainScreenIntent) -> Unit,
    onNavigateToTransactionFlow: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(.5f)
                .background(Color.White, RoundedCornerShape(16.dp))
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.brand_name),
                color = KoardGreen800,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(6.dp)
            )
        }

        Spacer(modifier = Modifier.padding(16.dp))

        // SDK Readiness Status - move above auth button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "SDK Status:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = sdkReadiness.getStatusMessage(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = if (sdkReadiness.isReadyForTransactions) {
                    KoardGreen800
                } else if (sdkReadiness.hasBlockingIssue) {
                    KoardRed500
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(
                onClick = {
                    if (isAuthenticated) {
                        onDispatch(MainScreenIntent.Logout)
                    } else {
                        onDispatch(MainScreenIntent.Login)
                    }
                },
                enabled = !isAuthenticating,
                modifier = Modifier
            ) {
                Text(
                    text = if (isAuthenticated) {
                        stringResource(R.string.logout)
                    } else {
                        stringResource(R.string.authenticate_merchant)
                    }
                )
            }

            if (isAuthenticating) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(isAuthenticated) { targetState ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = targetState,
                    onClick = { onDispatch(MainScreenIntent.OnSelectLocation) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = selectedLocation?.name ?: stringResource(R.string.select_location),
                        textDecoration = TextDecoration.Underline
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(
                onClick = { onDispatch(MainScreenIntent.EnrollDevice) },
                enabled = isAuthenticated && !isEnrollingDevice && !isDeviceEnrolled
            ) {
                when {
                    isEnrollingDevice -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text("Enrolling...")
                        }
                    }
                    isDeviceEnrolled -> {
                        Text("Enrolled")
                    }
                    else -> {
                        Text("Enroll Device")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Display last transaction debug info
        if (lastTransactionStatusCode != null || lastTransactionActionStatus != null || lastTransactionFinalStatus != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Last Transaction Debug:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Start).padding(horizontal = 16.dp)
            )
            lastTransactionStatusCode?.let { code ->
                Text(
                    text = "Status Code: $code",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start).padding(start = 32.dp, top = 4.dp)
                )
            }
            lastTransactionActionStatus?.let { status ->
                Text(
                    text = "Action Status: $status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start).padding(start = 32.dp, top = 4.dp)
                )
            }
            lastTransactionFinalStatus?.let { status ->
                Text(
                    text = "Final Status: $status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start).padding(start = 32.dp, top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (authenticationError.isNotBlank()) {
            Text(
                text = authenticationError,
                fontSize = 12.sp,
                color = KoardRed500,
                modifier = Modifier
            )
        }

        if (!enrollmentError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = enrollmentError,
                fontSize = 12.sp,
                color = KoardRed500,
                modifier = Modifier
            )
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(
                onClick = onNavigateToTransactionFlow,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.process_sample_transaction))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSelectionSheet(
    locations: List<KoardLocation>,
    selectedLocation: KoardLocation?,
    onLocationSelected: (KoardLocation) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            LazyColumn {
                items(locations) { location ->
                    LocationItem(
                        location = location,
                        isSelected = location.id == selectedLocation?.id,
                        onLocationClick = { onLocationSelected(location) },
                        isLast = location == locations.last()
                    )
                }
            }

            Spacer(modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun LocationItem(
    location: KoardLocation,
    isSelected: Boolean = false,
    onLocationClick: () -> Unit,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLocationClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = location.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = KoardGreen800
        )

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.selected),
                tint = KoardGreen800
            )
        }
    }

    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp),
            thickness = 0.5.dp
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    KoardAndroidSDKTheme {
        Surface {
            MainScreenContent(
                isAuthenticated = false,
                isAuthenticating = false,
                authenticationError = "",
                selectedLocation = null,
                hasDeviceCertificates = false,
                isDeviceEnrolled = false,
                enrollmentError = null,
                onDispatch = {},
                onNavigateToTransactionFlow = {}
            )
        }
    }
}
