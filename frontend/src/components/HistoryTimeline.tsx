import { Loader, Text, Timeline } from '@mantine/core'
import type { AuditEvent } from '../api/types'
import { AUDIT_ACTIONS } from '../lib/audit'
import { formatDateTime } from '../lib/dates'

export function HistoryTimeline({ events, loading }: { events: AuditEvent[] | undefined; loading?: boolean }) {
  if (loading) return <Loader size="sm" />
  if (!events?.length) {
    return (
      <Text size="sm" c="dimmed">
        No history yet.
      </Text>
    )
  }
  return (
    <Timeline bulletSize={12} lineWidth={2}>
      {events.map((event) => (
        <Timeline.Item key={event.id} color={AUDIT_ACTIONS[event.action].color} title={<Text size="sm" fw={500}>{AUDIT_ACTIONS[event.action].label}</Text>}>
          <Text size="sm">{event.summary}</Text>
          <Text size="xs" c="dimmed" mt={2}>
            {formatDateTime(event.occurredAt)} · {event.user ?? 'System'}
          </Text>
        </Timeline.Item>
      ))}
    </Timeline>
  )
}
