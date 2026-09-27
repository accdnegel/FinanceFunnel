# Pitaka Product Requirements

## 1. Product definition

Pitaka is an offline personal financial tracker. It tracks liquid funds, non-liquid allocations, income, expenses, transfers, budgets, and user-defined reporting categories without relying on market-value updates or recurring transactions.

## 2. Accounting model

### 2.1 Asset equation

```text
Total Assets = Liquid Pitaka balances + Savings balances + Investment balances
```

Moving funds from a Pitaka into a Savings or Investment Goal changes their classification from liquid to non-liquid but does not change total assets. Income increases total assets. Expenses decrease total assets. Transfers only move assets.

Example:

```text
Initial assets:       800,000
Savings allocations:  30,000
Investment allocations: 50,000
Liquid assets:        720,000
Non-liquid assets:     80,000
Total assets:         800,000
```

### 2.2 Monetary precision

- Display normal currency values with two decimal places.
- Preserve and accept up to six decimal places where required.
- Reject non-finite values.
- A Pitaka balance may never become negative.
- Monthly budgets and Expense Funnel remaining balances may become negative; their limits are warnings, not transaction blockers.

### 2.3 Historical values

- Current net worth uses current configured exchange rates.
- Historical monthly cash flow and expense reports use the conversion rate captured when each transaction was recorded.
- Original currency and amount remain available in transaction history.

## 3. Pitakas

### 3.1 Types

- A leaf Pitaka is a financial account and may hold balances in multiple currencies.
- Each leaf has one primary/display currency.
- A parent Pitaka is a derived folder whose balances are the per-currency sums of its children.
- Parent Pitakas cannot directly receive income, expenses, transfers, Goal contributions, Goal withdrawals, or manual adjustments. The user must select a leaf.

### 3.2 Hierarchy

Only two levels are allowed:

```text
Parent Pitaka
└── Child Pitaka
```

Rules:

- A child cannot have children.
- A Pitaka with children cannot become a child.
- A leaf can become a child of a root parent.
- A child can be promoted to a root Pitaka.
- Reparenting changes only aggregation; it does not create or destroy money.
- A parent may become a child only after all its children are promoted or reassigned.

### 3.3 First-child conversion

When a non-zero standalone Pitaka receives its first child:

- Every existing currency balance is moved into the first child.
- The child's explicitly entered starting balances are added to the inherited balances.
- The original Pitaka becomes a zero-balance parent container.
- The UI must explain and confirm this reallocation.

### 3.4 Opening balances

Starting balances create `OPENING_BALANCE` ledger events. They appear in history and net-worth reconstruction but do not count as income.

### 3.5 Transfers

Transfers are allowed between leaf Pitakas, whether they are roots or children.

Cross-currency transfers preserve:

- Source amount and currency
- Destination amount and currency
- Suggested destination amount from configured rates
- Any destination amount manually overridden by the user
- Effective and configured exchange-rate snapshots

Transfers are rejected with an explicit error when the selected source-currency balance is insufficient.

### 3.6 Deletion

Archive is the normal removal action and preserves balances and history. Permanent deletion remains available through an impact-preview confirmation that offers a backup export.

- Deleting a parent promotes all immediate children to root Pitakas, then deletes the empty parent.
- A parent with direct balances or direct transactions cannot be deleted until they are moved to a child.
- Deleting a leaf atomically reverses every related ledger effect, including transfer counterpart balances, Goal progress, and Expense Funnel spending; then deletes its ledger records and the leaf.
- The confirmation must describe all affected balances and records.

## 4. Income

An Income record contains:

- Name
- Date, defaulting to today
- Amount
- Currency
- Destination leaf Pitaka

Income increases the selected currency balance and total assets. Income has a dedicated creation screen accessible globally and may also be initiated from a Pitaka detail screen.

## 5. Goals

### 5.1 Goal types

Goals are classified as Savings or Investment. Both use the same contribution and withdrawal accounting. Investments track allocated capital only and do not model market gains or losses.

### 5.2 Multi-currency targets

A Goal has independent per-currency target and current-balance maps. For example:

```text
PHP target: 10,000
USD target: 10,000
```

A Goal is completed when every configured currency target is reached. Contributions may exceed targets and progress may exceed 100%.

When source and target currencies differ, the user chooses a Goal target currency and confirms the converted destination amount. Both source and destination values are persisted.

### 5.3 Lifecycle

- Target dates are optional and editable/removable.
- A newly completed Goal remains active and visible, marked `COMPLETED`.
- Completed Goals continue to accept deposits, partial withdrawals, or disposition decisions.
- The user may archive a Goal explicitly.
- Archived and completed Goals remain visible in historical views and reports.

### 5.4 Withdrawals

Users can withdraw part or all of a Goal into a selected leaf Pitaka. A withdrawal preserves Goal-side and Pitaka-side amounts/currencies. It decreases non-liquid assets and increases liquid assets without changing total assets.

