# Next Alarm for Smartspacer

A [Smartspacer](https://github.com/KieronQuinn/Smartspacer) add-on plugin that shows the
**next scheduled alarm from your preferred clock app(s)** as a Smartspace Target and Complication.

Addresses [KieronQuinn/Smartspacer#297](https://github.com/KieronQuinn/Smartspacer/issues/297) —
the built-in alarm complication cannot filter by app. This plugin solves that by using
**Shizuku** to read all pending alarms from specific clock apps and surface the earliest one.

---

## Features

- **Target & Complication** — shows the next alarm time with an alarm icon; tap to open the clock app
- **Per-app filtering** — pick one or more installed clock apps to monitor (e.g. Fossify Clock, Samsung Clock)
- **Shizuku-powered** — uses Shizuku to enumerate all pending alarms from selected apps via `dumpsys alarm`, enabling accurate filtering even when another app has an earlier reminder scheduled
- **Graceful fallback** — without Shizuku, falls back to `AlarmManager.getNextAlarmClock()` and filters by the creator package (works for most single-clock-app setups)
- **Reactive updates** — refreshes automatically when alarms change, device time changes, or on boot
- **12-hour display window** — only shows when an alarm is within 12 hours (configurable in source)

---

## Requirements

| Requirement | Details |
|---|---|
| **Android** | 10+ (minSdk 29) |
| **Smartspacer** | Any release that supports the Plugin SDK |
| **Shizuku** | Recommended for multi-app filtering; optional for single-app use |

---

## Setup

1. Install the APK on your device.
2. Open **Smartspacer → Targets / Complications** and add *Next Alarm*.
3. The **Select Clock Apps** screen will appear:
   - Grant Shizuku permission (tap the button) for accurate per-app filtering.
   - Check the clock app(s) you want to monitor.
   - Leave all unchecked to monitor every installed clock app.
4. Tap **Save**. The target/complication will appear as soon as an alarm is within 12 hours.

---

## How it works

### With Shizuku (recommended)

Shizuku spawns a privileged user service (`AlarmReaderService`) that runs `dumpsys alarm` as the
`shell` user. The output is parsed to find all `RTC_WAKEUP` alarm-clock entries
(`tag=*alarm*`) from the selected packages. The earliest future alarm is returned.

### Without Shizuku (fallback)

`AlarmManager.getNextAlarmClock()` is called. The returned `AlarmClockInfo.showIntent.creatorPackage`
is checked against the selected apps. Because this API only exposes the single globally-next alarm,
it may miss your preferred app's alarm if another app has an earlier one.

---

## Building

```bash
./gradlew :app:assembleRelease
```

The APK will be at `app/build/outputs/apk/release/app-release-unsigned.apk`.

---

## Architecture

```
app/src/main/kotlin/com/dakomi/smartspacer/alarms/
├── MainActivity.kt                  # Launcher activity (status display)
├── model/NextAlarm.kt               # Data class
├── data/
│   ├── Settings.kt                  # SharedPreferences wrapper
│   └── AlarmRepository.kt           # Shizuku + fallback alarm reading & parsing
├── service/AlarmReaderService.kt    # Shizuku UserService (runs as shell)
├── targets/NextAlarmTarget.kt       # SmartspacerTargetProvider
├── complications/NextAlarmComplication.kt  # SmartspacerComplicationProvider
├── ui/ClockAppPickerActivity.kt     # Setup / config UI
└── receiver/AlarmUpdateReceiver.kt  # Alarm-change broadcast receiver
```

---

## License

Apache 2.0 — see [LICENSE](LICENSE).