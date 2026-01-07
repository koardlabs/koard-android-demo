package com.koard.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.koard.android.BuildConfig
import com.koard.android.R
import com.koardlabs.merchant.sdk.KoardMerchantSdk
import com.koardlabs.merchant.sdk.domain.KoardLocation
import com.koardlabs.merchant.sdk.domain.exception.KoardException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val koardSdk = KoardMerchantSdk.getInstance()

    private val _uiState = MutableStateFlow(
        MainScreenUiState(
            isAuthenticated = koardSdk.isAuthenticated,
            hasDeviceCertificates = koardSdk.isCertificateGenerated(),
            isDeviceEnrolled = koardSdk.isDeviceEnrolled
        )
    )
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private val intents = MutableSharedFlow<MainScreenIntent>()
    private val _effects = Channel<MainScreenEffect>()
    val effects = _effects.receiveAsFlow()

    // Expose SDK readiness state
    val sdkReadiness = koardSdk.readinessState

    init {
        viewModelScope.launch {
            intents.collect { intent ->
                when (intent) {
                    is MainScreenIntent.Login -> login()
                    is MainScreenIntent.Logout -> logout()
                    MainScreenIntent.OnSelectLocation -> onSelectLocation()
                    is MainScreenIntent.OnLocationSelected -> onLocationSelected(intent.location)
                    MainScreenIntent.EnrollDevice -> enrollDevice()
                }
            }
        }

        // Load active location if authenticated and location is set
        if (koardSdk.isAuthenticated) {
            viewModelScope.launch(Dispatchers.IO) {
                koardSdk.activeLocationId?.let { locationId ->
                    try {
                        val result = koardSdk.getLocation(locationId)
                        result.onSuccess { location ->
                            _uiState.update { it.copy(selectedLocation = location) }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to load active location")
                    }
                }
            }
        }
    }

    private suspend fun login() = withContext(Dispatchers.IO) {
        _uiState.update { it.copy(isAuthenticating = true, authenticationError = "") }

        val isSuccess = koardSdk.login(
            merchantCode = BuildConfig.MERCHANT_CODE,
            merchantPin = BuildConfig.MERCHANT_PIN
        )

        _uiState.update {
            it.copy(
                isAuthenticated = isSuccess,
                isAuthenticating = false,
                authenticationError = "",
                hasDeviceCertificates = koardSdk.isCertificateGenerated(),
                isDeviceEnrolled = koardSdk.isDeviceEnrolled
            )
        }
        if (!isSuccess) {
            _uiState.update {
                it.copy(
                    authenticationError = getApplication<Application>().getString(
                        R.string.authentication_failed
                    )
                )
            }
        } else {
            Timber.d("Authentication successful")

            // Load active location if one is saved
            koardSdk.activeLocationId?.let { locationId ->
                try {
                    val result = koardSdk.getLocation(locationId)
                    result.onSuccess { location ->
                        _uiState.update { it.copy(selectedLocation = location) }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load active location after login")
                }
            }
        }
    }

    private suspend fun logout() = withContext(Dispatchers.IO) {
        koardSdk.logout()
        _uiState.update {
            it.copy(
                isAuthenticated = false,
                isAuthenticating = false,
                authenticationError = "",
                selectedLocation = null,
                hasDeviceCertificates = koardSdk.isCertificateGenerated(),
                isDeviceEnrolled = koardSdk.isDeviceEnrolled
            )
        }
        Timber.d("User logged out")
    }

    private suspend fun onSelectLocation() = withContext(Dispatchers.IO) {
        try {
            val result = koardSdk.getLocations()
            if (result.isSuccess) {
                val locations = result.getOrNull().orEmpty()
                if (locations.isNotEmpty()) {
                    _effects.send(MainScreenEffect.ShowLocationSheet(locations))
                } else {
                    Timber.w("No locations available")
                }
            } else {
                Timber.e("Failed to fetch locations: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching locations")
        }
    }

    private suspend fun onLocationSelected(location: KoardLocation) = withContext(Dispatchers.IO) {
        koardSdk.setActiveLocation(location.id)
        _uiState.update { it.copy(selectedLocation = location) }
    }

    private suspend fun enrollDevice() = withContext(Dispatchers.IO) {
        _uiState.update { it.copy(isEnrollingDevice = true, enrollmentError = null) }
        try {
            Timber.d("Enrolling device...")
            val message = koardSdk.enrollDevice()
            Timber.d("Enrollment result: $message")
        } catch (e: KoardException) {
            Timber.e(e, "Failed to enroll device")
            _uiState.update { it.copy(enrollmentError = e.error.shortMessage) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to enroll device")
            _uiState.update { it.copy(enrollmentError = e.message ?: "Failed to enroll device") }
        } finally {
            _uiState.update {
                it.copy(
                    isEnrollingDevice = false,
                    isDeviceEnrolled = koardSdk.isDeviceEnrolled
                )
            }
        }
    }

    fun onDispatch(intent: MainScreenIntent) {
        viewModelScope.launch { intents.emit(intent) }
    }
}

data class MainScreenUiState(
    val isAuthenticated: Boolean = false,
    val isAuthenticating: Boolean = false,
    val authenticationError: String = "",
    val selectedLocation: KoardLocation? = null,
    val lastTransactionStatusCode: Int? = null,
    val lastTransactionActionStatus: String? = null,
    val lastTransactionFinalStatus: String? = null,
    val isGeneratingCertificates: Boolean = false,
    val isEnrollingDevice: Boolean = false,
    val hasDeviceCertificates: Boolean = false,
    val isDeviceEnrolled: Boolean = false,
    val enrollmentError: String? = null
)

sealed class MainScreenIntent {
    data object Login : MainScreenIntent()
    data object Logout : MainScreenIntent()
    data object OnSelectLocation : MainScreenIntent()
    data class OnLocationSelected(val location: KoardLocation) : MainScreenIntent()
    data object EnrollDevice : MainScreenIntent()
}

sealed interface MainScreenEffect {
    data class ShowLocationSheet(val locations: List<KoardLocation>) : MainScreenEffect
}
