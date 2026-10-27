import { Badge, Group, Text, Tooltip } from '@mantine/core'
import type { Confidence, Transaction } from '../api/types'

const CONFIDENCE: Record<Confidence, { label: string; color: string }> = {
  HIGH: { label: 'High', color: 'teal' },
  MEDIUM: { label: 'Medium', color: 'yellow' },
  LOW: { label: 'Low', color: 'gray' },
}

export function ConfidenceBadge({ confidence, score }: { confidence: Confidence; score?: number }) {
  const { label, color } = CONFIDENCE[confidence]
  return (
    <Tooltip label={score != null ? `Score ${score} / 100` : undefined} disabled={score == null}>
      <Badge color={color} variant="dot">
        {label}
      </Badge>
    </Tooltip>
  )
}

/** Compact match state for table rows. */
export function MatchCell({ tx }: { tx: Transaction }) {
  if (tx.amount.startsWith('-')) {
    return (
      <Text size="xs" c="dimmed">
        outgoing
      </Text>
    )
  }
  switch (tx.matchStatus) {
    case 'MATCHED':
      return (
        <Group gap={6} wrap="nowrap">
          <Badge color="teal">
            Matched
          </Badge>
          <Text size="xs" c="dimmed" lineClamp={1}>
            {tx.matchedInvoices.join(', ')}
          </Text>
        </Group>
      )
    case 'SUGGESTED':
      return (
        <Group gap={6} wrap="nowrap">
          <Badge color="blue">
            To review
          </Badge>
          {tx.bestSuggestion && (
            <Text size="xs" c="dimmed" lineClamp={1}>
              {tx.bestSuggestion.ruleLabel}
            </Text>
          )}
        </Group>
      )
    case 'IGNORED':
      return <Badge color="gray">Not an invoice</Badge>
    default:
      return (
        <Badge color="orange" variant="outline">
          Unmatched
        </Badge>
      )
  }
}
