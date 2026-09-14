# Pitaka Development Improvements

## Implemented
- PHP is now the default currency for new Pitakas, currency settings, and fallback display values.
- Ledger entries now retain the transaction currency.
- Goals and expense funnels now carry a currency field, defaulting to PHP, preparing the data model for currency-aware progress and spending.
- Added hide/show masking controls to transaction history amounts in Pitaka, Expense, and Goal logs.
- Added an animated startup screen using the supplied Pitaka artwork; the logo moves vertically in a wave-like motion before the app opens.

## Important implementation note
The current data model remains backward-compatible through Room's destructive-migration configuration. Existing local data may be reset when the schema changes. A production release should replace destructive migration with explicit migrations and complete the multi-currency reconciliation flow: per-container currency balances, conversion dialogs, and currency-specific funnel/goal aggregation.
