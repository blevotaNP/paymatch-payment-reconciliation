import type { InvoiceStatus } from '../api/types'

export const INVOICE_STATUS: Record<InvoiceStatus, { label: string; color: string }> = {
  UNPAID: { label: 'Unpaid', color: 'gray' },
  PARTIALLY_PAID: { label: 'Partially paid', color: 'yellow' },
  PAID: { label: 'Paid', color: 'teal' },
  OVERPAID: { label: 'Overpaid', color: 'grape' },
}

export const INVOICE_STATUS_OPTIONS = Object.entries(INVOICE_STATUS).map(([value, { label }]) => ({ value, label }))
