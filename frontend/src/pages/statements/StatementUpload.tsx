import { Alert, Group, Paper, Select, Stack, Text } from '@mantine/core'
import { Dropzone } from '@mantine/dropzone'
import { IconAlertTriangle, IconFileUpload, IconUpload, IconX } from '@tabler/icons-react'
import { useState } from 'react'
import { useBankFormats, useUploadStatement } from '../../api/statements'
import type { BankFormat, StatementImport } from '../../api/types'

export function StatementUpload({ onImported }: { onImported: (result: StatementImport) => void }) {
  const [format, setFormat] = useState<string>('AUTO')
  const { data: formats } = useBankFormats()
  const upload = useUploadStatement()

  const formatOptions = [
    { value: 'AUTO', label: 'Detect automatically' },
    ...(formats ?? []).map((f) => ({ value: f.code, label: f.label })),
  ]
  const selected = formats?.find((f) => f.code === format)

  return (
    <Paper p="md">
      <Stack gap="sm">
        <Group justify="space-between" align="flex-end" wrap="wrap">
          <div>
            <Text fw={600}>Import a statement</Text>
            <Text size="sm" c="dimmed">
              Re-importing a file or an overlapping period is safe: transactions that are already stored are skipped.
            </Text>
          </div>
          <Select
            label="Bank format"
            data={formatOptions}
            value={format}
            onChange={(value) => setFormat(value ?? 'AUTO')}
            allowDeselect={false}
            w={240}
            description={selected?.description}
          />
        </Group>

        {upload.error && (
          <Alert color="red" icon={<IconAlertTriangle size={18} />} withCloseButton onClose={() => upload.reset()}>
            {upload.error.message}
          </Alert>
        )}

        <Dropzone
          onDrop={(files) =>
            upload.mutate(
              { file: files[0], format: format === 'AUTO' ? null : (format as BankFormat) },
              { onSuccess: onImported },
            )
          }
          loading={upload.isPending}
          multiple={false}
          maxSize={10 * 1024 * 1024}
        >
          <Group justify="center" gap="lg" mih={120} style={{ pointerEvents: 'none' }}>
            <Dropzone.Accept>
              <IconUpload size={40} color="var(--mantine-color-brand-6)" stroke={1.5} />
            </Dropzone.Accept>
            <Dropzone.Reject>
              <IconX size={40} color="var(--mantine-color-red-6)" stroke={1.5} />
            </Dropzone.Reject>
            <Dropzone.Idle>
              <IconFileUpload size={40} color="var(--mantine-color-dimmed)" stroke={1.5} />
            </Dropzone.Idle>
            <div>
              <Text fw={500}>Drop a bank statement here or click to choose</Text>
              <Text size="sm" c="dimmed">
                MT940 (.sta, .mt940, .txt) or CSV export from PKO BP, mBank or ING · up to 10 MB
              </Text>
            </div>
          </Group>
        </Dropzone>
      </Stack>
    </Paper>
  )
}
