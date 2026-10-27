import { Badge, Group, Paper, Stack, Table, Text, ThemeIcon } from '@mantine/core'
import {
  IconBuildingBank,
  IconCash,
  IconFileInvoice,
  IconId,
  IconTextCaption,
  IconUser,
  type Icon,
} from '@tabler/icons-react'
import type { ReactNode } from 'react'
import type { MatchReason, Suggestion } from '../../api/types'
import { ConfidenceBadge } from '../../components/MatchBadges'
import { MoneyText } from '../../components/MoneyText'
import { formatDate, formatDateTime } from '../../lib/dates'

const REASON_ICONS: Record<MatchReason['field'], Icon> = {
  title: IconTextCaption,
  amount: IconCash,
  account: IconBuildingBank,
  nip: IconId,
  name: IconUser,
  invoice: IconFileInvoice,
}

interface SuggestionCardProps {
  suggestion: Suggestion
  currency: string
  /** Buttons shown at the bottom, e.g. confirm and reject in the review queue. */
  actions?: ReactNode
  highlighted?: boolean
}

export function SuggestionCard({ suggestion, currency, actions, highlighted }: SuggestionCardProps) {
  const decided = suggestion.status !== 'PENDING'
  return (
    <Paper
      p="sm"
      style={{
        opacity: suggestion.status === 'REJECTED' ? 0.6 : 1,
        borderColor: highlighted ? 'var(--mantine-color-brand-4)' : undefined,
        boxShadow: highlighted ? '0 0 0 1px var(--mantine-color-brand-4)' : undefined,
      }}
    >
      <Stack gap="xs">
        <Group justify="space-between" wrap="nowrap">
          <div>
            <Text fw={600} size="sm">
              {suggestion.ruleLabel}
            </Text>
            <Text size="xs" c="dimmed">
              {suggestion.ruleDescription}
            </Text>
          </div>
          <Group gap={6} wrap="nowrap" style={{ flexShrink: 0 }}>
            {decided && (
              <Badge color={suggestion.status === 'CONFIRMED' ? 'teal' : 'red'} variant="light">
                {suggestion.status === 'CONFIRMED' ? 'Confirmed' : 'Rejected'}
              </Badge>
            )}
            <ConfidenceBadge confidence={suggestion.confidence} score={suggestion.score} />
          </Group>
        </Group>

        <Table fz="sm" verticalSpacing={4} withRowBorders={false}>
          <Table.Tbody>
            {suggestion.items.map((item) => (
              <Table.Tr key={item.invoiceId}>
                <Table.Td>
                  <Text size="sm" fw={500}>
                    {item.invoiceNumber}
                  </Text>
                  <Text size="xs" c="dimmed">
                    {item.counterpartyName} · due {formatDate(item.dueDate)}
                  </Text>
                </Table.Td>
                <Table.Td ta="right">
                  <MoneyText amount={item.amount} currency={currency} fw={500} />
                  <Text size="xs" c="dimmed">
                    of <MoneyText amount={item.grossAmount} currency={currency} size="xs" />
                  </Text>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>

        <Stack gap={4}>
          {suggestion.reasons.map((reason) => {
            const ReasonIcon = REASON_ICONS[reason.field] ?? IconTextCaption
            return (
              <Group key={reason.message} gap={8} wrap="nowrap" align="flex-start">
                <ThemeIcon size={20} variant="light" color="gray" radius="sm">
                  <ReasonIcon size={13} />
                </ThemeIcon>
                <Text size="xs" style={{ flex: 1 }}>
                  {reason.message}
                </Text>
              </Group>
            )
          })}
        </Stack>

        {actions}

        {decided && suggestion.decidedAt && (
          <Text size="xs" c="dimmed">
            {suggestion.status === 'CONFIRMED' ? 'Confirmed' : 'Rejected'} {formatDateTime(suggestion.decidedAt)}
            {suggestion.decidedBy ? ` by ${suggestion.decidedBy}` : ' automatically'}
          </Text>
        )}
      </Stack>
    </Paper>
  )
}
