import { Stack, Text, ThemeIcon } from '@mantine/core'
import type { ReactNode } from 'react'

interface EmptyStateProps {
  icon: ReactNode
  title: string
  description?: string
  action?: ReactNode
}

export function EmptyState({ icon, title, description, action }: EmptyStateProps) {
  return (
    <Stack align="center" gap={6} py={48}>
      <ThemeIcon size={48} radius="xl" variant="light" color="gray">
        {icon}
      </ThemeIcon>
      <Text fw={600} mt={4}>
        {title}
      </Text>
      {description && (
        <Text size="sm" c="dimmed" ta="center" maw={380}>
          {description}
        </Text>
      )}
      {action && <div style={{ marginTop: 8 }}>{action}</div>}
    </Stack>
  )
}
