# GuardDroid — sample test apps

Ten tiny **benign** Android apps used to exercise GuardDroid's malware
detection. Each app is identical except for the **permission profile** it
declares, and each profile mirrors a real-world malware archetype so
GuardDroid's permission-based classifier has something realistic to score.

> ⚠️ **These apps are not malware.** They contain no payload and take no harmful
> action — they only list permissions in their manifest. That is exactly what a
> permission-based detector consumes, so it's all that's needed to validate
> GuardDroid end-to-end. (Real malware is neither created nor distributed here.)

## The 10 fixtures

| Flavor | App label | Package | Expected result |
|--------|-----------|---------|-----------------|
| `smsTrojan` | TEST SMS Trojan | `com.guarddroid.samples.smstrojan` | High (~100%) |
| `spyware` | TEST Spyware | `com.guarddroid.samples.spyware` | High (~100%) |
| `bankingOverlay` | TEST Banking Overlay | `com.guarddroid.samples.bankingoverlay` | High (~100%) |
| `ransomware` | TEST Ransomware | `com.guarddroid.samples.ransomware` | High (~90%) |
| `premiumDialer` | TEST Premium Dialer | `com.guarddroid.samples.premiumdialer` | High (~100%) |
| `adware` | TEST Adware | `com.guarddroid.samples.adware` | High (~84%) |
| `deviceAdminBot` | TEST Device-Admin Bot | `com.guarddroid.samples.deviceadminbot` | High (~99%) |
| `stalkerware` | TEST Stalkerware | `com.guarddroid.samples.stalkerware` | High (~99%) |
| `dropper` | TEST Dropper | `com.guarddroid.samples.dropper` | High (~100%) |
| `benignFlashlight` | TEST Benign Flashlight | `com.guarddroid.samples.benignflashlight` | **Safe (~1%)** — control / false-positive check |

Each is a separate package, so all ten can be installed side by side.

## Build the 10 APKs

From the **repository root** (reuses the main project's Gradle wrapper):

```bash
./gradlew -p sample-test-apps :app:assembleDebug
```

The APKs land in:

```
sample-test-apps/app/build/outputs/apk/<flavor>/debug/app-<flavor>-debug.apk
```

e.g. `app-smsTrojan-debug.apk`, `app-benignFlashlight-debug.apk`, …

You can also open `sample-test-apps/` as its own project in Android Studio and
pick a build variant.

## Test GuardDroid with them

1. Install and launch **GuardDroid**; grant the notification permission.
2. **Install-time alert:** install any high-risk fixture (e.g. the SMS Trojan)
   while GuardDroid is in the background —
   ```bash
   adb install sample-test-apps/app/build/outputs/apk/smsTrojan/debug/app-smsTrojan-debug.apk
   ```
   GuardDroid should raise the loud full-screen alarm + notification showing the
   package name, criticality and malicious %.
3. **Manual scan:** install several fixtures, open GuardDroid and tap
   **Scan Now** — every fixture should appear ranked by risk, the nine malware
   archetypes flagged High and `benignFlashlight` shown as Safe. Tap any app for
   the contributing permissions.

## Why these profiles score the way they do

GuardDroid's model is monotonic in the permissions it tracks, so a cluster of
dangerous permissions (SMS/telephony abuse, device-admin, screen overlays,
accessibility hijacking, silent package install, location+audio+contacts
surveillance) pushes the malicious probability up, while a lightweight app that
only wants Internet/vibrate/flashlight stays near zero — which is exactly what
the `benignFlashlight` control verifies.
