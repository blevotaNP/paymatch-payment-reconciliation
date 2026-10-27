import type { AuditAction } from '../api/types'

export const AUDIT_ACTIONS: Record<AuditAction, { label: string; color: string }> = {
  IMPORT_CREATED: { label: 'Statement imported', color: 'blue' },
  IMPORT_UNDONE: { label: 'Import undone', color: 'gray' },
  INVOICE_CREATED: { label: 'Invoice created', color: 'blue' },
  INVOICE_UPDATED: { label: 'Invoice updated', color: 'blue' },
  INVOICE_DELETED: { label: 'Invoice deleted', color: 'red' },
  INVOICES_IMPORTED: { label: 'Invoices imported', color: 'blue' },
  MATCH_AUTO_CONFIRMED: { label: 'Matched automatically', color: 'teal' },
  MATCH_CONFIRMED: { label: 'Match confirmed', color: 'teal' },
  MANUAL_ALLOCATION: { label: 'Matched manually', color: 'teal' },
  SUGGESTION_REJECTED: { label: 'Suggestion rejected', color: 'orange' },
  TRANSACTION_IGNORED: { label: 'Not an invoice payment', color: 'gray' },
  MATCH_UNDONE: { label: 'Decision undone', color: 'red' },
}
