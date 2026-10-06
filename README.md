# CyberPulse

Android app that collects the latest cybersecurity news, sorts it into categories, and tracks newly
published vulnerabilities against the system types you care about.

## Features

- **News**: articles from The Hacker News, BleepingComputer, Krebs on Security, SecurityWeek,
  Dark Reading, The Record and CISA advisories, sorted into categories (Vulnerabilities, Malware &
  Ransomware, Breaches & Incidents, Threat Actors & Campaigns, Policy & Law Enforcement,
  Research & Tools, Industry, General).
- **Vulnerabilities**: CVEs published to NVD in the last 3 days, recent CISA KEV (actively exploited)
  additions, plus vulnerability news. Filter by your systems, exploited-only, severity and source.
- **My Systems**: set each system type (Windows, macOS, Linux, Android, iOS, browsers, network devices,
  cloud, virtualization, web apps, databases, enterprise software, dev tools/supply chain, ICS/OT,
  IoT, AI/ML) to **Flag**, **Default** or **Suppress**. You can also tap a system tag on any card.
- **Alerts**: background refresh about every 2 hours (WorkManager). Notifies for new vulnerabilities
  on flagged systems (or everything except suppressed), above a minimum severity. Actively exploited
  CVEs always alert.

### Look

Mr. Robot / fsociety-inspired: always-dark black terminal, signal red, monospace type, sharp corners,
faint CRT scanlines, a glitching shell-prompt title (`ui/components/Terminal.kt`, `ui/theme/Theme.kt`).

### Flag / suppress rules (`domain/VulnPolicy.kt`)

- Flagged if *any* affected system type is flagged.
- Suppressed (hidden from the vulnerability feed, no alerts) only if *every* affected system type is
  suppressed, so a Chrome-on-Windows bug still shows if you suppress Windows but not browsers.
- Flagged beats suppressed.

## Build

Requires the Android SDK (compileSdk 37) and a JDK 17+ (Android Studio's bundled JBR works).

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Or open the folder in Android Studio and run the `app` configuration.

## Layout

- `domain/`: system types, categories, keyword classifier, flag/suppress policy (pure Kotlin, unit tested)
- `data/remote/`: RSS/Atom parser, NVD CVE API 2.0 and CISA KEV clients
- `data/`: Room database, repository (fetch, merge, dedupe, 30-day retention), settings
- `work/`: periodic refresh worker and notifications
- `ui/`: Jetpack Compose screens (News, Vulnerabilities, My Systems)
