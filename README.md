# Pitaka

A fully offline personal finance tracker for Android — Kotlin + Jetpack Compose + Room.
"Pitaka" is Filipino for "wallet." Everything lives on-device (SQLite via Room); nothing
needs an internet connection to work.

## Core model

- **Pitakas** — your real fund sources (bank accounts, cash, e-wallets). Each has a live
  balance and a "last updated" timestamp. You can transfer money between them.
- **Goals** — Savings or Investment targets ("funnels"/"piggy banks"). Contributing to a
  Goal pulls money out of a Pitaka, just like an expense would.
- **Expenses** — categorized spending, always charged to a specific Pitaka, checked against
  a Monthly Max Expense limit you set.

**Key accounting rule:** Income and Expenses are the only two things that change your total
net worth. Transfers between Pitakas and contributions to Goals just move money around —
they don't create or destroy it. Investment-goal contributions are explicitly tracked as
non-liquid assets and don't count against your monthly expense limit.

## Building the APK

Same as before — no Android SDK is required on your end if you use GitHub Actions:

1. Push this project to a GitHub repo.
2. The included `.github/workflows/build-apk.yml` builds automatically on push.
3. Download the APK from the run's **Artifacts** tab.

Or locally with Android Studio: open the folder, let Gradle sync, then **Build → Build APK(s)**.

## Branding

The app icon and in-app logo (`res/drawable/pitaka_logo.png`) are generated from your
uploaded wallet artwork. The color theme (orange/red/yellow primary, blue/green accents)
is pulled from the same palette.

## Notable design decisions / assumptions made

- **Monthly Max Expense** is a single ongoing setting (not configured separately per month).
  Update it any time from the Expenses tab.
- **Savings goal contributions** reduce liquid Pitaka balances the same way Investment
  contributions do — both are reallocations. The Home dashboard breaks Net Worth into
  Liquid / Savings / Investments so you can see all three at a glance.
- Deleting a ledger entry (income, expense, transfer, or contribution) reverses its effect
  on the relevant Pitaka balance(s) automatically.
- Database schema is pre-release (v1); no migrations have been needed yet.


## Expense funnels

Expense funnels are named spending allowances with an independent limit and optional start/end validity dates. Expenses can be assigned to a funnel while still being charged to a Pitaka; the Pitaka balance decreases normally, and the funnel's remaining balance is calculated from its linked expense entries.

## Visual identity

The UI uses the Pitaka logo palette and Batik-inspired card color presets. The font slot can be added at `app/src/main/res/font/batangas.ttf` when the licensed Batangas font file is available.


## Development completion notes

- PHP is the default currency.
- Pitakas, goals, and expense funnels retain per-currency balances in a serialized balance map.
- Ledger entries retain their transaction currency.
- Cross-currency transfers use the destination currency for the destination balance.
- Amount masking is available in transaction histories.
- Recurring rules are applied on app launch when due.
- The database currently uses destructive migration fallback; replace this with explicit migrations before production release.
- Exchange rates are manually maintained and offline.
