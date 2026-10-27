import { Group, Kbd, Modal, Stack, Text } from '@mantine/core'

const SHORTCUTS: [string[], string][] = [
  [['J', '↓'], 'Next payment'],
  [['K', '↑'], 'Previous payment'],
  [['C', 'Enter'], 'Confirm the top suggestion'],
  [['R'], 'Reject the top suggestion'],
  [['M'], 'Match manually / split'],
  [['I'], 'Not an invoice payment'],
  [['?'], 'Show this help'],
]

export function ShortcutsModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  return (
    <Modal opened={opened} onClose={onClose} title={<Text fw={600}>Keyboard shortcuts</Text>} centered size="sm">
      <Stack gap="xs">
        {SHORTCUTS.map(([keys, label]) => (
          <Group key={label} justify="space-between">
            <Text size="sm">{label}</Text>
            <Group gap={4}>
              {keys.map((key) => (
                <Kbd key={key}>{key}</Kbd>
              ))}
            </Group>
          </Group>
        ))}
      </Stack>
    </Modal>
  )
}
