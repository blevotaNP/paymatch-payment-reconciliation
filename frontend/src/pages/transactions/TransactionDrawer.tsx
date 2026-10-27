import { Alert, Badge, Button, Code, Divider, Drawer, Group, Loader, Paper, SimpleGrid, Stack, Text, Title } from '@mantine/core'
import { modals } from '@mantine/modals'
import { IconArrowBackUp, IconChecklist, IconInfoCircle } from '@tabler/icons-react'
import { useNavigate } from 'react-router'
import { useReviewAction, useTransactionHistory } from '../../api/review'
import { useTransaction } from '../../api/statements'
import { AmountText } from '../../components/AmountText'
import { ErrorState } from '../../components/ErrorState'
import { HistoryTimeline } from '../../components/HistoryTimeline'
import { notifyError, notifySuccess } from '../../components/notify'
import { MatchCell } from '../../components/MatchBadges'
import { MoneyText } from '../../components/MoneyText'
import { formatDate, formatDateTime } from '../../lib/dates'
import { formatIban } from '../../lib/validation'
import { SuggestionCard } from './SuggestionCard'

export function TransactionDrawer({ id, onClose }: { id: number | null; onClose: () => void }) {
  const { data, isPending, error } = useTransaction(id)
  const history = useTransactionHistory(id)
  const action = useReviewAction()
  const navigate = useNavigate()

  const confirmUndo = () => {
    if (!data) return
    const tx = data.transaction
    modals.openConfirmModal({
      title: tx.matchStatus === 'MATCHED' ? 'Undo match' : 'Send back to review',
      children: (
        <Text size="sm">
          {tx.matchStatus === 'MATCHED'
            ? `The payment will be removed from ${tx.matchedInvoices.join(', ')} and go back to the review queue.`
            : 'The payment will go back to the review queue.'}
        </Text>
      ),
      labels: { confirm: 'Undo', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: () =>
        action.mutate(
          { transactionId: tx.id, version: tx.version, action: { type: 'undo' } },
          {
            onSuccess: () => notifySuccess('Decision undone; the payment is back in the review queue'),
            onError: (e) => notifyError(e, 'Could not undo'),
          },
        ),
    })
  }

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
          <Group justify="space-between">
            <MatchCell tx={data.transaction} />
            {!data.transaction.amount.startsWith('-') &&
              (data.transaction.matchStatus === 'MATCHED' || data.transaction.matchStatus === 'IGNORED' ? (
                <Button size="xs" variant="default" color="red" leftSection={<IconArrowBackUp size={14} />} onClick={confirmUndo} loading={action.isPending}>
                  {data.transaction.matchStatus === 'MATCHED' ? 'Undo match' : 'Back to review'}
                </Button>
              ) : (
                <Button size="xs" variant="light" leftSection={<IconChecklist size={14} />} onClick={() => navigate(`/review?id=${data.transaction.id}`)}>
                  Review this payment
                </Button>
              ))}
          </Group>
          {data.transaction.matchStatus === 'IGNORED' && (
            <Alert color="gray" py="xs">
              Not an invoice payment: {data.transaction.ignoreReasonLabel?.toLowerCase()}
              {data.transaction.ignoreNote ? ` (${data.transaction.ignoreNote})` : ''}
            </Alert>
          )}
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
                          {allocation.note && (
                            <Text size="xs" fs="italic">
                              {allocation.note}
                            </Text>
                          )}
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
              {data.suggestions.length === 0 && data.allocations.length === 0 && data.transaction.matchStatus !== 'IGNORED' && (
                <Alert color="gray" icon={<IconInfoCircle size={18} />} py="xs">
                  No invoice matches this payment. It will be checked again when new invoices are added.
                </Alert>
              )}
            </Stack>
          )}

          <Stack gap="xs">
            <Title order={5}>History</Title>
            <HistoryTimeline events={history.data} loading={history.isPending} />
          </Stack>

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
