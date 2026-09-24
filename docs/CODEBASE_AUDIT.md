# Pitaka / FinanceFunnel — Codebase Audit
## Branch audited
- Branch: `feature/technical-debt-accounting-hardening`
- Audit date: 2026-09-24
- Scope: application architecture, persistence, accounting logic, multi-currency handling, hierarchy, goals, expense funnels, recurring rules, budgets, UI validation, migrations, and build configuration.

## Executive summary
The branch has a coherent offline-first architecture and a ledger-centered accounting model, but several financial-integrity defects remain. The most serious defects involve deletion and editing of ledger-linked objects, because the application stores derived balances on Pitakas/Goals/Funnels while also allowing ledger rows to be deleted or changed without consistently reversing/recomputing every affected derived balance.

A unit-test foundation has now been added under `app/src/test`; comprehensive accounting integration coverage is still incomplete.

## Severity definitions
- P0 — can corrupt financial state or produce materially incorrect balances/history.
- P1 — important incorrect behavior, data inconsistency, or unsafe accounting edge case.
- P2 — functional defect/UX problem with limited accounting impact.
- P3 — maintainability/documentation/quality issue.

## Findings

### P0-01 — Deleting a Pitaka can leave other Pitakas with incorrect balances
**Location:** `PitakaRepository.deletePitakaCascade()`

The method deletes all ledger rows touching the Pitaka, then deletes the Pitaka. It does not call `reverseEffect()` before deleting those rows.

For a transfer:
1. Pitaka A sends ₱1,000 to Pitaka B.
2. A = -₱1,000 and B = +₱1,000.
3. Delete Pitaka A.
4. The transfer row is deleted.
5. Pitaka B remains +₱1,000 even though the event that created that balance no longer exists.

The same class of problem affects expenses and goal contributions:
- deleting a Pitaka can remove an expense ledger row without reversing its funnel allocation;
- deleting a Pitaka can remove a goal contribution row without reversing goal progress.

**Required fix:** either prohibit deletion when the Pitaka has financial history, or reverse every affected ledger effect inside the same Room transaction before deleting history. Prefer retaining historical ledger records and marking the Pitaka inactive/archived.

### P0-02 — Editing an expense amount does not update its funnel allocation
**Location:** `PitakaRepository.updateEntry()`

The method reverses the old entry and applies the new entry, but only replaces `name`, `amount`, `category`, and `pitakaId`.

It does not recompute `funnelAmount` / `funnelCurrency`.

Example:
- Original expense: ₱100; funnel allocation ₱100.
- Edit expense to ₱200.
- Old effect reverses ₱100 from Pitaka and ₱100 from funnel.
- New effect adds ₱200 to Pitaka deduction but re-adds only the old stored funnel amount of ₱100.

Result: Pitaka changes by ₱100 more, while funnel spending remains unchanged.

**Required fix:** expense edits must explicitly define the source transaction amount/currency and the funnel-applied amount/currency, then reverse old values and apply the new values.

### P0-03 — Goal progress can become inconsistent when a goal contribution is edited
The same generic `updateEntry()` mechanism can modify a `GOAL_CONTRIBUTION` amount while retaining `goalAmount` and `goalCurrency`.

That creates the same mismatch as P0-02:
- source Pitaka effect follows the new `amount`;
- goal effect follows the stale `goalAmount`.

**Required fix:** either make goal contributions immutable or update all contribution-side fields atomically.

### P0-04 — Manual balance adjustment is not multi-currency safe
**Location:** `adjustPitakaBalanceManually()`

The adjustment calculates:
`delta = newBalance - pitaka.currentAmount`

and creates an ADJUSTMENT without an explicit currency. The default adjustment currency is therefore PHP.

For a USD Pitaka, `currentAmount` represents its primary currency, but the adjustment entry has no currency supplied. `adjustBalance()` only changes `currentAmount` when the entry currency equals the Pitaka's primary currency.

**Required fix:** adjustment must accept an explicit currency and calculate the delta against `currencyBalances[currency]`, not only `currentAmount`.

### P0-05 — Expense recording can silently use PHP for a non-PHP Pitaka
**Location:** `recordExpense()`

The repository passes `currency ?: "PHP"` into `recordExpenseInternal()`.

