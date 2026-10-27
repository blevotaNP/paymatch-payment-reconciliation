import { Badge } from '@mantine/core'
import type { InvoiceStatus } from '../api/types'
import { INVOICE_STATUS } from '../lib/invoiceStatus'

export function InvoiceStatusBadge({ status }: { status: InvoiceStatus }) {
  const { label, color } = INVOICE_STATUS[status]
  return <Badge color={color}>{label}</Badge>
}
