# Sample files

All data here is made up. Company names, NIP numbers and account numbers are fictional
(the numbers pass checksum validation, but don't belong to anyone).

| File | Format | Notes |
|------|--------|-------|
| `invoices.csv` | invoice import | Windows-1250, `;`-separated, Polish amounts |
| `statements/mt940_pekao_2026-09.sta` | MT940 | Polish `:86:` sub-fields with `~` separator, Windows-1250 |
| `statements/mt940_pekao_2026-09-25_2026-10-10.sta` | MT940 | overlaps the file above by two transactions, to show deduplication |
| `statements/pko_bp_historia_2026-10.csv` | PKO BP CSV | contains two identical fees on the same day |
| `statements/mbank_lista_operacji_2026-10.csv` | mBank CSV | metadata block, quoted account numbers, summary rows |
| `statements/ing_lista_transakcji_2026-10.csv` | ING CSV | UTF-8 with BOM, unbooked card authorization, footer line |

The bank CSV files are **approximations**: I built them from the publicly known layout of each export
(column names, separators, encodings, quirks), not from real files. Banks change their exports from
time to time, so a real file may need small adjustments in the parser. MT940 follows the standard,
with the sub-field layout that most Polish banks use in `:86:`.

The statements are designed to line up with `invoices.csv`, so after importing everything you get
exact matches, a title with a typo, partial payments, a payment for two invoices, an overpayment and
transactions that are not invoice payments at all (fees, refunds, salaries).