If a caller omits currency for a USD Pitaka, the transaction is recorded as PHP instead of inheriting the Pitaka's currency. This is inconsistent with income handling, which derives the currency from the Pitaka.

**Required fix:** resolve currency once using:
1. explicitly selected transaction currency;
2. otherwise source Pitaka currency;
3. otherwise PHP.

Do not default directly to PHP when a source Pitaka exists.

### P1-01 — Transfer accounting is under-specified
Transfers store:
- `amount`
- `currency`
- optional `secondaryAmount`

The destination currency is inferred from the destination Pitaka at application time.

This works for the current UI, but the ledger does not persist an explicit destination currency. If a Pitaka's primary currency is edited later, historical transfer interpretation can become ambiguous.

**Required fix:** store `sourceAmount`, `sourceCurrency`, `destinationAmount`, and `destinationCurrency` explicitly on transfer entries.

### P1-02 — Transfer validation does not verify sufficient funds
The UI validates positive input but does not prevent transferring more than the source Pitaka holds. The repository also does not enforce sufficient balance.

This allows a Pitaka to become negative.

Whether negative balances should be allowed is a product decision, but the current behavior is undocumented and inconsistent with goal contribution validation.

**Required fix:** define a policy:
- disallow overdrafts and validate the exact currency balance; or
- explicitly allow negative balances and surface them as liabilities/overdrafts.

### P1-03 — Goal contribution validation uses `currentAmount` rather than the actual currency balance
GoalDetailScreen compares the contribution against `src.currentAmount`.

For a multi-currency Pitaka this is only valid when the contribution currency is the Pitaka's primary currency. A Pitaka can have USD holdings in `currencyBalances` while `currentAmount` represents PHP.

**Required fix:** validate against `currencyBalances[sourceCurrency]`.

### P1-04 — Goal contribution UI does not expose the goal currency conversion decision
The repository can store `goalAmount` and `goalCurrency`, but GoalDetailScreen calls `recordGoalContribution()` without an explicit currency/conversion amount.

If source and goal currencies differ, the current repository path can deduct one numeric amount from the source while crediting the same numeric amount to the goal in another currency.

**Required fix:** use the same explicit currency-mismatch workflow required for expenses/funnels: same currency, convert, create a separate currency balance, or cancel.

### P1-05 — Recurring rules have no currency fields
`RecurringRule` stores amount, type, category and Pitaka, but not transaction currency.

The recurring processor subsequently derives income currency from the Pitaka but expense currency can fall through the PHP-default path.

**Required fix:** add transaction currency to recurring rules and preserve it when posting the ledger entry.

### P1-06 — Recurring posting is catch-up based and can surprise users
On app startup, any rule whose day has arrived is posted once for the current month.

This is intentional and documented, but it means a user who opens the app on September 24 can immediately receive a September 1 recurring transaction.

**Required fix:** keep this behavior if desired, but show a startup/recent-activity indicator or provide a rule setting such as "post missed occurrences" versus "only post on exact date."

### P1-07 — Currency balance serialization is fragile
Balances are encoded into a string such as `PHP=1000|USD=20`.

Malformed numeric values silently become `0.0` during parsing.

This can turn corrupted persisted data into an apparently valid zero balance without an error.

**Required fix:** validate persisted balance data, log/reject malformed records, and preferably normalize currencies into a child table rather than a delimited string.

### P1-08 — `Double` is used for monetary persistence
Pitakas, goals, funnels and ledger entries use `Double`.

Floating-point representation can introduce small precision errors in repeated financial operations and currency conversions.

**Required fix:** migrate to integer minor units or a decimal-safe representation with currency-specific scale.

### P1-09 — Goal deletion intentionally removes progress while preserving source deductions
Deleting a Goal deletes the Goal row but retains contribution ledger rows. Those rows then reference a missing goal.

The source Pitaka deduction remains, but the corresponding destination asset is no longer represented.

This may be intentional product behavior, but it is an accounting-model ambiguity.

**Recommended fix:** archive goals rather than delete them, or introduce a "closed goal" state so historical destination records remain understandable.

### P1-10 — Expense funnel deletion has the same orphan-history problem
Deleting a funnel can leave expense ledger rows pointing at a deleted funnel. Funnel balances/history can no longer be interpreted consistently.

