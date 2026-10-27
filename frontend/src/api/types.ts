export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CurrentUser {
  username: string
  displayName: string
}

export interface Counterparty {
  id: number
  name: string
  nip: string | null
  iban: string | null
}

export interface CounterpartyRequest {
  name: string
  nip: string | null
  iban: string | null
}

export type InvoiceStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'OVERPAID'

export interface Invoice {
  id: number
  number: string
  counterparty: { id: number; name: string; nip: string | null }
  issueDate: string
  dueDate: string
  grossAmount: string
  paidAmount: string
  remainingAmount: string
  currency: string
  status: InvoiceStatus
  overdue: boolean
  daysOverdue: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface InvoiceRequest {
  number: string
  counterpartyId: number
  issueDate: string
  dueDate: string
  grossAmount: string
  currency: string
}

export interface InvoiceImportResult {
  totalRows: number
  imported: number
  skipped: { line: number; invoiceNumber: string | null; reason: string }[]
  detectedEncoding: string
}

export interface SortParam {
  column: string
  direction: 'asc' | 'desc'
}
