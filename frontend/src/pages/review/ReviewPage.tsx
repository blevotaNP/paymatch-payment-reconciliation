import { Alert, Badge, Button, Grid, Group, Kbd, Loader, Paper, ScrollArea, SegmentedControl, Stack, Text, Tooltip } from '@mantine/core'
import { useDisclosure, useHotkeys } from '@mantine/hooks'
import { IconCheck, IconChecks, IconInfoCircle, IconKeyboard, IconX } from '@tabler/icons-react'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { useReviewAction, useReviewSummary } from '../../api/review'
import { useTransaction, useTransactions } from '../../api/statements'
import type { Suggestion, Transaction, TransactionDetails } from '../../api/types'
import { AmountText } from '../../components/AmountText'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { notifyError, notifySuccess } from '../../components/notify'
import { PageHeader } from '../../components/PageHeader'
import { formatDate } from '../../lib/dates'
import { formatIban } from '../../lib/validation'
import { SuggestionCard } from '../transactions/SuggestionCard'
import { IgnoreModal } from './IgnoreModal'
import { ManualMatchModal } from './ManualMatchModal'
import { QueueItem } from './QueueItem'
import { ShortcutsModal } from './ShortcutsModal'

type Tab = 'SUGGESTED' | 'UNMATCHED'

