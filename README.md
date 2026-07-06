# PolyAlerts

A lightweight **Android companion app for Polymarket**. It is *not* a clone of the
exchange — it only:

1. **Reads public market prices** from Polymarket's open Gamma API (no login, no account).
2. Lets you set **price-threshold alerts** ("notify me when *Yes* on market X rises to ≥ 60¢").
3. Checks prices in the background and fires a **notification** when a threshold is crossed.
4. **Redirects you to polymarket.com** (in-app Chrome Custom Tab) to actually trade.

All the heavy Polymarket machinery — accounts, KYC, deposits/withdrawals, the order book —
stays on the real site. This app is just alerts + a doorway.

## How it works

```
Gamma API (gamma-api.polymarket.com/markets)  ──► browse + live prices
        │
   set alert (Room db, local)
        │
   WorkManager worker every 15 min ──► compare price to your target ──► 🔔 notification
                                                                          │
                                                          tap ──► Custom Tab ──► polymarket.com/event/{slug}
```

A rule fires once per crossing, then re-arms automatically when the price moves back.

## Project layout

```
app/src/main/java/com/polyalerts/
├─ PolyApp.kt              Application: create notif channel + schedule worker
├─ MainActivity.kt         single Activity, requests POST_NOTIFICATIONS, hosts Compose
├─ data/
│  ├─ api/                 Gamma API: Market model, GammaApi (Retrofit), Network
│  ├─ db/                  Room: AlertRule, AlertDao, AlertDb
│  └─ Repository.kt        single data entry point (remote markets + local rules)
├─ alerts/
│  ├─ Notifications.kt     notification channel + builder (tap → opens market)
│  ├─ AlertWorker.kt       fetches prices, evaluates rules, notifies
│  └─ AlertScheduler.kt    enqueues the 15-min periodic WorkManager job
└─ ui/
   ├─ AppNav.kt            bottom-nav: Markets / Alerts
   ├─ AppViewModel.kt      state + actions
   ├─ BrowseScreen.kt      top markets list + "Set alert" / "Open"
   ├─ SetAlertDialog.kt    pick outcome, ≥/≤, target cents
   ├─ WatchlistScreen.kt   manage saved alerts (toggle / delete / open)
   └─ OpenSite.kt          Chrome Custom Tab redirect
```

## Build & run

1. Open the `polyalerts/` folder in **Android Studio** (Ladybug or newer).
2. Let it generate the Gradle wrapper if prompted (or run `gradle wrapper`).
3. Run on a device/emulator with API 26+. Grant the notification permission when asked.
4. Markets tab → pick a market → **Set alert** → choose outcome, ≥/≤, and a cents target.

> Note: WorkManager's minimum periodic interval is **15 minutes**, so alerts are
> near-real-time, not instant. For truly live alerts you'd add a foreground service
> holding the public WebSocket — a later iteration.

## Roadmap ideas (v2+)

- Foreground-service WebSocket for instant alerts.
- More alert types: % move, volume spike, new-market keyword watch, resolution reminders.
- Search + categories on the browse screen.
- "Watchlist" of markets (separate from alerts) with at-a-glance price changes.
- FCM push (needs a tiny backend) so alerts work even if the device throttles WorkManager.

## Legal / fair use

This app only consumes Polymarket's public, unauthenticated data endpoints and deep-links
users to the official site to trade. It stores no credentials and handles no funds. Respect
Polymarket's Terms of Service and API rate limits; this is an unofficial companion tool.
