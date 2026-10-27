import { Badge, Group, Loader, Paper, Stack, Text } from '@mantine/core'
import { useInvoicePayments } from '../../api/review'
import type { Invoice } from '../../api/types'
import { MoneyText } from '../../components/MoneyText'
import { formatDate, formatDateTime } from '../../lib/dates'

export function InvoicePayments({ invoice }: { invoice: Invoice }) {
  const { data, isPending } = useInvoicePayments(invoice.id)

  if (isPending) return <Loader size="sm" />
  return (
    <Stack gap="sm">
      <Group grow>
        <Summary label="Gross" amount={invoice.grossAmount} currency={invoice.currency} />
        <Summary label="Paid" amount={invoice.paidAmount} currency={invoice.currency} />
        <Summary label="Left to pay" amount={invoice.remainingAmount} currency={invoice.currency} />
      </Group>
      {!data?.length ? (
        <Text size="sm" c="dimmed">
          No payments matched to this invoice yet.
        </Text>
      ) : (
        data.map((payment) => (
          <Paper key={payment.id} p="sm">
            <Group justify="space-between" wrap="nowrap">
              <div>
                <Group gap={6}>
                  <Text size="sm" fw={500}>
                    {formatDate(payment.bookingDate)} · {payment.sender ?? 'bank transfer'}
                  </Text>
                  <Badge size="xs" color={payment.source === 'AUTO' ? 'teal' : 'blue'}>
                    {payment.source === 'AUTO' ? 'Automatic' : 'Manual'}
                  </Badge>
                </Group>
                {payment.title && (
                  <Text size="xs" lineClamp={1}>
                    {payment.title}
                  </Text>
                )}
                <Text size="xs" c="dimmed">
                  {payment.source === 'AUTO' ? 'Matched' : `Matched by ${payment.createdBy}`} {formatDateTime(payment.createdAt)}
                </Text>
                {payment.note && (
                  <Text size="xs" fs="italic">
                    {payment.note}
                  </Text>
                )}
              </div>
              <MoneyText amount={payment.amount} currency={invoice.currency} fw={500} />
            </Group>
          </Paper>
        ))
      )}
    </Stack>
  )
}

function Summary({ label, amount, currency }: { label: string; amount: string; currency: string }) {
  return (
    <div>
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      <MoneyText amount={amount} currency={currency} fw={600} />
    </div>
  )
}
