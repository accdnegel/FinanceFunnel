# Pitaka / FinanceFunnel — How the Application Works

## 1. Product model

Pitaka is an offline-first personal finance tracker. The application models money through four primary concepts:

1. **Pitakas** — sources/pools where money is held.
2. **Goals** — savings or investment targets.
3. **Expense Funnels** — spending envelopes with limits and optional validity dates.
4. **Ledger Entries** — permanent financial events that explain money movement.

The application also provides:
- monthly expense limits;
- recurring income/expense rules;
- exchange-rate settings;
- multi-currency balances;
- transaction masking;
- Batik/solid card designs;
- CSV export;
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
      +--> RecurringRuleDao
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

The system provides an `Unclassified Expense` funnel.

### LedgerEntry

Ledger entries represent financial events:

| Type | Meaning | Balance effect |
|---|---|---|
| INCOME | Money enters a Pitaka | Pitaka + |
| EXPENSE | Money leaves a Pitaka | Pitaka - |
| TRANSFER | Money moves between Pitakas | Source -, destination + |
| GOAL_CONTRIBUTION | Money moves from Pitaka to Goal | Pitaka -, Goal + |
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
3. A funnel is selected or the system Unclassified funnel is used.
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

The system can also assign expenses without a user funnel to Unclassified Expense.

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

## 13. Recurring transactions

Recurring rules support monthly income and expense transactions.

A rule contains:
- type;
- name;
- amount;
- category;
- Pitaka;
- day of month;
- active state;
- last applied month.

There is no background scheduler.

Instead, the application checks rules when it opens.

If today is on or after the configured day and the rule has not been applied in the current month, the transaction is posted.

This provides offline catch-up behavior.

## 14. Transaction editing and deletion

Ledger entries can be deleted.

The intended accounting mechanism is:

```
Original effect
      ↓
reverseEffect()
      ↓
delete ledger row
```

When editing, the implementation attempts:

```
reverse old effect
      ↓
update entry
      ↓
apply new effect
```

This design is important because balances are derived from ledger activity.

However, the current implementation has known defects around stale funnel/goal allocation fields and deletion of linked entities. See `docs/CODEBASE_AUDIT.md`.

## 15. Manual balance adjustment

A Pitaka can be reconciled against a real-world balance.

The application creates an ADJUSTMENT ledger entry rather than silently overwriting the balance.

For example:

```
Recorded balance: PHP 9,850
Actual balance:   PHP 10,000
Difference:       +PHP 150
```

An adjustment of +PHP 150 is recorded.

Multi-currency adjustment behavior requires hardening before it should be considered fully safe.

## 16. Data persistence and offline behavior

The database is local Room/SQLite storage.

The application does not require a network connection for:
- recording transactions;
- calculating balances;
- managing Pitakas;
- managing goals;
- managing funnels;
- recurring catch-up;
- configured currency conversions.

Exchange rates are manually configured and stored locally.

## 17. Database migrations

The current database version is 6.

Migration 4 → 5 introduced:
- Pitaka hierarchy;
- card styles;
- system funnel;
- related indexes.

Migration 5 → 6 introduced:
- funnel amount/currency fields;
- goal amount/currency fields;
- historical backfills for existing expense and contribution rows.

Future schema changes must have explicit migrations so existing financial records survive application updates.

## 18. Visual system

Pitakas and Goals support:
- solid-color cards;
- Batik template cards;
- accent colors;
- previews during creation/editing.

The card style is stored as an identifier, allowing UI rendering to remain separate from financial data.

## 19. Navigation

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
- recurring rules;
- currency settings;
- monthly budget history;
- creation/edit dialogs.

Navigation is defined centrally in the navigation graph.

## 20. Security/privacy model

The application is designed primarily as a local finance tracker. Financial records are stored in the application's local database.

The amount-masking controls hide displayed values in the UI; masking is a presentation feature, not encryption of the stored database.

## 21. Important accounting invariant

The intended invariant is:

> Every financial balance change must have exactly one corresponding, reversible financial event.

For every ledger event:

```
apply(entry)
then reverse(entry)
```

should return every affected balance to its previous state.

This invariant should become the central automated test principle for future development.

## 22. Development priorities

Before adding major new features, the recommended order is:

1. Fix destructive Pitaka/Funnel/Goal deletion behavior.
2. Make ledger edits update every affected derived balance.
3. Make every transaction explicitly currency-aware.
4. Replace floating-point monetary storage.
5. Add accounting regression tests.
6. Harden Room migrations.
7. Extract the accounting engine from the repository.
8. Improve UI error/result handling.
9. Update documentation whenever financial behavior changes.

## 23. Repository map

```
app/src/main/java/com/pitaka/app/
├── MainActivity.kt
├── data/
│   ├── AppDatabase.kt
│   ├── Pitaka.kt
│   ├── PitakaHierarchy.kt
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
│   ├── RecurringRule.kt
│   ├── RecurringRuleDao.kt
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
    └── StringSimilarity.kt
```
