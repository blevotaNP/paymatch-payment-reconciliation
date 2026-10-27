import { Group, Text, ThemeIcon } from '@mantine/core'
import { IconCheck } from '@tabler/icons-react'

export function Logo({ size = 'md' }: { size?: 'md' | 'lg' }) {
  const large = size === 'lg'
  return (
    <Group gap={8} wrap="nowrap">
      <ThemeIcon size={large ? 36 : 28} radius="md">
        <IconCheck size={large ? 22 : 18} stroke={3} />
      </ThemeIcon>
      <Text fw={700} fz={large ? 22 : 17} style={{ letterSpacing: '-0.02em' }}>
        PayMatch
      </Text>
    </Group>
  )
}
