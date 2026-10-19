# Gold Tracker (Android)

Home-screen widget + app showing today's gold rate in ₹, with Day / Month / Year trend charts.

## Run it
1. Install Android Studio (Koala or newer) and open this folder (`GoldTracker`).
2. Let Gradle sync (it downloads Gradle 8.9 and the SDK pieces it needs).
3. Run on a phone/emulator (Android 8.0+).
4. Add the widget: long-press the home screen → Widgets → **Gold rate**.

## Features
- Live rate in ₹ with change vs. the start of the selected period (green/red).
- Day (last 24h, 15-min points), Month and Year (daily points) charts.
  Press and drag on the chart to read the price at any point.
- 24K / 22K and per 1 g / per 10 g switches.
- Widget: price, 24h change, sparkline, tap ↻ to refresh, tap anywhere to open the app.
  Refreshes about every 30 minutes in the background.
- Last data is cached, so the app and widget still show something offline.

## Data source & accuracy
Rates are computed from COMEX gold futures (USD/oz) × USD/INR, fetched from Yahoo Finance's
public chart endpoint (no API key). This is the *international* price converted to rupees.
Indian retail rates add import duty, GST and jeweller margin, so use **Local premium** in the
app to add a percentage until it matches your jeweller / bank rate.

Yahoo's endpoint is unofficial and can change or rate-limit. To use a different provider,
only `GoldRepository.fetch()` needs to change; it must return a list of
`Point(epochSeconds, usdPerOz, usdInr)`.

## Layout
- `GoldRepository.kt` — network + parsing
- `Settings.kt` — preferences, cache, price conversion, formatting
- `MainActivity.kt` — Compose UI and chart
- `GoldWidget.kt` — Glance widget, sparkline bitmap, refresh action
- `RefreshWorker.kt` — WorkManager periodic refresh
