import { Alert, Badge, Code, Divider, Drawer, Group, Loader, Paper, SimpleGrid, Stack, Text, Title } from '@mantine/core'
import { IconInfoCircle } from '@tabler/icons-react'
import { useTransaction } from '../../api/statements'
import { AmountText } from '../../components/AmountText'
import { ErrorState } from '../../components/ErrorState'
import { MatchCell } from '../../components/MatchBadges'
import { MoneyText } from '../../components/MoneyText'
import { formatDate, formatDateTime } from '../../lib/dates'
import { formatIban } from '../../lib/validation'
import { SuggestionCard } from './SuggestionCard'

export function TransactionDrawer({ id, onClose }: { id: number | null; onClose: () => void }) {
  const { data, isPending, error } = useTransaction(id)

  return (
    <Drawer opened={id != null} onClose={onClose} position="right" size="lg" title={<Text fw={600}>Transaction</Text>}>
      {isPending ? (
        <Loader size="sm" />
      ) : error ? (
        <ErrorState error={error} />
      ) : (
        <Stack>
          <Group justify="space-between" align="flex-start">
            <Stack gap={2}>
              <Text size="xs" c="dimmed">
                Booked {formatDate(data.transaction.bookingDate)}
              </Text>
              <Text fw={600}>{data.transaction.counterpartyName ?? 'No counterparty'}</Text>
            </Stack>
            <AmountText amount={data.transaction.amount} currency={data.transaction.currency} fz="xl" fw={650} />
          </Group>

          <Text size="sm">{data.transaction.title ?? '–'}</Text>
          <MatchCell tx={data.transaction} />
          <Divider />

          <SimpleGrid cols={2} spacing="sm">
            <Field label="Counterparty account" value={formatIban(data.transaction.counterpartyAccount)} mono />
            <Field label="Own account" value={formatIban(data.transaction.accountIban)} mono />
            <Field label="Value date" value={formatDate(data.transaction.valueDate)} />
            <Field label="Bank reference" value={data.transaction.bankReference ?? '–'} mono />
            <Field label="Imported from" value={data.importFileName} />
            <div>
              <Text size="xs" c="dimmed">
                Format
              </Text>
              <Badge variant="outline" color="gray" mt={2}>
                {data.transaction.bankFormat}
              </Badge>
            </div>
          </SimpleGrid>

          {data.transaction.amount.startsWith('-') ? null : (
            <Stack gap="xs">
              <Title order={5}>Matching</Title>
              {data.allocations.length > 0 && (
                <Paper p="sm" bg="teal.0" withBorder={false}>
                  <Stack gap={6}>
                    {data.allocations.map((allocation) => (
                      <Group key={allocation.id} justify="space-between" wrap="nowrap">
                        <div>
                          <Text size="sm" fw={500}>
                            Paid {allocation.invoiceNumber}
                          </Text>
                          <Text size="xs" c="dimmed">
                            {allocation.source === 'AUTO' ? 'Confirmed automatically' : `Confirmed by ${allocation.createdBy}`} ·{' '}
                            {formatDateTime(allocation.createdAt)}
                          </Text>
                        </div>
                        <MoneyText amount={allocation.amount} currency={data.transaction.currency} fw={500} />
                      </Group>
                    ))}
                  </Stack>
                </Paper>
              )}
              {data.suggestions.map((suggestion) => (
                <SuggestionCard key={suggestion.id} suggestion={suggestion} currency={data.transaction.currency} />
              ))}
              {data.suggestions.length === 0 && data.allocations.length === 0 && (
                <Alert color="gray" icon={<IconInfoCircle size={18} />} py="xs">
                  No invoice matches this payment. It will be checked again when new invoices are added.
                </Alert>
              )}
            </Stack>
          )}

          {data.rawData && (
            <div>
              <Text size="xs" c="dimmed" mb={4}>
                Original data from the file
              </Text>
              <Code block style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                {data.rawData}
              </Code>
            </div>
          )}
        </Stack>
      )}
    </Drawer>
  )
}

function Field({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      <Text size="sm" ff={mono ? 'monospace' : undefined}>
        {value}
      </Text>
    </div>
  )
}
