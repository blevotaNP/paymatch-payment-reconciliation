import { Button, Group, Modal, Radio, Stack, Text, Textarea } from '@mantine/core'
import { useState } from 'react'
import { useIgnoreReasons, useReviewAction } from '../../api/review'
import type { IgnoreReason, Transaction } from '../../api/types'
import { notifyError, notifySuccess } from '../../components/notify'
import { formatMoney } from '../../lib/money'

interface IgnoreModalProps {
  transaction: Transaction
  onClose: () => void
  onDone: () => void
}

/** Mounted with key={transaction.id}, so the form starts fresh for every payment. */

export function IgnoreModal({ transaction, onClose, onDone }: IgnoreModalProps) {
  const { data: reasons } = useIgnoreReasons()
  const [reason, setReason] = useState<IgnoreReason>('REFUND')
  const [note, setNote] = useState('')
  const action = useReviewAction()

  const submit = () =>
    action.mutate(
      { transactionId: transaction.id, version: transaction.version, action: { type: 'ignore', reason, note: note.trim() || undefined } },
      {
        onSuccess: () => {
          notifySuccess('Marked as not an invoice payment')
          onDone()
        },
        onError: (e) => notifyError(e, 'Could not update payment'),
      },
    )

  return (
    <Modal opened onClose={onClose} title={<Text fw={600}>Not an invoice payment</Text>} centered>
      <Stack>
        <Text size="sm" c="dimmed">
          {formatMoney(transaction.amount, transaction.currency)}
          {transaction.counterpartyName ? ` from ${transaction.counterpartyName}` : ''} will leave the review queue. You can undo this
          later.
        </Text>
        <Radio.Group value={reason} onChange={(value) => setReason(value as IgnoreReason)} label="Reason">
          <Stack gap="xs" mt={6}>
            {reasons?.map((r) => (
              <Radio key={r.code} value={r.code} label={r.label} />
            ))}
          </Stack>
        </Radio.Group>
        <Textarea label="Note" placeholder="Optional" value={note} onChange={(e) => setNote(e.currentTarget.value)} maxLength={500} autosize />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={submit} loading={action.isPending} data-autofocus>
            Mark as not an invoice
          </Button>
        </Group>
      </Stack>
    </Modal>
  )
}
