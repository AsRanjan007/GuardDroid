<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="GuardDroid logo"/>
  <h1>GuardDroid</h1>
  <p><b>On-device Android malware scanner powered by a permission-based ML model.</b></p>
</div>

GuardDroid is a native Kotlin / Jetpack Compose Android app that continuously
scans installed and newly-added third-party apps and flags likely malware using
a lightweight **TensorFlow Lite** classifier trained on the
[NaticusDroid Android Permissions dataset](https://archive.ics.uci.edu/dataset/722/naticusdroid+android+permissions+dataset)
feature schema. All analysis runs **entirely on-device** — no permissions data
ever leaves the phone.

---

## How it works

Each installed app is reduced to a **binary vector of 86 requested permissions**
(the NaticusDroid feature schema). That `[1, 86]` vector is fed to a TFLite
neural network that outputs a single probability — how closely the app's
permission profile matches known malware. Apps above the **70% threshold** raise
an urgent notification and are surfaced on the dashboard.

```
PackageManager ──▶ PermissionExtractor ──▶ [1×86] vector ──▶ InferenceEngine (TFLite)
                                                                     │
                              ┌──────────────────────────────────────┘
                              ▼
                     malicious probability ──▶ RiskLevel ──▶ Dashboard + Notifications
```

## Architecture

| Layer | Component | Responsibility |
|-------|-----------|----------------|
| **Monitoring** | [`PackageInstallReceiver`](app/src/main/java/com/guarddroid/app/receiver/PackageInstallReceiver.kt) | `BroadcastReceiver` for `PACKAGE_ADDED` / `PACKAGE_REPLACED` / `PACKAGE_REMOVED`; scans new apps immediately. |
| | [`AppScanWorker`](app/src/main/java/com/guarddroid/app/worker/AppScanWorker.kt) | `WorkManager` `CoroutineWorker` — periodic full scans (every 6h), manual "Scan Now", and single-app scans. |
| **Features** | [`PermissionExtractor`](app/src/main/java/com/guarddroid/app/ml/PermissionExtractor.kt) | Queries `PackageManager` with `GET_PERMISSIONS`, filters out system apps, maps permissions to the `[1×86]` model vector. |
| | [`PermissionSchema`](app/src/main/java/com/guarddroid/app/ml/PermissionSchema.kt) | Auto-generated canonical list of the 86 permission features (model input order). |
| **Inference** | [`InferenceEngine`](app/src/main/java/com/guarddroid/app/ml/InferenceEngine.kt) | Loads `assets/naticus_droid_permission_model.tflite`, runs thread-safe inference. |
| | [`AppScanner`](app/src/main/java/com/guarddroid/app/ml/AppScanner.kt) | Orchestrates enumerate → extract → infer for one or all apps. |
| **State** | [`ScanRepository`](app/src/main/java/com/guarddroid/app/data/ScanRepository.kt) | Single source of truth; `StateFlow` + `SharedPreferences` persistence. |
| **Alerts** | [`NotificationHelper`](app/src/main/java/com/guarddroid/app/notification/NotificationHelper.kt) | Notification channels + urgent threat alerts that deep-link to details. |
| | [`ThreatAlertActivity`](app/src/main/java/com/guarddroid/app/alert/ThreatAlertActivity.kt) | Full-screen malware alarm (over the lock screen, alarm tone + vibration) launched from the background when a newly installed app is high-risk. |
| **UI** | [`DashboardScreen`](app/src/main/java/com/guarddroid/app/ui/DashboardScreen.kt) / [`ScanDetailScreen`](app/src/main/java/com/guarddroid/app/ui/ScanDetailScreen.kt) | Compose Material 3 dashboard (status, stats, app list, Scan Now) and per-app detail with contributing permissions. |

## The machine-learning model

The bundled model (`app/src/main/assets/naticus_droid_permission_model.tflite`)
is a **logistic-regression** classifier over the 86 permission flags:

```
Input [1×86] (binary permission flags)
  → Dense(1, sigmoid) → malicious probability [1×1]
```

A logistic model keeps the mapping **monotonic** — every dangerous permission
can only *increase* the score — so an app is flagged on the strength of its own
permission cluster (SMS abuse, device-admin, overlays, accessibility hijacking,
silent install, location+audio+contacts surveillance …) rather than the model
over-fitting a single cluster. It compiles to just `FULLY_CONNECTED` + `LOGISTIC`
for maximum on-device runtime compatibility.

It is produced by [`ml/train_naticus_model.py`](ml/train_naticus_model.py),
which also **auto-generates `PermissionSchema.kt`** so the on-device feature
order can never drift from the model's input order.

```bash
pip install numpy tensorflow-cpu

# Train on realistic synthesised NaticusDroid-style data (default):
python3 ml/train_naticus_model.py

# …or train on the real UCI dataset CSV:
python3 ml/train_naticus_model.py --csv path/to/NATICUSdroid.csv
```

> The synthesised distribution is derived from the well-documented NaticusDroid
> permission-risk profile (SMS/telephony abuse, device-admin, accessibility
> abuse, silent package install, screen overlays, etc.). Point `--csv` at the
> real dataset to reproduce the published model exactly. Sanity check on the
> shipped model: a benign profile (`INTERNET`, `VIBRATE`, …) scores ~0%, a
> classic SMS-trojan profile scores ~93%.

## Permissions requested by GuardDroid

| Permission | Why |
|------------|-----|
| `QUERY_ALL_PACKAGES` | Enumerate installed third-party apps to scan them. |
| `POST_NOTIFICATIONS` | Show urgent malware alerts (runtime-requested on Android 13+). |
| `USE_FULL_SCREEN_INTENT` | Raise the over-the-lock-screen malware alarm from the background. |
| `VIBRATE` | Vibrate during a high-risk alert. |
| `RECEIVE_BOOT_COMPLETED` | Keep periodic background scans reliable across reboots. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Back the expedited install-time scan. |

> **Note on Android 14+:** apps that aren't phone/alarm apps may have
> `USE_FULL_SCREEN_INTENT` withheld by default, in which case the alarm shows as
> a heads-up notification (still with sound). To get the full over-the-lock-screen
> popup, enable *Settings → Apps → GuardDroid → Full-screen intents*.

## Building

Requirements: Android Studio Ladybug+ / AGP 8.5, JDK 17, `minSdk 26`, `targetSdk 34`.

```bash
# Create local.properties with your SDK location, then:
./gradlew :app:assembleDebug        # build the APK
./gradlew :app:testDebugUnitTest    # run the JVM unit tests
```

Open the project in Android Studio and run the **app** configuration on a device
or emulator (API 26+).

## Project layout

```
GuardDroid/
├─ app/
│  ├─ src/main/
│  │  ├─ assets/naticus_droid_permission_model.tflite   # the classifier
│  │  ├─ java/com/guarddroid/app/
│  │  │  ├─ ml/        PermissionExtractor, InferenceEngine, AppScanner, PermissionSchema
│  │  │  ├─ receiver/  PackageInstallReceiver
│  │  │  ├─ worker/    AppScanWorker
│  │  │  ├─ notification/ NotificationHelper
│  │  │  ├─ data/      ScanRepository, AppScanResult, RiskLevel
│  │  │  ├─ ui/        DashboardScreen, ScanDetailScreen, ScanViewModel, theme/
│  │  │  ├─ GuardDroidApp.kt, MainActivity.kt
│  │  └─ res/          adaptive launcher icon (from the GuardDroid logo), themes
│  └─ src/test/        PermissionMappingTest
├─ ml/train_naticus_model.py   # trains the .tflite + generates PermissionSchema.kt
└─ sample-test-apps/           # 10 benign permission-profile test apps (see its README)
```

## Testing with the sample apps

The [`sample-test-apps/`](sample-test-apps/README.md) folder contains ten tiny
**benign** apps, each declaring a different malware-archetype permission profile
(SMS trojan, spyware, banking overlay, ransomware, premium dialer, adware,
device-admin bot, stalkerware, dropper) plus one genuinely benign flashlight app
as a false-positive control. Build all ten with:

```bash
./gradlew -p sample-test-apps :app:assembleDebug
```

Install a high-risk one to see the background install alarm, or install several
and tap **Scan Now** — the nine malicious profiles are flagged High and the
flashlight control stays Safe. See the folder's README for details.

## Disclaimer

GuardDroid is an educational demonstration of permission-based ML malware
triage. A permission profile is a heuristic signal, not a verdict — a high score
means "worth reviewing", not "definitely malicious". It is not a substitute for a
full commercial anti-malware engine.
