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

export type BankFormat = 'MT940' | 'PKO_BP_CSV' | 'MBANK_CSV' | 'ING_CSV'

export interface BankFormatInfo {
  code: BankFormat
  label: string
  description: string
}

export interface SkippedRow {
  line: number
  reason: string
  content: string | null
}

export interface StatementImport {
  id: number
  fileName: string
  bankFormat: BankFormat
  bankLabel: string
  accountIban: string | null
  periodFrom: string | null
  periodTo: string | null
  encoding: string
  totalRows: number
  importedCount: number
  duplicateCount: number
  skippedCount: number
  skippedRows: SkippedRow[]
  warnings: string[]
  status: 'ACTIVE' | 'UNDONE'
  importedBy: string
  importedAt: string
  undoneBy?: string
  undoneAt?: string
  duplicates?: { line: number; bookingDate: string; amount: string; title: string | null }[]
}

export interface Transaction {
  id: number
  importId: number
  bankFormat: BankFormat
  accountIban: string | null
  bookingDate: string
  valueDate: string | null
  amount: string
  currency: string
  counterpartyName: string | null
  counterpartyAccount: string | null
  title: string | null
  bankReference: string | null
}

export interface TransactionDetails {
  transaction: Transaction
  importFileName: string
  rawData: string | null
}