**Recommended fix:** archive funnels, reassign their expenses to the system Unclassified funnel, or prohibit deletion while referenced by ledger entries.

### P2-01 — Parent Pitakas and child Pitakas can be reassigned without financial reconciliation
`setPitakaParent()` validates cycles but does not perform a balance migration.

The current hierarchy treats a parent as a pure container once it has children, but changing parentage of an existing leaf can alter which balances are included in a parent's displayed effective total.

This is structurally valid but should be treated as a presentation/accounting-boundary operation and clearly explained to the user.

### P2-02 — Parent creation inheritance is a risky implicit mutation
Creating the first child under a standalone Pitaka moves the parent's existing balances into the child and zeroes the parent.

This prevents apparent money loss, but it is a hidden accounting mutation triggered by creating a child.

**Recommended fix:** explicitly show a "Convert this Pitaka into a parent" confirmation describing the balance migration.

### P2-03 — CreateExpenseScreen always starts with PHP
The screen initializes `currency = "PHP"`, even if the selected Pitaka is USD.

The user can therefore create a USD Pitaka expense while the transaction currency remains PHP unless manually changed.

**Required fix:** initialize transaction currency from the selected Pitaka and update it when the Pitaka changes, unless the user has deliberately overridden it.

### P2-04 — UI error messages can display the wrong currency
GoalDetailScreen uses a literal `$` when reporting insufficient funds. That is incorrect for PHP, EUR, JPY, etc.

**Required fix:** use the source Pitaka currency code/symbol formatter.

### P2-05 — UI operations launch asynchronously and immediately navigate away
Several buttons call ViewModel methods and then immediately call `onDone()`.

The user can leave the screen before the database operation completes or fails. Exceptions from `viewModelScope.launch` are not surfaced to the initiating UI.

**Required fix:** expose operation state/result from the ViewModel and navigate only after successful completion; display failures.

### P2-06 — Validation is concentrated in UI rather than the domain/repository
Many screens use inline checks such as `amount > 0`, but repository methods do not consistently validate names, currencies, dates, relationships, or sufficient funds.

A ViewModel/repository can be invoked by another screen or future feature without those UI safeguards.

**Required fix:** make repository/domain validation authoritative and treat UI validation as an additional convenience layer.

### P3-01 — Repository is a god object
`PitakaRepository` owns Pitakas, goals, funnels, ledger effects, budgets, currencies and recurring rules.

This increases the chance that unrelated changes break accounting.

**Recommended refactor:** separate CRUD repositories and extract an `AccountingService` responsible for applying/reversing ledger effects.

### P3-02 — No accounting regression test suite
No `app/src/test` or `app/src/androidTest` test suite was found.

At minimum, tests should cover every ledger type, deletion, editing, multi-currency conversion, hierarchy aggregation, migrations and recurring posting.

### P3-03 — Build workflow does not include the current technical-debt branch
The discovered APK workflow triggers on `main` and `feature/pitaka-home-hierarchy-spending-refactor`, not `feature/technical-debt-accounting-hardening`.

Therefore pushes to the audited branch do not necessarily trigger the expected APK workflow.

**Required fix:** add the active feature branch or, preferably, use a pattern/PR workflow that validates every development branch.

## Recommended test matrix

### Ledger integrity
- income increases source balance
- expense decreases source balance
- transfer preserves total net worth
- cross-currency transfer deducts source currency and adds destination currency
- goal contribution deducts source and increases goal
- adjustment changes exactly one specified currency
- deleting a ledger entry exactly reverses every affected balance
- editing an entry produces the same final state as delete-old + create-new

### Multi-currency
- same-currency transaction
- different transaction/funnel currencies
- different source/goal currencies
- missing exchange rate
- zero/negative exchange rate
- malformed persisted balance
- changing primary currency after historical transactions

### Hierarchy
- leaf balance
- parent aggregation
- nested parent aggregation
- first-child inheritance
- reparenting
- cycle prevention
- deletion/archiving of parent and child

### Deletion
- delete Pitaka referenced by transfer
- delete Pitaka referenced by expense
- delete Pitaka referenced by goal contribution
- delete Goal with contributions
- delete Funnel with expenses

### Recurring
- exact due day
- missed due day
- February day 31
- reopening app twice in same month
- disabled rule
- multi-currency recurring rule

