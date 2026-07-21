# Wi-Fi RTT Measurement App Blueprint

## Scope

Research/prototype Android application for real-time Wi-Fi RTT ranging between Android devices.

The app supports two roles:

- Receiver: one device per measurement session, scans publishers, measures Wi-Fi RTT distance sequentially, displays dashboard, graph, logs, details, and exports CSV.
- Publisher: multiple devices per measurement session, remain available as Wi-Fi RTT responders and expose status/logging information.

Only Wi-Fi RTT is in scope. No Bluetooth, BLE, NFC, Wi-Fi Direct, Nearby Connections, or alternative discovery/ranging technologies are part of this design.

## Technology Baseline

- Kotlin
- Gradle Kotlin DSL
- Minimum SDK: 28, because Android Wi-Fi RTT APIs require Android 9 / API 28
- Target/compile SDK: latest stable Android SDK available in the build environment
- Jetpack Compose
- Material Design 3
- Navigation Compose
- ViewModel
- Hilt
- Coroutines
- Flow / StateFlow
- DataStore Preferences
- Android Wi-Fi RTT APIs
- Clean Architecture
- Repository Pattern

## Increment Order

1. Project setup
2. Navigation
3. Role Selection
4. Publisher implementation
5. Receiver implementation
6. Wi-Fi RTT manager
7. Measurement engine
8. Live dashboard
9. Graph implementation
10. CSV export
11. Logging
12. Polishing
13. Testing

Each increment must compile before moving to the next.

## Folder Structure

```text
app/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    java/com/example/wifirttmeasurement/
      WifiRttMeasurementApp.kt
      MainActivity.kt
      presentation/
        navigation/
          AppNavHost.kt
          AppRoute.kt
        ui/
          splash/
            SplashScreen.kt
            SplashViewModel.kt
            SplashUiState.kt
          role/
            RoleSelectionScreen.kt
            RoleSelectionViewModel.kt
            RoleSelectionUiState.kt
          receiver/
            ReceiverScreen.kt
            ReceiverViewModel.kt
            ReceiverUiState.kt
            PublisherDetailsScreen.kt
            PublisherDetailsViewModel.kt
            PublisherDetailsUiState.kt
            components/
              ReceiverDashboard.kt
              PublisherList.kt
              MeasurementControls.kt
              MeasurementLogList.kt
              DistanceGraph.kt
          publisher/
            PublisherScreen.kt
            PublisherViewModel.kt
            PublisherUiState.kt
            components/
              PublisherDeviceCard.kt
              PublisherStatusCard.kt
              PublisherControls.kt
              PublisherLogList.kt
          components/
            AppScaffold.kt
            PermissionDialog.kt
            StatusIndicator.kt
            StatCard.kt
            LogRow.kt
          theme/
            Color.kt
            Theme.kt
            Type.kt
      domain/
        model/
          AppRole.kt
          BatteryStatus.kt
          ConnectionStatus.kt
          DashboardStats.kt
          DeviceCapability.kt
          MeasurementLog.kt
          MeasurementResult.kt
          MeasurementStatus.kt
          PublisherDevice.kt
          PublisherStatus.kt
          RttFailureReason.kt
          RttPermissionState.kt
          SessionState.kt
        repository/
          AppPreferencesRepository.kt
          CsvExportRepository.kt
          LogRepository.kt
          PublisherRepository.kt
          ReceiverRepository.kt
          RttRepository.kt
        usecase/
          CheckDeviceCapabilitiesUseCase.kt
          ExportMeasurementsCsvUseCase.kt
          ObserveLogsUseCase.kt
          ObservePublisherStatusUseCase.kt
          ObserveReceiverStateUseCase.kt
          ScanPublishersUseCase.kt
          StartMeasurementSessionUseCase.kt
          StartPublishingUseCase.kt
          StopMeasurementSessionUseCase.kt
          StopPublishingUseCase.kt
      data/
        repository/
          AppPreferencesRepositoryImpl.kt
          CsvExportRepositoryImpl.kt
          LogRepositoryImpl.kt
          PublisherRepositoryImpl.kt
          ReceiverRepositoryImpl.kt
          RttRepositoryImpl.kt
        rtt/
          AndroidRttManager.kt
          RttCapabilityChecker.kt
          RttMeasurementEngine.kt
          RttPublisherController.kt
          RttScanCoordinator.kt
        datastore/
          AppPreferencesDataStore.kt
          DataStoreKeys.kt
        csv/
          CsvManager.kt
          MeasurementCsvFormatter.kt
        mapper/
          RttResultMapper.kt
      di/
        AppModule.kt
        DataModule.kt
        DispatcherModule.kt
        RttModule.kt
        RepositoryModule.kt
      utils/
        AppDispatchers.kt
        ClockProvider.kt
        Result.kt
```

## Data Models

