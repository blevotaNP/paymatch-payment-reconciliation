import { Badge, Code, Divider, Drawer, Group, Loader, SimpleGrid, Stack, Text } from '@mantine/core'
import { useTransaction } from '../../api/statements'
import { AmountText } from '../../components/AmountText'
import { ErrorState } from '../../components/ErrorState'
import { formatDate } from '../../lib/dates'
import { formatIban } from '../../lib/validation'

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