## Remediation status
The first hardening pass has been applied: destructive Pitaka/Goal/Funnel deletion is blocked when financial history exists; transfer destination currency is persisted; recurring rules carry currency; expense, goal, and transfer operations validate source funds; manual adjustments are currency-aware; expense creation inherits the Pitaka currency; linked funnel/goal allocations are rescaled when an entry amount is edited; database migrations were advanced to version 8; the APK workflow now includes the accounting-hardening branch; and currency-balance unit tests were added.

Remaining architectural work includes decimal-safe money storage, base-currency normalization of monthly/category statistics, complete multi-currency Goal/Funnel progress models, user-facing operation error state, and extraction of accounting logic from the repository.

## Audit conclusion
The branch has a good structural foundation, but it should not yet be considered accounting-safe. The priority is to make ledger effects authoritative and reversible across every linked object, then add regression tests before further feature expansion.


## Continued Hardening — 2026-09-24

### Money and serialization
- Currency-balance parsing now rejects non-finite values and normalizes currency codes.
- Balance writes reject non-finite amounts before persistence.
- Repository-level validation rejects non-finite monetary transaction amounts.

### Recurring transactions
- Recurring rule creation validates type, name, amount, day-of-month, and target Pitaka.
- Recurring currency is inherited from the target Pitaka and persisted explicitly.

### Reporting
- Monthly and category reporting uses base-currency conversion before aggregation, preventing mixed-currency raw sums.
- Goal totals use persisted per-currency balances when calculating aggregate progress.

### Still pending
- Full integer/decimal-safe schema migration for every persisted monetary field.
- Comprehensive Room integration tests for apply/reverse/edit/delete/transfer/contribution flows.
- Multi-currency funnel progress UI and limit semantics.
- Centralized user-visible error state in the ViewModel/UI.


## Multi-Currency Funnel/Goal Hardening
- Funnel spending can now be retrieved as a currency-keyed map from ledger history.
- Goal progress can now be retrieved as a currency-keyed aggregate for each GoalType.
- Funnel presentation models expose per-currency spending alongside legacy primary-currency totals.
- This avoids treating USD/PHP/etc. as interchangeable numeric units.


## Ledger Edit Hardening
- Transaction edits now validate name, amount, target Pitaka, available currency balance, and Goal currency before committing the replacement.
- The old ledger effect is reversed and the replacement applied inside one Room transaction, so validation failure rolls the reversal back atomically.
- Pitaka creation now rejects malformed currency codes.


## Hierarchy and Currency Metadata Hardening
- Pitaka re-parenting is now atomic and cycle-safe inside a Room transaction.
- Pitaka metadata updates validate names/currency codes.
- A Pitaka primary currency cannot be switched to a currency that already has a non-zero secondary balance; this prevents silently reinterpreting existing money under a different primary currency.


## Goal Multi-Currency Correction
- Goal contributions no longer reject a valid source currency merely because it differs from the Goal's primary currency.
- Contributions are stored using their actual transaction currency and added to the Goal's currencyBalances.
- Edit validation now verifies Goal existence without imposing a single-currency restriction.


## Ledger Invariant Review
- Transfer validation checks source and destination identities and requires an explicit destination amount for cross-currency transfers.
- Goal contributions preserve the transaction currency and update Goal currencyBalances accordingly.
- Removed a stale edit-time validation that incorrectly required Goal primary currency to equal contribution currency.
- Ledger reversal negates secondary, funnel, and goal amounts together with the primary amount.


## Transfer Validation
- Transfer posting now rejects blank names at the repository boundary.
- Source balance, source/destination identity, and cross-currency destination amount remain validated inside the same Room transaction.


## Deletion and Adjustment Integrity
- Ledger deletion reverses the full accounting effect before deleting the row, inside one Room transaction.
- Pitakas with transaction history cannot be deleted, preserving ledger auditability.
- Goals with contribution history cannot be deleted; the repository directs the user toward archival semantics.
- Manual balance adjustments now reject NaN/infinite targets, malformed currencies, and missing Pitakas.


## ViewModel Error Propagation
- Repository operation failures are now captured by a shared ViewModel operation-error StateFlow.
- Successful operations clear the previous error.
- The UI can observe this state and render actionable Snackbars/dialogs instead of silently losing coroutine exceptions.
- This centralizes user-facing failure propagation while keeping accounting validation in the repository.


