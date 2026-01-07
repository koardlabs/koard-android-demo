# Koard Android SDK Demo App

This Jetpack Compose sample drives the Koard Merchant SDK the same way a production integration would. It logs a merchant in with the configured credentials, lets you pick a location, enroll the device, and run NFC sale or pre-authorization transactions against a real reader experience. Everything lives in a single application module and the SDK is consumed from the bundled local Maven repository so the demo can track SDK changes without depending on a remote artifact.

## Requirements

- Android Studio Hedgehog (or newer) with the Android 15/`compileSdk = 36` platform installed
- JDK 21
- Physical Android 12+ device with NFC hardware (the app targets `minSdk = 31`, so emulators are not supported)
- [Visa Kernel App](https://play.google.com/store/apps/details?id=com.visa.kic.app.kernel) app installed on the device
- Merchant credentials for each environment you plan to test (API key, merchant code, merchant PIN)
- Koard SDK AARs published into the `libs-maven/` directory that ships with this repo

## Setup

### 1. Populate the local Koard SDK artifact

`settings.gradle.kts` adds the root-level `libs-maven/` directory as a Maven repository. Copy (or publish) the current Koard SDK artifacts into `libs-maven/com/koardlabs/koard-android-sdk/` so that Gradle can resolve `com.koardlabs:koard-android-sdk:0.0.1`. The directory structure must match the Maven coordinates (`groupId/artifactId/version`).

### 2. Configure API + merchant credentials

`build.gradle.kts` defines two product flavors:

```kotlin
productFlavors {
    create("prod") {
        dimension = "environment"
        buildConfigField("String", "ENVIRONMENT", "\"PROD\"")
        buildConfigField("String", "API_KEY", "\"<PROD_API_KEY>\"")
        buildConfigField("String", "MERCHANT_CODE", "\"<PROD_MERCHANT_CODE>\"")
        buildConfigField("String", "MERCHANT_PIN", "\"<PROD_MERCHANT_PIN>\"")
    }
    create("uat") {
        dimension = "environment"
        buildConfigField("String", "ENVIRONMENT", "\"UAT\"")
        buildConfigField("String", "API_KEY", "\"<UAT_API_KEY>\"")
        buildConfigField("String", "MERCHANT_CODE", "\"<UAT_MERCHANT_CODE>\"")
        buildConfigField("String", "MERCHANT_PIN", "\"<UAT_MERCHANT_PIN>\"")
        applicationIdSuffix = ".uat"
    }
}
```

Replace the placeholder strings with the credentials that belong to each environment. `DemoApplication` reads `BuildConfig.API_KEY` at launch to initialize the SDK, and the home screen logs in with `BuildConfig.MERCHANT_CODE`/`MERCHANT_PIN`, so the app cannot function until those values are set. Debug builds for `prod` are intentionally disabled by `androidComponents`; use the `uat` flavor for day-to-day testing.

### 3. Build & run the sample

From the repo root:

```bash
# Typical inner-loop build
./gradlew assembleUatDebug installUatDebug

# Production-style release (requires signing if you want to install it)
./gradlew assembleProdRelease
```

You can also open the project directly in Android Studio and select either the `uatDebug` or `prodRelease` configuration. A physical device (with NFC + the Visa KiC app) must be attached when you run the app.

## How the demo works

### Application startup & readiness

- `DemoApplication` initializes `KoardMerchantSdk` on a background dispatcher using the flavor-specific API key and the `ENVIRONMENT` BuildConfig. Initialization has to complete before any Activity is created.
- `MainActivity` calls `registerActivityForNfc()`/`unregisterActivityForNfc()` so the SDK owns the reader lifecycle automatically. You do not need any manual `enableReaderMode` code in the demo.
- The home tab exposes the SDK’s `readinessState` flow. It renders a short status message that turns green once the device has certificates, an active location, the Visa kernel, NFC support, and an enrollment.

### Navigating the UI

The bottom navigation houses three tabs plus two detail flows:

1. **History** – Fetches recent transactions from `KoardMerchantSdk.getTransactions()` and lets you tap into the details.
2. **Home** – Authentication, location selection, enrollment, readiness, and the entry point into the transaction flow.
3. **Settings** – Device diagnostics (kernel install state, developer mode flag, SDK status) and a pre-enrollment data reset.

Tapping **Process Sample Transaction** on the home tab pushes a dedicated transaction flow screen, and tapping a transaction row pushes a transaction details screen. Both detail screens have their own top app bar navigation.

### Authentication, locations, and enrollment (Home tab)

- **Authenticate Merchant** calls `KoardMerchantSdk.login` with the flavor credentials. Once logged in, the button flips to **Logout** and any stored `activeLocationId` is reloaded automatically.
- **Select Location** opens a bottom sheet populated from `KoardMerchantSdk.getLocations()`. The selected location is persisted via `setActiveLocation`.
- **Enroll Device** invokes `KoardMerchantSdk.enrollDevice()`. The button is enabled only when you are authenticated, have picked a location, and the device is not already enrolled. Errors (for example, missing kernel or location) surface inline under the button.
- A **Process Sample Transaction** button at the bottom navigates to the NFC flow only after the previous steps are complete. Last-transaction debug info is shown for quick triage while testing.

### Processing NFC sales or pre-authorizations

The transaction flow screen mirrors a production checkout:

- Enter a subtotal and optionally configure tip, tax, and surcharge inputs. Each amount can toggle between percentage and fixed modes, and surcharge has three states (Off, Bypass, Enable).
- Press **Preauth** or **Sale** to run `koardSdk.preauth` or `koardSdk.sale`. Both calls perform a readiness check before launching the NFC experience and stream `KoardTransactionResponse` updates back to the UI.
- A full-screen overlay shows progress messages (e.g., “Tap card on the back of the device”), final status labels, and any formatted errors. Dismiss it to return to the form.
- If the SDK returns `SURCHARGE_PENDING`, a modal asks you to confirm or override the surcharge. You can enter a new amount or percentage and the demo sends a `confirm` call with the adjusted `PaymentBreakdown`.

### Viewing history and acting on a transaction

- The **History** tab loads the transaction list on entry and exposes a refresh icon to re-query `getTransactions()`.
- Selecting a row navigates to **Transaction Details**, which displays core metadata, reader status, and totals. From here you can trigger the SDK operations that the merchant portal exposes: capture, reverse, incremental authorization, adjust tip, or refund. Each operation opens a modal so you can provide the required amount data before the SDK call executes.
- The details screen also lets you send a receipt over email or SMS with basic validation helpers located in `utils/`.

### Settings and maintenance

- Settings refresh whenever the tab becomes visible. It shows whether the Visa kernel app is installed (with a link to the Play Store if missing), whether developer mode is enabled inside the SDK, and the same readiness status string surfaced on the home screen.
- **Clear All Data** calls `clearEnrollmentState()` so you can wipe cached certificates and locations before enrolling. The action is intentionally disabled once the device is enrolled; to start over you should uninstall the app or unenroll the device from the Koard console first.
- All diagnostics also land in Logcat through Timber, so attach Android Studio’s Logcat or `adb logcat` while running edge cases.

## Updating the SDK dependency

To point the demo at a newer Koard SDK build, replace everything under `libs-maven/com/koardlabs/koard-android-sdk/` with the new published version (either by copying a release drop or by running the SDK repo’s `publish` task against this directory). Keep the same Maven coordinate folder structure so Gradle detects the new version, then rebuild:

```bash
./gradlew assembleUatDebug --refresh-dependencies
```

Gradle will resolve the refreshed artifact directly from the local repository without needing a network connection.

## Troubleshooting

- **SDK dependency not found** – Verify `libs-maven/com/koardlabs/koard-android-sdk/**` exists and was not accidentally nested under another directory level. Remove the Gradle cache (`~/.gradle/caches/modules-2/files-2.1/com.koardlabs`) if you need to force a refresh.
- **Authentication fails instantly** – Double-check that each flavor’s `API_KEY`, `MERCHANT_CODE`, and `MERCHANT_PIN` were updated and that the `ENVIRONMENT` matches the backend you are hitting.
- **No locations in the sheet** – The merchant must have locations provisioned for the environment tied to the API key. Check server logs and Logcat (`Timber` tags) for error descriptions.
- **Cannot enroll / kernel missing** – Confirm the Visa KiC app is installed and up to date. The Settings tab will link you to the Play Store when it is missing. Enrollment also requires a selected location and an authenticated merchant.
- **Transaction buttons stay disabled or fail before tapping** – Enter a non-empty subtotal and ensure the readiness message on the home screen is green. The SDK will block the transaction if enrollment, certificates, NFC, or the kernel are not ready yet.
- **Need to reset an enrolled device** – Because the “Clear All Data” button is disabled post-enrollment, uninstall the app (or clear app storage) and unenroll the device from the Koard console before reinstalling. That guarantees a clean certificate/enrollment state.

© Mycelio, Inc. All rights reserved.
