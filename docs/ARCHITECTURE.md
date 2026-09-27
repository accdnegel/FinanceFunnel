# Pitaka / FinanceFunnel — How the Application Works

## 1. Product model

Pitaka is an offline-first personal finance tracker. The application models money through four primary concepts:

1. **Pitakas** — sources/pools where money is held.
2. **Goals** — savings or investment targets.
3. **Expense Funnels** — spending envelopes with limits and optional validity dates.
4. **Ledger Entries** — permanent financial events that explain money movement.

The application also provides:
- monthly expense limits;
- exchange-rate settings;
- multi-currency balances;
- transaction masking;
- Batik/solid card designs;
- CSV export;
- database backup and restore;
- monthly and category-based statistics.

## 2. Application architecture

The application follows a Kotlin + Jetpack Compose + Room architecture:

```
Compose Screens
      |
      v
PitakaViewModel
      |
      v
PitakaRepository
      |
      +--> PitakaDao
      +--> LedgerDao
      +--> GoalDao
      +--> ExpenseFunnelDao
      +--> CurrencyDao
      +--> MonthlyBudgetDao
      |
      v
Room / SQLite
```

### UI layer
Compose screens display state and collect user input.

### ViewModel
`PitakaViewModel` exposes Kotlin `Flow` values and launches repository operations in `viewModelScope`.

### Repository
`PitakaRepository` is currently the central domain/data coordinator. It performs Room transactions and applies financial effects.

### Room
Room persists all financial data in the local `pitaka.db` SQLite database.

## 3. Database entities

### Pitaka

A Pitaka represents a money-holding pool.

Important fields:
- `id`
- `name`
- `currentAmount`
- `currency`
- `currencyBalances`
- `parentPitakaId`
- `colorHex`
- `cardStyle`

A Pitaka can be a leaf or a parent.

### Goal

A Goal represents either:
- SAVINGS
- INVESTMENT

Important fields:
- `targetAmount`
- `currency`
- `targetBalances`
- `currencyBalances`
- `targetDate`

### ExpenseFunnel

A funnel represents a spending envelope.

Important fields:
- `name`
- `limit`
- `currency`
- `currencyBalances`
- `validFrom`
- `validUntil`
- `isSystem`
- `cardStyle`

The system provides a `General Expenses` funnel.

### LedgerEntry

Ledger entries represent financial events:

| Type | Meaning | Balance effect |
|---|---|---|
| INCOME | Money enters a Pitaka | Pitaka + |
| EXPENSE | Money leaves a Pitaka | Pitaka - |
| TRANSFER | Money moves between Pitakas | Source -, destination + |
| GOAL_CONTRIBUTION | Money moves from Pitaka to Goal | Pitaka -, Goal + |
| GOAL_WITHDRAWAL | Money moves from Goal to Pitaka | Goal -, Pitaka + |
| GOAL_EXPENSE | Money leaves a Goal and is recorded as spending | Goal -, Funnel + |
| OPENING_BALANCE | Initial funds are recorded when a Pitaka is created | Pitaka + |
| ADJUSTMENT | Manual reconciliation | Pitaka +/- |

The ledger is intended to be the audit trail for financial changes.

## 4. Pitaka hierarchy

Pitakas can contain other Pitakas.

Example:

```
Bank Accounts
├── Payroll
├── Savings
└── USD Account
```

A leaf Pitaka displays its own currency balances.

A parent Pitaka displays the recursive sum of its descendants.

For example:

```
Bank Accounts
├── Payroll: PHP 30,000
├── Savings: PHP 20,000
└── USD Account: USD 500
```

The parent effective balances are:

```
PHP 50,000
USD 500
```

A parent does not independently own money once it has children.

### First-child conversion

When an existing standalone Pitaka receives its first child, the implementation transfers the parent's existing stored balances into the new child and clears the parent's own balance. This preserves the existing money while converting the Pitaka into a container.

This behavior should be confirmed explicitly in the UI because it is a financial-state mutation.

## 5. Currency model

The application supports multiple currencies.

A balance map is serialized in the database using a compact representation such as:

```
PHP=15000|USD=250
```

The `CurrencyBalances` utility:
- parses stored balances;
- normalizes currency codes to uppercase;
- adds deltas;
- encodes balances back to storage;
- provides display lines.

Each ledger entry also stores its transaction currency.

### Base currency

Currency settings define a base/display currency.

The ViewModel converts values using stored exchange rates:

```
converted = amount × fromRateToBase ÷ toRateToBase
```

No online exchange-rate service is required.

## 6. Income

When income is recorded:

1. The source Pitaka is identified.
2. The Pitaka's currency is used.
3. An INCOME ledger entry is created.
4. The ledger effect increases that currency balance.

Conceptually:

```
Salary → Payroll Pitaka
Payroll + PHP 50,000
```

## 7. Expenses

When an expense is recorded:

1. A source Pitaka is selected.
2. An expense category is normalized.
3. A funnel is selected or the system General Expenses funnel is used.
4. The transaction is stored with transaction and funnel currency/amount information.
5. The Pitaka balance is reduced.
6. The funnel's applied amount is increased.

Conceptually:

```
Transportation expense
        |
        +--> Bank Pitaka - PHP 500
        |
        +--> Transportation Funnel + PHP 500 spent
```

The funnel therefore tracks spending separately from the actual source of funds.

## 8. Expense funnels

A funnel has:
- a spending limit;
- optional start/end validity dates;
- its own currency balance;
- a visual card style.

Example:

