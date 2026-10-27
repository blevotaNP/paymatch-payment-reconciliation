import { ActionIcon, Alert, Button, Group, Modal, Stack, Text, Textarea, TextInput } from '@mantine/core'
import { IconAlertTriangle, IconPlus, IconTrash } from '@tabler/icons-react'
import { useState } from 'react'
import { useReviewAction } from '../../api/review'
import type { TransactionDetails } from '../../api/types'
import { notifyError, notifySuccess } from '../../components/notify'
import { amountToInput, formatMoney, fromCents, normalizeAmountInput, toCents } from '../../lib/money'
import { InvoicePicker, type PickedInvoice } from './InvoicePicker'

interface Row {
  key: number
  invoice: PickedInvoice | null
  amount: string
}

interface ManualMatchModalProps {
  details: TransactionDetails
  onClose: () => void
  onDone: () => void
}

let nextKey = 1

function initialRows(details: TransactionDetails): Row[] {
  // start from the best suggestion if there is one, so a combined payment only needs a check and a click
  const top = details.suggestions.find((s) => s.status === 'PENDING')
  if (!top) {
    return [{ key: nextKey++, invoice: null, amount: amountToInput(details.transaction.amount) }]
  }
  return top.items.map((item) => ({
    key: nextKey++,
    invoice: {
      id: item.invoiceId,
      number: item.invoiceNumber,
      counterpartyName: item.counterpartyName,
      remainingAmount: item.remainingAmount,
      currency: details.transaction.currency,
    },
    amount: amountToInput(item.amount),
  }))
}

/**
 * Pick any open invoice, or split the payment across several. Sums are done in integer cents.
 * Mounted with key={transaction.id}, so the rows start fresh for every payment.
 */
export function ManualMatchModal({ details, onClose, onDone }: ManualMatchModalProps) {
  const [rows, setRows] = useState<Row[]>(() => initialRows(details))
  const [note, setNote] = useState('')
  const action = useReviewAction()
  const tx = details.transaction

  const totalCents = toCents(tx.amount)
  const rowCents = rows.map((row) => {
    const normalized = normalizeAmountInput(row.amount)
    return normalized ? toCents(normalized) : null
  })
  const allocatedCents = rowCents.reduce<bigint>((sum, cents) => sum + (cents ?? 0n), 0n)
  const leftCents = totalCents - allocatedCents
  const valid = rows.every((row, i) => row.invoice && rowCents[i] != null && rowCents[i]! > 0n) && leftCents === 0n

  const update = (key: number, patch: Partial<Row>) =>
    setRows((current) => current.map((row) => (row.key === key ? { ...row, ...patch } : row)))

  const addRow = () =>
    setRows((current) => [
      ...current,
      { key: nextKey++, invoice: null, amount: leftCents > 0n ? amountToInput(fromCents(leftCents)) : '' },
    ])

  const submit = () =>
    action.mutate(
      {
        transactionId: tx.id,
        version: tx.version,
        action: {
          type: 'allocate',
          parts: rows.map((row) => ({ invoiceId: row.invoice!.id, amount: normalizeAmountInput(row.amount)! })),
          note: note.trim() || undefined,
        },
      },
      {
        onSuccess: () => {
          notifySuccess(rows.length > 1 ? `Payment split between ${rows.length} invoices` : `Matched to ${rows[0].invoice!.number}`)
          onDone()
        },
        onError: (e) => notifyError(e, 'Could not match payment'),
      },
    )

  return (
    <Modal opened onClose={onClose} size="xl" title={<Text fw={600}>Match manually</Text>}>
      <Stack>
        <Text size="sm" c="dimmed">
          Payment of <b>{formatMoney(tx.amount, tx.currency)}</b>
          {tx.counterpartyName ? ` from ${tx.counterpartyName}` : ''}. Add more invoices to split it.
        </Text>

        {rows.map((row, i) => {
          const cents = rowCents[i]
          const overpay = row.invoice && cents != null ? cents - toCents(row.invoice.remainingAmount) : 0n
          return (
            <Stack key={row.key} gap={4}>
              <Group gap="sm" align="flex-start" wrap="nowrap">
                <InvoicePicker
                  value={row.invoice}
                  onChange={(invoice) => update(row.key, { invoice })}
                  excludeIds={rows.filter((r) => r.invoice).map((r) => r.invoice!.id)}
                />
                <TextInput
                  w={150}
                  value={row.amount}
                  onChange={(e) => update(row.key, { amount: e.currentTarget.value })}
                  error={row.amount && cents == null ? 'Invalid amount' : undefined}
                  styles={{ input: { textAlign: 'right' } }}
                  className="tabular"
                  aria-label="Amount"
                />
                <ActionIcon
                  variant="subtle"
                  color="gray"
                  size={36}
                  disabled={rows.length === 1}
                  onClick={() => setRows((current) => current.filter((r) => r.key !== row.key))}
                  aria-label="Remove line"
                >
                  <IconTrash size={16} />
                </ActionIcon>
              </Group>
              {overpay > 0n && (
                <Text size="xs" c="orange.8">
                  This pays {formatMoney(fromCents(overpay), tx.currency)} more than is left on {row.invoice!.number}; the
                  invoice will show as overpaid.
                </Text>
              )}
            </Stack>
          )
        })}

        <Group justify="space-between">
          <Button variant="subtle" leftSection={<IconPlus size={16} />} onClick={addRow} disabled={rows.length >= 20}>
            Add invoice
          </Button>
          <Text size="sm" className="tabular" c={leftCents === 0n ? 'teal.8' : 'red.7'} fw={500}>
            {leftCents === 0n
              ? 'Fully allocated'
              : leftCents > 0n
                ? `${formatMoney(fromCents(leftCents), tx.currency)} left to allocate`
                : `${formatMoney(fromCents(-leftCents), tx.currency)} too much`}
          </Text>
        </Group>

        <Textarea
          label="Note"
          placeholder="Optional, e.g. 'client confirmed by e-mail'"
          value={note}
          onChange={(e) => setNote(e.currentTarget.value)}
          maxLength={500}
          autosize
          minRows={1}
        />

        {action.error && (
          <Alert color="red" icon={<IconAlertTriangle size={18} />} py="xs">
            {action.error.message}
          </Alert>
        )}

        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={submit} disabled={!valid} loading={action.isPending}>
            Confirm match
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}