## Complete ViewModel Mutation Error Routing
- All repository mutation calls in PitakaViewModel now route through launchOperation, including recurring rules, currency settings, monthly budgets, Pitakas, and manual adjustments.
- Recurring catch-up failures are also surfaced instead of becoming silent startup coroutine failures.
- UI can therefore provide one consistent error surface for repository validation failures.


## Global Compose Error Surface
- PitakaNavGraph observes the ViewModel operationError StateFlow.
- Repository failures are displayed through a Material SnackbarHost at the navigation root, so errors remain visible regardless of which child screen initiated the operation.
- The error is cleared after presentation to prevent repeated display on recomposition.


## Recurring Transaction Date Hardening
- Catch-up transactions are now stamped with the effective scheduled day rather than app-open time.
- Day 31 in a shorter month is clamped to that month's last day, matching the existing recurring-rule semantics.
- Recurring currency codes are validated before posting.
- A missing Pitaka or unsupported recurring type now fails the transaction instead of silently updating the rule.


## Recurring Transaction Hardening
- Recurring entries are dated to their effective scheduled day rather than the app-open timestamp.
- Catch-up validates rule type, name, amount, day-of-month, currency, and referenced Pitaka before posting.
- Day 29/30/31 rules are clamped to the final calendar day of shorter months, preserving the documented monthly behavior.
- The recurring posting and lastAppliedMonth update remain atomic, preventing a rule from being marked applied when posting fails.


## Multi-Currency Reporting and Rate Validation
- Monthly expense/income reporting is calculated in the configured base currency from the complete ledger, avoiding raw aggregation of different currencies.
- Current-month expense totals use the same conversion path as monthly statistics.
- Exchange rates now require a three-letter currency code and a positive finite rate, preventing malformed or non-numeric conversion factors.


## Exchange-Rate Safety
- Missing exchange rates are no longer treated as an implicit 1:1 conversion.
- Conversion returns no value when either currency's rate is unavailable or invalid; aggregate reports exclude unconvertible entries rather than assigning a fabricated rate.
- Equal-currency conversion remains exact and does not require a stored exchange rate.
- This prevents a missing USD/PHP rate from silently turning $1,000 into ₱1,000.


## Missing-Rate User Feedback
- Consolidated reporting no longer fabricates 1:1 rates for unknown currencies.
- The ViewModel exposes whether ledger entries require an unavailable conversion rate.
- The navigation-level Snackbar warns the user that some consolidated totals are incomplete until the relevant exchange rate is configured.


## Conversion Semantics and Historical Rates
- Conversion into the configured base currency treats the base currency as a unit rate of 1; a stored rate row for the base currency is not required.
- Non-base currencies still require a positive configured rate to participate in consolidated totals.
- Base-currency validation now requires three ASCII letters, matching the repository's other currency-code validation.
- Historical ledger rows currently do not store a transaction-time exchange-rate snapshot. Existing historical reports therefore depend on the currently configured rates for legacy/non-snapshotted foreign-currency rows. This is a documented accounting limitation, not silently presented as historical-rate accuracy.
- A future schema migration should add nullable transaction-time conversion metadata and populate it for new transactions; existing rows must not be backfilled with today's rates because that would fabricate historical data.


## Automated Regression Coverage
- Added unit coverage for CurrencyBalances normalization, stable encoding, malformed currency rejection, and non-finite amount rejection.
- This utility is a critical persistence boundary for multi-currency Pitakas, Goals, and Funnels, so validation behavior is now protected by executable tests rather than documentation alone.


## Development checklist — live status (2026-09-24)

### P0 — Financial correctness
- [x] Harden ledger effect application: fail loudly when required Pitaka/Goal/Funnel/transfer endpoints are missing.
- [ ] Prove `applyEffect()` and `reverseEffect()` are exact inverses for every ledger type with automated tests.
  - [x] Extract pure edit/reversal arithmetic and add regression coverage for scaled transfer/allocation legs.
  - [x] Add pure CurrencyBalances add/reverse regression coverage.