function isTyping(): boolean {
  const el = document.activeElement as HTMLElement | null
  if (!el) return false
  if (el.isContentEditable || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT') return true
  return el.tagName === 'INPUT' && !['radio', 'checkbox', 'button'].includes((el as HTMLInputElement).type)
}

export function ReviewPage() {
  const [searchParams] = useSearchParams()
  const [tab, setTab] = useState<Tab>('SUGGESTED')
  const [selectedId, setSelectedId] = useState<number | null>(searchParams.get('id') ? Number(searchParams.get('id')) : null)
  const [manualFor, setManualFor] = useState<TransactionDetails | null>(null)
  const [ignoreFor, setIgnoreFor] = useState<Transaction | null>(null)
  const [helpOpened, help] = useDisclosure(false)
  const itemRefs = useRef(new Map<number, HTMLButtonElement>())

  const { data: summary } = useReviewSummary()
  const list = useTransactions({
    matchStatus: [tab],
    direction: 'INCOMING',
    page: 0,
    size: 100,
    sort: { column: 'bookingDate', direction: 'asc' },
  })
  const items = list.data?.content ?? []
  const details = useTransaction(selectedId)
  const action = useReviewAction()

  // keep a valid selection when the list changes (tab switch, item decided by someone else)
  useEffect(() => {
    if (list.isPlaceholderData || list.isFetching) return
    if (items.length === 0) {
      if (selectedId != null && !details.data) setSelectedId(null)
      return
    }
    if (selectedId == null || (!items.some((tx) => tx.id === selectedId) && details.data?.transaction.matchStatus !== tab)) {
      setSelectedId(items[0].id)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items, tab, list.isFetching])

  useEffect(() => {
    if (selectedId != null) itemRefs.current.get(selectedId)?.scrollIntoView({ block: 'nearest' })
  }, [selectedId])

  const index = items.findIndex((tx) => tx.id === selectedId)
  const neighbourId = () => items[index + 1]?.id ?? items[index - 1]?.id ?? null
  const move = (delta: number) => {
    const next = items[Math.max(0, Math.min(items.length - 1, (index < 0 ? 0 : index) + delta))]
    if (next) setSelectedId(next.id)
  }

  const tx = details.data?.transaction
  const pending = details.data?.suggestions.filter((s) => s.status === 'PENDING') ?? []
  const rejected = details.data?.suggestions.filter((s) => s.status === 'REJECTED') ?? []
  const busy = action.isPending
  const modalOpen = manualFor != null || ignoreFor != null || helpOpened

  const confirm = (suggestion: Suggestion) => {
    if (!tx || busy) return
    const next = neighbourId()
    action.mutate(
      { transactionId: tx.id, version: tx.version, action: { type: 'confirm', suggestionId: suggestion.id } },
      {
        onSuccess: () => {
          notifySuccess(`Confirmed ${suggestion.items.map((i) => i.invoiceNumber).join(', ')}`)
          setSelectedId(next)
        },
        onError: (e) => {
          notifyError(e, 'Could not confirm')
          details.refetch()
        },
      },
    )
  }

  const reject = (suggestion: Suggestion) => {
    if (!tx || busy) return
    const next = neighbourId()
    action.mutate(
      { transactionId: tx.id, version: tx.version, action: { type: 'reject', suggestionId: suggestion.id } },
      {
        onSuccess: (updated) => {
          notifySuccess('Suggestion rejected')
          // stay on the payment while it still has other suggestions
          if (updated.transaction.matchStatus !== tab) setSelectedId(next)
        },
        onError: (e) => {
          notifyError(e, 'Could not reject')
          details.refetch()
        },
      },
    )
  }

  const whenIdle = (fn: () => void) => () => {
    if (!modalOpen && !busy && !isTyping()) fn()
  }
  useHotkeys([
    ['j', whenIdle(() => move(1))],
    ['ArrowDown', whenIdle(() => move(1))],
    ['k', whenIdle(() => move(-1))],
    ['ArrowUp', whenIdle(() => move(-1))],
    ['c', whenIdle(() => pending[0] && confirm(pending[0]))],
    ['Enter', whenIdle(() => pending[0] && confirm(pending[0]))],
    ['r', whenIdle(() => pending[0] && reject(pending[0]))],
    ['m', whenIdle(() => details.data && setManualFor(details.data))],
    ['i', whenIdle(() => tx && setIgnoreFor(tx))],
    ['shift+slash', () => help.open()],
    // checked in whenIdle instead: the default would also block shortcuts after clicking the tab switch (a radio input)
  ], [])

  const afterModal = () => {
    const next = neighbourId()
    setManualFor(null)
    setIgnoreFor(null)
    setSelectedId(next)
  }

  return (
    <>
      <PageHeader
        title="Review queue"
        description="Payments the engine could not match on its own. Confirm, correct or set them aside."
        actions={
          <Group gap="sm">
            <SegmentedControl
              value={tab}
              onChange={(value) => {
                setTab(value as Tab)
                setSelectedId(null)
              }}
              data={[
                { value: 'SUGGESTED', label: `To review${summary ? ` (${summary.toReview})` : ''}` },
                { value: 'UNMATCHED', label: `No suggestion${summary ? ` (${summary.unmatched})` : ''}` },
              ]}
            />
            <Tooltip label="Keyboard shortcuts (?)">
              <Button variant="default" px="xs" onClick={help.open} aria-label="Keyboard shortcuts">
                <IconKeyboard size={18} />
              </Button>
            </Tooltip>
          </Group>
        }
      />

      {list.error ? (
        <Paper>
          <ErrorState error={list.error} onRetry={() => list.refetch()} />
        </Paper>
      ) : list.isPending ? (
        <Loader />
      ) : items.length === 0 && !tx ? (
        <Paper>
          <EmptyState
            icon={<IconChecks size={24} />}
            title={tab === 'SUGGESTED' ? 'All caught up' : 'Nothing here'}
            description={
              tab === 'SUGGESTED'
                ? 'Every suggested payment has been reviewed. New ones appear after the next statement import.'
                : 'Every incoming payment has at least one suggestion or a decision.'
            }
          />
        </Paper>
      ) : (
        <Grid gap="md" align="flex-start">
          <Grid.Col span={{ base: 12, md: 5 }}>
            <Paper p={0} style={{ overflow: 'hidden' }}>
              <ScrollArea.Autosize mah="calc(100vh - 210px)">
                {items.map((item) => (
                  <QueueItem
                    key={item.id}
                    ref={(el) => {
                      if (el) itemRefs.current.set(item.id, el)
                      else itemRefs.current.delete(item.id)
                    }}
                    tx={item}
                    active={item.id === selectedId}
                    onSelect={() => setSelectedId(item.id)}
                  />
                ))}
              </ScrollArea.Autosize>
            </Paper>
          </Grid.Col>

          <Grid.Col span={{ base: 12, md: 7 }}>
            {details.isPending ? (
              <Loader size="sm" />
            ) : details.error ? (
              <ErrorState error={details.error} onRetry={() => details.refetch()} />
            ) : tx ? (
              <Stack>
                <Paper p="md">
                  <Group justify="space-between" align="flex-start" wrap="nowrap">
                    <Stack gap={2}>
                      <Text size="xs" c="dimmed">
                        Booked {formatDate(tx.bookingDate)} · {details.data.importFileName}
                      </Text>
                      <Text fw={600} size="lg">
                        {tx.counterpartyName ?? 'Unknown sender'}
                      </Text>
                      {tx.counterpartyAccount && (
                        <Text size="xs" ff="monospace" c="dimmed">
                          {formatIban(tx.counterpartyAccount)}
                        </Text>
                      )}
                    </Stack>
                    <AmountText amount={tx.amount} currency={tx.currency} fz={24} fw={650} />
                  </Group>
                  <Paper bg="gray.0" withBorder={false} p="xs" mt="sm">
                    <Text size="xs" c="dimmed">
                      Title
                    </Text>
                    <Text size="sm" ff="monospace">
                      {tx.title ?? '–'}
                    </Text>
                  </Paper>
                </Paper>

                {tx.matchStatus !== 'SUGGESTED' && tx.matchStatus !== 'UNMATCHED' && (
                  <Alert color="teal" icon={<IconCheck size={18} />} py="xs">
                    This payment was already decided ({tx.matchStatus === 'MATCHED' ? `matched to ${tx.matchedInvoices.join(', ')}` : 'not an invoice payment'}).
                  </Alert>
                )}

                {pending.map((suggestion, i) => (
                  <SuggestionCard
                    key={suggestion.id}
                    suggestion={suggestion}
                    currency={tx.currency}
                    highlighted={i === 0}
                    actions={
                      <Group gap="xs">
                        <Button size="xs" leftSection={<IconCheck size={14} />} onClick={() => confirm(suggestion)} loading={busy}>
                          Confirm {i === 0 && <Kbd ml={6} size="xs">C</Kbd>}
                        </Button>
                        <Button size="xs" variant="default" leftSection={<IconX size={14} />} onClick={() => reject(suggestion)} disabled={busy}>
                          Reject {i === 0 && <Kbd ml={6} size="xs">R</Kbd>}
                        </Button>
                      </Group>
                    }
                  />
                ))}

                {pending.length === 0 && (tx.matchStatus === 'SUGGESTED' || tx.matchStatus === 'UNMATCHED') && (
                  <Alert color="gray" icon={<IconInfoCircle size={18} />}>
                    No invoice matched this payment automatically. Find the invoice yourself, or mark the payment as not an
                    invoice payment (refund, fee, own transfer).
                  </Alert>
                )}

                {(tx.matchStatus === 'SUGGESTED' || tx.matchStatus === 'UNMATCHED') && (
                  <Paper p="sm">
                    <Group justify="space-between">
                      <Group gap="xs">
                        <Button variant="light" onClick={() => setManualFor(details.data)}>
                          Match manually <Kbd ml={8} size="xs">M</Kbd>
                        </Button>
                        <Button variant="subtle" color="gray" onClick={() => setIgnoreFor(tx)}>
                          Not an invoice <Kbd ml={8} size="xs">I</Kbd>
                        </Button>
                      </Group>
                      <Button variant="subtle" color="gray" onClick={() => move(1)} disabled={index >= items.length - 1}>
                        Skip <Kbd ml={8} size="xs">J</Kbd>
                      </Button>
                    </Group>
                  </Paper>
                )}

                {rejected.length > 0 && (
                  <Stack gap="xs">
                    <Group gap={6}>
                      <Text size="sm" c="dimmed">
                        Rejected earlier
                      </Text>
                      <Badge color="gray" size="sm">
                        {rejected.length}
                      </Badge>
                    </Group>
                    {rejected.map((suggestion) => (
                      <SuggestionCard key={suggestion.id} suggestion={suggestion} currency={tx.currency} />
                    ))}
                  </Stack>
                )}
              </Stack>
            ) : null}
          </Grid.Col>
        </Grid>
      )}

      {manualFor && (
        <ManualMatchModal key={manualFor.transaction.id} details={manualFor} onClose={() => setManualFor(null)} onDone={afterModal} />
      )}
      {ignoreFor && <IgnoreModal key={ignoreFor.id} transaction={ignoreFor} onClose={() => setIgnoreFor(null)} onDone={afterModal} />}
      <ShortcutsModal opened={helpOpened} onClose={help.close} />
    </>
  )
}
