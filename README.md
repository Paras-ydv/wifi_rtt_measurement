# Wi-Fi RTT Measurement

A research-grade Android app for real-time Wi-Fi RTT (Round-Trip Time) ranging between Android devices. One device acts as the **Receiver** (initiates ranging) and one or more devices act as **Publishers** (Wi-Fi RTT responders). The Receiver measures distance to each Publisher sequentially, displays live stats and a distance graph, and exports results as CSV.

---

## Screenshots

> Run the app on a physical device to see live ranging data. The UI adapts to phone and tablet layouts.

---

## Requirements

| Requirement | Detail |
|---|---|
| Android device | Physical device required — Wi-Fi RTT does **not** work on emulators |
| Android version | Android 9 (API 28) or higher |
| Wi-Fi RTT hardware | Device must support IEEE 802.11mc (Wi-Fi RTT). Check with `PackageManager.FEATURE_WIFI_RTT` |
| Permissions | `ACCESS_FINE_LOCATION`, `NEARBY_WIFI_DEVICES` (API 33+) |
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 |
| Gradle | 8.13 (wrapper included — no manual install needed) |
| Android SDK | API 36 (compile), API 28 (minimum) |

> **Note:** Wi-Fi RTT ranging requires the access point or peer device to support IEEE 802.11mc. Most modern Wi-Fi 6 routers and Pixel devices (Pixel 4+) support this.

---

## How to Run

### 1. Clone the repository

```bash
git clone https://github.com/Paras-ydv/wifi_rtt_measurement.git
cd wifi_rtt_measurement
```

### 2. Open in Android Studio

- Open Android Studio
- Choose **File → Open** and select the `wifi_rtt_measurement` folder
- Wait for Gradle sync to complete (first sync downloads dependencies — allow a few minutes)

### 3. Connect a physical device

- Enable **Developer Options** on your Android device
- Enable **USB Debugging**
- Connect via USB or use wireless debugging

### 4. Build and run

```bash
# From the terminal (Gradle wrapper included)
./gradlew :app:assembleDebug

# Or press the Run button in Android Studio
```

The APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

### 5. Grant permissions

On first launch, the app requests:
- **Fine Location** — required by Android for any Wi-Fi scan or RTT ranging
- **Nearby Wi-Fi Devices** (Android 13+) — required for Wi-Fi scanning without location

Both must be granted for scanning and ranging to work.

---

## App Flow

```
Launch
  └── Splash Screen (650 ms)
        └── Role Selection
              ├── Receiver  ──→  Receiver Dashboard
              │                       └── Publisher Details
              └── Publisher ──→  Publisher Dashboard
```

### Role Selection

The app checks whether the device supports Wi-Fi RTT (`FEATURE_WIFI_RTT` + `WifiRttManager.isAvailable`). The capability summary is shown on the role selection screen. The selected role is persisted in DataStore so the app remembers it across restarts.

---

## Receiver Mode

The Receiver is the device that **initiates** Wi-Fi RTT ranging to one or more Publishers.

### What it does

1. **Scan** — triggers a Wi-Fi scan and filters results to RTT-capable access points (IEEE 802.11mc responders). Each discovered AP becomes a Publisher entry in the list.
2. **Select** — tap the checkbox next to one or more Publishers to select them.
3. **Measure Selected** — runs one RTT ranging round against selected Publishers only.
4. **Measure All** — runs one RTT ranging round against all discovered Publishers.
5. **Stop** — cancels an in-progress scan or measurement session.
6. **Export CSV** — writes all measurements to a CSV file and opens the system share sheet.

### Dashboard

| Section | Description |
|---|---|
| Stats cards | Avg / Min / Max / Median / Std Dev distance, measurement rate (Hz), active publisher count, total measurements |
| Publisher list | All discovered RTT-capable APs with connection status, last measured distance, RSSI, and a checkbox for selection |
| Live graph | Canvas-drawn distance-over-time line chart, last 120 successful measurements |
| Measurement table | Last 50 individual ranging results with distance, std dev, RSSI, round number, and timestamp |
| Logs | In-app event log (scan started, measurement completed/failed, etc.) |

### Publisher Details

Tap any Publisher in the list to open its details screen:

- Publisher info: BSSID, status, connection state, last RSSI, last seen timestamp
- Per-publisher stats: Avg / Min / Max distance, success rate
- Distance history graph (last 60 measurements for this publisher)
- Full measurement table for this publisher (up to 100 entries)

### CSV Export format

```
Timestamp,Publisher ID,Publisher Name,Distance (m),RSSI (dBm),Std Dev (m),Status,Measurement Number
2025-08-20 06:30:01.123,aa:bb:cc:dd:ee:ff,MyRouter,2.31,-54,0.15,Success,1
```

The CSV is saved to the app's private `files/exports/` directory and shared via the system share sheet (Files, Drive, email, etc.).

---

## Publisher Mode

The Publisher is a device that **responds** to Wi-Fi RTT ranging requests from the Receiver.