- [x] Fix expense edit allocation so funnel amount scales with the edited transaction and remains validated.
- [x] Fix goal-contribution edit allocation so goal amount scales with the edited transaction and remains validated.
- [x] Complete transfer accounting audit, including cross-currency edit/delete reversal.
  - [x] Cross-currency transfer edits now scale the destination leg with the source amount.
- [x] Complete manual-adjustment multi-currency audit.
- [ ] Complete deletion/archive policy for ledger-linked Pitakas/Goals/Funnels.
- [ ] Add transaction-time exchange-rate/base-amount snapshot fields for future historical reporting.

### P1 — Multi-currency and recurring
- [x] Validate exchange-rate codes and positive finite rates.
- [x] Handle base currency as an implicit 1:1 conversion rate.
- [x] Surface missing exchange-rate feedback in the UI.
- [x] Harden UI-facing monthly conversion to base currency.
- [x] Make Goal/Funnel creation and repository persistence currency-aware.
- [x] Make Goal/Funnel balances consistently multi-currency in all DAO/UI paths.
  - [x] Funnel summaries use funnel allocation amounts in the funnel currency and surface other-currency allocations.
  - [x] Goal summaries display the configured goal currency instead of a hard-coded currency symbol.
- [ ] Persist explicit source/destination currencies for transfers.
- [x] Add transaction currency to recurring rules.
- [x] Audit recurring catch-up behavior and short-month handling with tests.

### P1/P2 — Data integrity and lifecycle
- [x] Reject malformed/non-finite currency-balance writes.
- [x] Add migration tests for every Room schema version.
- [x] Define archive/soft-delete behavior for financial entities.
- [x] Audit hierarchy re-parenting and first-child balance migration.
- [ ] Replace monetary `Double` persistence with a decimal-safe/minor-unit representation (planned migration).

### P2/P3 — Quality and UX
- [x] Propagate major repository mutation errors to ViewModel/UI feedback.
- [x] Add initial CurrencyBalances unit coverage.
- [ ] Expand accounting regression/unit/integration tests.\n  - [x] Add Android Room lifecycle coverage for income/expense edit-delete, goal contribution reversal, cross-currency transfer reversal, and manual multi-currency adjustment.
- [ ] Fix stale current-month state across month boundaries.
- [ ] Verify all build/CI paths after accounting changes.\n  - [ ] Run the new Android instrumentation suite in GitHub Actions and inspect the result.
- [ ] Complete comprehensive codebase/user documentation.


## Recurring Integration Coverage — 2026-09-24
- Added Android Room tests for recurring income catch-up, short-month day-31 clamping, duplicate prevention within a month, due-date gating, multi-currency recurring expenses, and disabling recurring rules.


## Room Migration Contract Coverage — 2026-09-24
- Added Android instrumentation coverage for the 4→5→6→7→8 migration chain.
- Verifies hierarchy/card-style/system-funnel columns, funnel/goal allocation columns, transfer secondary-currency introduction, recurring-rule currency introduction, and preservation of the seeded legacy rows.
- Historical destination currency is not fabricated for legacy cross-currency transfers when the pre-migration schema did not persist that information.


## Pitaka Hierarchy / Adjustment Audit — 2026-09-24
- Verified first-child conversion preserves all existing parent currency balances in the child and clears the parent container balance.
- Verified manual adjustments are currency-specific and recorded as ADJUSTMENT ledger entries.
- Verified self/ancestor hierarchy cycles are rejected and financial Pitakas with transaction history cannot be deleted.
- Remaining lifecycle work: define explicit archive/soft-delete semantics for financial entities.


## Archive Lifecycle — 2026-09-24
- Pitakas, Goals, and Expense Funnels now have `archivedAt` state persisted through Room migration 8→9.
- Active DAO listings hide archived containers while ledger history remains intact.
- Pitakas/Goals/Funnels with financial history are not permanently deletable; archive/restore is the lifecycle path.
- System expense funnels remain protected from archive/delete operations.


## Historical Conversion Snapshots — 2026-09-24
- Ledger entries now persist the transaction-time rate-to-base, converted base amount, and base currency for newly recorded transactions.
- Room migration 9→10 adds the nullable fields; existing rows remain null because historical rates must not be fabricated from today's rates.
- New non-base transactions require a configured usable rate at posting time; base-currency transactions use an implicit 1.0 rate.