```
Transportation
Limit: PHP 10,000
Valid: Sep 15 — Oct 15
Spent: PHP 3,500
Remaining: PHP 6,500
```

The system assigns expenses without a user funnel to General Expenses.

## 9. Goals

Goals can be SAVINGS or INVESTMENT goals.

A contribution creates a GOAL_CONTRIBUTION ledger event:

```
Payroll Pitaka - PHP 5,000
Savings Goal + PHP 5,000
```

The Home screen converts savings/investment progress into the selected base currency when calculating totals.

## 10. Transfers

Transfers move money between Pitakas.

Same-currency example:

```
Payroll - PHP 20,000
Savings + PHP 20,000
```

Cross-currency transfers use the stored exchange rates to calculate a destination amount.

Example:

```
USD account - USD 100
PHP account + approximately PHP 5,800
```

The exact amount depends on the configured exchange rates.

## 11. Net worth

The dashboard calculates three broad components:

```
Liquid assets
+ Savings goal progress
+ Investment goal progress
= Displayed net worth
```

Transfers and goal contributions do not inherently increase net worth because they move value within the application's tracked assets.

Expenses reduce net worth and income increases it.

## 12. Monthly budgets

Monthly budgets use a `yyyy-MM` key.

If a month does not have an explicit budget, the most recent earlier budget is carried forward.

Example:

```
January: PHP 20,000
February: no row
March: no row
```

February and March therefore inherit January's limit until a later explicit budget is created.

## 13. Transaction editing and deletion

Ledger entries can be deleted.

The intended accounting mechanism is:

```
Original effect
      ↓
reverseEffect()
      ↓
delete ledger row
```

When editing, the implementation performs this atomically:

```
reverse old effect
      ↓
update entry
      ↓
apply new effect
```

This design is important because balances are derived from ledger activity.

Linked Pitaka, Goal, Funnel, currency, conversion snapshot, and allocation fields are validated as one complete replacement event.

## 14. Manual balance adjustment

A Pitaka can be reconciled against a real-world balance.

The application creates an ADJUSTMENT ledger entry rather than silently overwriting the balance.

For example:

```
Recorded balance: PHP 9,850
Actual balance:   PHP 10,000
Difference:       +PHP 150
```

An adjustment of +PHP 150 is recorded.

Adjustments are recorded against an explicit currency balance.

## 15. Data persistence and offline behavior

The database is local Room/SQLite storage.

The application does not require a network connection for:
- recording transactions;
- calculating balances;
- managing Pitakas;
- managing goals;
- managing funnels;
- configured currency conversions.

Exchange rates are manually configured and stored locally.

## 16. Database migrations

The current database version is 12.

Migration 4 → 5 introduced:
- Pitaka hierarchy;
- card styles;
- system funnel;
- related indexes.

Migration 5 → 6 introduced:
- funnel amount/currency fields;
- goal amount/currency fields;
- historical backfills for existing expense and contribution rows.

Migrations 6 → 11 introduced transfer currencies, archival timestamps, and historical base-currency snapshots. Migration 11 → 12 adds multi-currency Goal targets, makes Goal dates optional, renames the system Funnel to General Expenses, and removes recurring rules.

Future schema changes must have explicit migrations so existing financial records survive application updates.

## 17. Visual system

Pitakas and Goals support:
- solid-color cards;
- Batik template cards;
- accent colors;
- previews during creation/editing.

The card style is stored as an identifier, allowing UI rendering to remain separate from financial data.

## 18. Navigation

The application contains screens for:
- Home;
- Pitakas;
- Pitaka details;
- Goals;
- Goal details;
- Expenses;
- expense categories;
- expense funnels;
- funnel details;
- transfers;
- currency settings;
- monthly budget history;
- creation/edit dialogs.

Navigation is defined centrally in the navigation graph.

## 19. Security/privacy model

The application is designed primarily as a local finance tracker. Financial records are stored in the application's local database.

The amount-masking controls hide displayed values in the UI; masking is a presentation feature, not encryption of the stored database.

## 20. Important accounting invariant

The intended invariant is:

> Every financial balance change must have exactly one corresponding, reversible financial event.

For every ledger event:

```
apply(entry)
then reverse(entry)
```

should return every affected balance to its previous state.

This invariant should become the central automated test principle for future development.

## 21. Development priorities

Recommended follow-up engineering work:

1. Add Room integration coverage for migrations and atomic accounting mutations.
2. Replace floating-point monetary storage with a fixed-precision representation.
3. Extract the accounting engine from the repository as its rules continue to grow.
4. Keep product and architecture documentation synchronized with financial behavior.

## 22. Repository map

```
app/src/main/java/com/pitaka/app/
├── MainActivity.kt
├── data/
│   ├── AppDatabase.kt
│   ├── Pitaka.kt
│   ├── PitakaRepository.kt
│   ├── PitakaDao.kt
│   ├── LedgerEntry.kt
│   ├── LedgerDao.kt
│   ├── Goal.kt
│   ├── GoalDao.kt
│   ├── ExpenseFunnel.kt
│   ├── ExpenseFunnelDao.kt
│   ├── Currency.kt
│   ├── CurrencyBalances.kt
│   ├── CurrencyDao.kt
│   ├── MonthlyBudget.kt
│   ├── MonthlyBudgetDao.kt
│   └── Converters.kt
├── navigation/
│   └── NavGraph.kt
├── ui/
│   ├── PitakaViewModel.kt
│   ├── components/
│   ├── screens/
│   └── theme/
└── util/
    ├── CsvExport.kt
      ├── DatabaseBackup.kt
    └── StringSimilarity.kt
```