```kotlin
enum class AppRole {
    Receiver,
    Publisher
}

enum class ConnectionStatus {
    Connected,
    Disconnected,
    Connecting,
    Unreachable
}

enum class PublisherStatus {
    Waiting,
    Busy,
    Offline
}

enum class MeasurementStatus {
    Success,
    Failed,
    Timeout,
    Unsupported,
    PermissionDenied
}

enum class RttFailureReason {
    None,
    PermissionDenied,
    RttUnsupported,
    ResponderUnavailable,
    Timeout,
    InvalidSession,
    ApiFailure,
    Unknown
}

data class DeviceCapability(
    val isRttAvailable: Boolean,
    val canActAsReceiver: Boolean,
    val canActAsPublisher: Boolean,
    val failureReason: RttFailureReason? = null
)

data class PublisherDevice(
    val id: String,
    val name: String,
    val connectionStatus: ConnectionStatus,
    val status: PublisherStatus,
    val lastMeasuredDistanceMeters: Double?,
    val lastRssiDbm: Int?,
    val lastMeasurementTimestampMillis: Long?
)

data class MeasurementResult(
    val timestampMillis: Long,
    val publisherId: String,
    val publisherName: String,
    val distanceMeters: Double?,
    val distanceStandardDeviationMeters: Double?,
    val rssiDbm: Int?,
    val status: MeasurementStatus,
    val failureReason: RttFailureReason,
    val roundNumber: Long,
    val measurementNumber: Long
)

data class DashboardStats(
    val currentMeasurements: Int,
    val averageDistanceMeters: Double?,
    val minimumDistanceMeters: Double?,
    val maximumDistanceMeters: Double?,
    val medianDistanceMeters: Double?,
    val standardDeviationMeters: Double?,
    val totalMeasurements: Long,
    val measurementRateHz: Double,
    val activePublishers: Int
)

data class MeasurementLog(
    val id: String,
    val timestampMillis: Long,
    val message: String,
    val severity: LogSeverity
)

enum class LogSeverity {
    Info,
    Warning,
    Error
}
```

## Navigation Graph

```text
Splash
  -> RoleSelection

RoleSelection
  -> ReceiverHome
  -> PublisherHome

ReceiverHome
  -> PublisherDetails/{publisherId}

PublisherDetails
  -> ReceiverHome
```

Route ownership:

- Splash decides only whether app initialization is ready.
- RoleSelection persists the selected role in DataStore, then navigates.
- ReceiverHome owns scanning, measuring, dashboard, graph, logs, and CSV export entry point.
- PublisherHome owns publishing lifecycle, responder status, battery percentage, request count, and logs.
- PublisherDetails is read-only analytics for one publisher.

## Dependency Graph

```text
Presentation
  ViewModels
    -> Use Cases
      -> Repository interfaces
        -> Repository implementations
          -> AndroidRttManager
          -> RttMeasurementEngine
          -> RttPublisherController
          -> CsvManager
          -> DataStore
          -> LogRepository

DI
  DispatcherModule
    -> AppDispatchers
  DataModule
    -> DataStore
  RttModule
    -> AndroidRttManager
    -> RttCapabilityChecker
    -> RttMeasurementEngine
    -> RttPublisherController
  RepositoryModule
    -> AppPreferencesRepository
    -> RttRepository
    -> ReceiverRepository
    -> PublisherRepository
    -> CsvExportRepository
    -> LogRepository
```

Dependency rule:

- Presentation depends on Domain.
- Domain depends on no Android framework APIs.
- Data depends on Domain and Android framework APIs.
- DI wires concrete implementations at the app boundary.

## UI Wireframes

### Splash

```text
┌─────────────────────────────┐
│                             │
│      Wi-Fi RTT Lab          │
│      Real-time ranging      │
│                             │
│        progress             │
│                             │
└─────────────────────────────┘
```

### Role Selection

```text
┌─────────────────────────────┐
│ Wi-Fi RTT Lab               │
│ Choose device role          │
│                             │
│ ┌─────────────┐ ┌─────────┐ │
│ │ Receiver    │ │Publisher│ │
│ │ Measure RTT │ │Respond  │ │
│ └─────────────┘ └─────────┘ │
│                             │
│ Capability summary          │
└─────────────────────────────┘
```

### Receiver Home

```text
┌───────────────────────────────────────┐
│ Receiver                              │
│ [Scan] [Measure Selected] [Measure All]│
│ [Stop] [Export CSV]                   │
│                                       │
│ Stats                                 │
│ ┌Avg┐ ┌Min┐ ┌Max┐ ┌Median┐ ┌Rate┐     │
│                                       │
│ Publishers                            │
│ ○ Publisher A  Connected  2.31 m RSSI │
│ ○ Publisher B  Connected  5.67 m RSSI │
│ ○ Publisher C  Connected  1.92 m RSSI │
│                                       │
│ Live Graph                            │
│ time -> distance, one line/publisher  │
│                                       │
│ Logs                                  │
└───────────────────────────────────────┘
```

