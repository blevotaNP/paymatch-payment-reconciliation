import { Group, Stack, Text, UnstyledButton } from '@mantine/core'
import { forwardRef } from 'react'
import type { Transaction } from '../../api/types'
import { AmountText } from '../../components/AmountText'
import { ConfidenceBadge } from '../../components/MatchBadges'
import { formatDate } from '../../lib/dates'

interface QueueItemProps {
  tx: Transaction
  active: boolean
  onSelect: () => void
}

export const QueueItem = forwardRef<HTMLButtonElement, QueueItemProps>(function QueueItem({ tx, active, onSelect }, ref) {
  return (
    <UnstyledButton
      ref={ref}
      onClick={onSelect}
      p="sm"
      w="100%"
      style={{
        borderLeft: `3px solid ${active ? 'var(--mantine-color-brand-6)' : 'transparent'}`,
        backgroundColor: active ? 'var(--mantine-color-brand-0)' : undefined,
        borderBottom: '1px solid var(--mantine-color-gray-2)',
      }}
    >
      <Stack gap={4}>
        <Group justify="space-between" wrap="nowrap">
          <Text size="sm" fw={600} lineClamp={1}>
            {tx.counterpartyName ?? 'Unknown sender'}
          </Text>
          <AmountText amount={tx.amount} currency={tx.currency} />
        </Group>
        <Text size="xs" c="dimmed" lineClamp={1}>
          {formatDate(tx.bookingDate)} · {tx.title ?? 'no title'}
        </Text>
        {tx.bestSuggestion && (
          <Group gap={6} wrap="nowrap">
            <ConfidenceBadge confidence={tx.bestSuggestion.confidence} />
            <Text size="xs" lineClamp={1}>
              {tx.bestSuggestion.ruleLabel}: {tx.bestSuggestion.invoiceNumbers.join(', ')}
            </Text>
          </Group>
        )}
      </Stack>
    </UnstyledButton>
  )
})