> **Important:** Acting as a Wi-Fi RTT responder (SoftAP responder mode) requires hardware support. Most consumer Android devices support being ranged *to* as a standard Wi-Fi client, but acting as a dedicated IEEE 802.11mc responder requires specific chipset support. The Publisher screen tracks the device's responder capability via `RttCapabilityChecker`.

### Dashboard

| Section | Description |
|---|---|
| Device Information | Device name, Publisher ID (derived from Android ID), battery percentage |
| Status | Current status (Waiting / Busy / Offline), connection status, whether waiting for RTT requests, last measurement timestamp |
| Requests Received | Counter of RTT ranging requests received |
| Controls | Start Publishing / Stop Publishing / Refresh Status |
| Logs | In-app event log |

---

## Architecture

The app follows **Clean Architecture** with strict layer separation:

```
presentation/          ← Compose UI, ViewModels, Navigation
    ui/
        splash/
        role/
        receiver/
        publisher/
    components/        ← Shared composables (PermissionDialog, ScreenPlaceholder)
    navigation/        ← AppNavHost, AppRoute
    theme/             ← Material 3 colours, typography

domain/                ← Pure Kotlin, no Android framework imports
    model/             ← Data classes and enums
    repository/        ← Repository interfaces
    usecase/           ← One-responsibility use cases

data/                  ← Android framework implementations
    rtt/               ← AndroidRttManager, RttScanCoordinator, RttMeasurementEngine,
                          RttPublisherController, RttCapabilityChecker
    repository/        ← Repository implementations
    csv/               ← CsvManager, MeasurementCsvFormatter
    mapper/            ← RttResultMapper
    datastore/         ← DataStore keys

di/                    ← Hilt modules (DataModule, DispatcherModule, RepositoryModule)
utils/                 ← Dispatcher qualifiers (@IoDispatcher, @MainDispatcher)
```

### Key design decisions

- **StateFlow everywhere** — all state is immutable and observed with `collectAsStateWithLifecycle()`
- **Use cases are single-responsibility** — each use case does exactly one thing
- **Repository interfaces in domain** — the domain layer has zero Android imports, making it fully testable with fakes
- **Sequential ranging** — the Receiver measures Publishers one at a time per round, keeping timing deterministic and easy to analyse
- **Coroutine suspension for ranging** — `WifiRttManager.startRanging` is wrapped in `suspendCancellableCoroutine` so it integrates cleanly with structured concurrency
- **BroadcastReceiver safety** — the Wi-Fi scan receiver uses `RECEIVER_NOT_EXPORTED` on API 33+ and guards against double-unregister on cancellation

---

## Tech Stack

| Library | Version | Purpose |
|---|---|---|
| Kotlin | 2.2.21 | Language |
| Android Gradle Plugin | 8.13.2 | Build |
| Jetpack Compose BOM | 2026.06.00 | UI framework |
| Material Design 3 | (via BOM) | Design system |
| Navigation Compose | 2.9.8 | Screen navigation |
| Hilt | 2.58 | Dependency injection |
| Hilt Navigation Compose | 1.2.0 | ViewModel injection in Compose |
| Lifecycle / ViewModel | 2.9.2 | MVVM, `collectAsStateWithLifecycle` |
| DataStore Preferences | 1.2.1 | Role persistence |
| Coroutines + Flow | (via Kotlin) | Async, state streams |
| Android Wi-Fi RTT APIs | (platform) | `WifiRttManager`, `RangingRequest`, `RangingResult` |

---

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` | Required by Android for Wi-Fi scanning and RTT ranging |
| `ACCESS_COARSE_LOCATION` | Fallback location permission |
| `ACCESS_WIFI_STATE` | Read Wi-Fi scan results |
| `CHANGE_WIFI_STATE` | Trigger Wi-Fi scans |
| `NEARBY_WIFI_DEVICES` | Required on Android 13+ for Wi-Fi scanning without full location |

The `android.hardware.wifi.rtt` feature is declared as `required="true"` in the manifest, so the app will not appear on devices that don't support Wi-Fi RTT on the Play Store.

---

## Known Limitations

- **Emulator not supported** — Wi-Fi RTT requires real hardware
- `WifiManager.startScan()` is deprecated since API 28 but remains the only way to trigger a fresh scan on minSdk 28 without a background service
- `ScanResult.SSID` is deprecated on API 33+ (use `WifiSsid` instead) — the app uses it for display only and falls back to BSSID if SSID is blank
- Publisher responder mode requires chipset-level IEEE 802.11mc support — not all Android devices support acting as a responder

---

## Project Structure

```
wifi_rtt_measurement/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/wifirttmeasurement/
│       │   ├── MainActivity.kt
│       │   ├── WifiRttMeasurementApp.kt
│       │   ├── data/
│       │   ├── di/
│       │   ├── domain/
│       │   ├── presentation/
│       │   └── utils/
│       └── res/
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
└── PROJECT_BLUEPRINT.md
```

---

## License

This project is a research prototype. No license is applied — use freely for academic and personal projects.