### Publisher Home

```text
┌─────────────────────────────┐
│ Publisher                   │
│                             │
│ Device Information          │
│ Name, Publisher ID, Battery │
│                             │
│ Status                      │
│ Green/Waiting/Busy/Offline  │
│ Connection Status           │
│ Waiting for RTT Requests    │
│ Requests Received           │
│ Last Measurement Timestamp  │
│                             │
│ Logs                        │
│                             │
│ [Start Publishing] [Stop]   │
└─────────────────────────────┘
```

### Publisher Details

```text
┌─────────────────────────────┐
│ Publisher A                 │
│ ID, status, last seen       │
│                             │
│ Avg / Min / Max / Success % │
│                             │
│ RSSI History                │
│ Measurement Timeline        │
│ Historical Measurements     │
└─────────────────────────────┘
```

## Class Diagram

```text
MainActivity
  -> AppNavHost

AppNavHost
  -> SplashScreen
  -> RoleSelectionScreen
  -> ReceiverScreen
  -> PublisherScreen
  -> PublisherDetailsScreen

SplashViewModel
  -> CheckDeviceCapabilitiesUseCase

RoleSelectionViewModel
  -> AppPreferencesRepository

PublisherViewModel
  -> CheckDeviceCapabilitiesUseCase
  -> StartPublishingUseCase
  -> StopPublishingUseCase
  -> ObservePublisherStatusUseCase
  -> ObserveLogsUseCase

ReceiverViewModel
  -> CheckDeviceCapabilitiesUseCase
  -> ScanPublishersUseCase
  -> StartMeasurementSessionUseCase
  -> StopMeasurementSessionUseCase
  -> ExportMeasurementsCsvUseCase
  -> ObserveReceiverStateUseCase
  -> ObserveLogsUseCase

PublisherDetailsViewModel
  -> ReceiverRepository

StartPublishingUseCase
  -> PublisherRepository

StartMeasurementSessionUseCase
  -> ReceiverRepository

PublisherRepositoryImpl
  -> RttRepository
  -> LogRepository

ReceiverRepositoryImpl
  -> RttRepository
  -> LogRepository

RttRepositoryImpl
  -> AndroidRttManager
  -> RttMeasurementEngine
  -> RttPublisherController
  -> RttCapabilityChecker

CsvExportRepositoryImpl
  -> CsvManager

AppPreferencesRepositoryImpl
  -> AppPreferencesDataStore
```

## Wi-Fi RTT Design Notes

- Receiver uses Android Wi-Fi RTT initiator APIs to request ranges to discovered RTT-capable publishers.
- Publisher mode is modeled behind `RttPublisherController` so responder lifecycle and capability checks remain isolated from UI.
- The project explicitly includes capability checks for receiver and publisher suitability even though supported devices are assumed for the intended research setup.
- Measurement engine performs sequential ranging across publishers to keep per-round timing deterministic and simple to analyze.
- Measurement results are immutable and emitted through StateFlow-backed UI state.
- Long-running measurement sessions use structured coroutine scopes owned by repositories/use cases and cancelled by `StopMeasurementSessionUseCase`.

## Permission Strategy

Runtime permission handling will live at the presentation boundary and feed domain-safe permission states into ViewModels.

Expected Android permissions include location-related Wi-Fi scan/ranging permissions and nearby Wi-Fi device permissions where applicable by SDK version.

The app must:

- Explain why the permission is needed.
- Handle denial gracefully.
- Surface denied permissions through Material dialogs/snackbars.
- Avoid starting scan/ranging/publishing operations while required permissions are missing.

## State Management

All screens expose immutable state objects:

```kotlin
data class ReceiverUiState(...)
data class PublisherUiState(...)
data class RoleSelectionUiState(...)
```

ViewModels own:

```kotlin
private val _uiState = MutableStateFlow(...)
val uiState: StateFlow<...> = _uiState.asStateFlow()
```

Compose screens collect with:

```kotlin
collectAsStateWithLifecycle()
```

## Logging Events

The in-app log stream must support:

- Publisher Started
- Publisher Stopped
- Measurement Started
- Measurement Completed
- Measurement Failed
- Publisher Connected
- Publisher Disconnected
- CSV Exported

## CSV Export

Required columns:

```text
Timestamp,Publisher ID,Publisher Name,Distance,RSSI,Std Dev,Status,Measurement Number
```

CSV export is initiated from Receiver Home and implemented in the data layer behind `CsvExportRepository`.

## Testing Readiness

The architecture is designed for later testing through:

- Repository interfaces in domain.
- Fake RTT manager.
- Mock repositories.
- Pure Kotlin use cases.
- Immutable UI state.
- Flow-based state observation.