### 5.5 Completed-Goal disposition

A completed Goal supports these decisions:

1. Keep it active as a completed Goal.
2. Withdraw some or all funds into leaf Pitakas, including crediting funds back according to their contribution origins where possible.
3. Mark the allocated funds as spent. This converts the selected Goal balance into an Expense, removes it from non-liquid assets, includes it in cash outflow/expense reporting, and archives the Goal when disposition is complete.

### 5.6 Deletion

Permanent Goal deletion reverses all contribution and withdrawal effects using their original amounts and currencies and returns remaining allocations to their original source Pitakas. If an original source no longer exists, deletion requires a user-selected leaf refund destination. The operation is atomic.

## 6. Expenses

### 6.1 Expense records

An Expense contains:

- Name
- Date, defaulting to today
- Amount and currency
- Optional free-text category
- Optional Expense Funnel
- Source leaf Pitaka

Blank categories become `Uncategorized`. Category matching is trimmed and case-insensitive. Existing category names are reused so matching records aggregate under one category. New text creates a reusable managed-list category.

Expenses are rejected with an explicit error when the selected Pitaka currency balance is insufficient.

### 6.2 Monthly budget

- A monthly limit includes every Expense in that calendar month.
- A configured limit carries into subsequent months until changed.
- Each historical month's effective limit remains stable once that month contains activity.
- Remaining budget may be negative.
- Historical months remain viewable.

### 6.3 Expense Funnels

A Funnel has:

- Unique name
- Amount limit
- Currency
- Inclusive validity start and end dates
- Default validity of the current calendar month

Funnel limits are independent from the monthly budget. Overspending is allowed and represented by a negative remaining amount.

### 6.4 General Expenses

`General Expenses` is a permanent, unlimited system Funnel. Expenses without a selected Funnel are assigned to it. Its entries remain fully editable, including category and reassignment to another eligible Funnel.

`Uncategorized` is the category fallback; `General Expenses` is the Funnel fallback.

### 6.5 Funnel deletion

Deleting a user Funnel keeps every Expense and atomically reassigns it to General Expenses while preserving all other details. Those records can subsequently be edited, recategorized, and reassigned.

## 7. Record editing and deletion

Transaction type is immutable. Changing a record from one type to another requires deletion and recreation.

Editable details by type:

- Income: name, date, amount, currency, destination leaf Pitaka
- Expense: name, date, amount, currency, category, Funnel, source leaf Pitaka, Funnel allocation
- Transfer: name, date, source Pitaka/currency/amount, destination Pitaka/currency/amount
- Goal contribution: name, date, source Pitaka/currency/amount, Goal, Goal currency/amount
- Goal withdrawal: name, date, Goal/currency/amount, destination Pitaka/currency/amount

Every edit atomically reverses the old event and applies the fully validated replacement. Every record deletion atomically reverses all of its effects.

Manual balance adjustments remain supported and always target one explicit currency.

## 8. Dashboard

### 8.1 Asset summary

Display:

- Total assets
- Liquid assets
- Non-liquid assets
- Savings balance
- Investment balance

Only Expenses reduce assets. Goal contributions and withdrawals reclassify assets.

### 8.2 Cash flow

Display side-by-side monthly bars for:

- Inflow: Income
- Outflow: Expenses, including Goal funds explicitly converted to expenses

Transfers, Goal contributions, and Goal withdrawals do not count as cash flow.

### 8.3 Expense reporting

For the selected month, display:

- Monthly budget limit, spent, remaining, percentage, and over-budget amount
- Expense distribution by category
- Expense distribution by Funnel, including General Expenses

Pie-chart segments open their filtered transaction histories.

## 9. Detail screens

### 9.1 Pitaka details

- Leaf: own per-currency balances and directly associated transaction history.
- Parent: aggregate child balances and combined histories across immediate children.
- Selecting a child displays only that child's history.

### 9.2 Goal details

Savings and Investment use separate tabs. Cards and details show name, per-currency current/target values, optional target date, completion status, and complete transaction history.

### 9.3 Expense details

The Spending tab shows the current monthly budget first, followed by Expense Funnels. Funnel details show limit, spent, remaining, validity, and all assigned Expenses.

## 10. Naming and data management

- Duplicate Pitaka, Goal, and Expense Funnel names are not allowed after trimming and case normalization.
- Categories with equivalent normalized names are merged logically.
- Archived entities remain in historical reports but are excluded from new-transaction selectors.
- CSV export includes active and archived records.
- Full backup and restore are required features. Permanent-delete confirmations offer backup export before allowing a separately confirmed destructive action.

## 11. Excluded functionality

- Recurring transactions are not part of the product and must be removed safely through a schema migration.
- Investment market-value gains and losses are not tracked.
- Transfer fees are not modeled.
- Hierarchies deeper than parent and child are not supported.
