import { Button, Stack, Text, ThemeIcon } from '@mantine/core'
import { IconAlertTriangle } from '@tabler/icons-react'

interface ErrorStateProps {
  error: unknown
  onRetry?: () => void
}

export function ErrorState({ error, onRetry }: ErrorStateProps) {
  const message = error instanceof Error ? error.message : 'Unexpected error'
  return (
    <Stack align="center" gap="xs" py="xl">
      <ThemeIcon size={44} radius="xl" color="red" variant="light">
        <IconAlertTriangle size={22} />
      </ThemeIcon>
      <Text fw={600}>Could not load data</Text>
      <Text size="sm" c="dimmed" ta="center" maw={360}>
        {message}
      </Text>
      {onRetry && (
        <Button variant="default" size="xs" onClick={onRetry}>
          Try again
        </Button>
      )}
    </Stack>
  )
}
