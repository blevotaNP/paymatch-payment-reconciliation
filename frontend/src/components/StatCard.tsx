import { Paper, Text } from '@mantine/core'
import type { ReactNode } from 'react'

export function StatCard({ label, value, color, hint }: { label: string; value: ReactNode; color?: string; hint?: string }) {
  return (
    <Paper p="sm">
      <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
        {label}
      </Text>
      <Text fz={22} fw={650} c={color} className="tabular">
        {value}
      </Text>
      {hint && (
        <Text size="xs" c="dimmed">
          {hint}
        </Text>
      )}
    </Paper>
  )
}
