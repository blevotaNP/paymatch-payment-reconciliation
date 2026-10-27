import { Alert, Anchor, Button, Group, Modal, Paper, SimpleGrid, Stack, Table, Text, ThemeIcon } from '@mantine/core'
import { Dropzone } from '@mantine/dropzone'
import { IconAlertTriangle, IconCircleCheck, IconFileSpreadsheet, IconUpload, IconX } from '@tabler/icons-react'
import { useImportInvoices } from '../../api/invoices'
import type { InvoiceImportResult } from '../../api/types'

const TEMPLATE = [
  'number;counterparty_name;nip;iban;issue_date;due_date;gross_amount;currency',
  'FV/1/2026;ACME Sp. z o.o.;526-018-15-96;PL61 1090 1014 0000 0712 1981 2874;01.10.2026;15.10.2026;1 230,50;PLN',
].join('\r\n')

function downloadTemplate() {
  const blob = new Blob([TEMPLATE], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'invoices-template.csv'
  link.click()
  URL.revokeObjectURL(url)
}

export function InvoiceImportModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const importInvoices = useImportInvoices()
  const result = importInvoices.data

  const close = () => {
    importInvoices.reset()
    onClose()
  }

  return (
    <Modal opened={opened} onClose={close} title={<Text fw={600}>Import invoices from CSV</Text>} size="lg" centered>
      {result ? (
        <ImportSummary result={result} onDone={close} onAgain={() => importInvoices.reset()} />
      ) : (
        <Stack>
          <Text size="sm" c="dimmed">
            Columns: <code>number</code>, <code>counterparty_name</code>, <code>nip</code>, <code>iban</code>,{' '}
            <code>issue_date</code>, <code>due_date</code>, <code>gross_amount</code>, <code>currency</code>. Separator{' '}
            <code>;</code> or <code>,</code>, UTF-8 or Windows-1250, amounts like <code>1 230,50</code>.{' '}
            <Anchor component="button" size="sm" onClick={downloadTemplate}>
              Download template
            </Anchor>
          </Text>
          {importInvoices.error && (
            <Alert color="red" icon={<IconAlertTriangle size={18} />}>
              {importInvoices.error.message}
            </Alert>
          )}
          <Dropzone
            onDrop={(files) => importInvoices.mutate(files[0])}
            loading={importInvoices.isPending}
            multiple={false}
            maxSize={10 * 1024 * 1024}
            accept={['text/csv', 'application/vnd.ms-excel', 'text/plain']}
          >
            <Group justify="center" gap="lg" mih={150} style={{ pointerEvents: 'none' }}>
              <Dropzone.Accept>
                <IconUpload size={42} color="var(--mantine-color-brand-6)" stroke={1.5} />
              </Dropzone.Accept>
              <Dropzone.Reject>
                <IconX size={42} color="var(--mantine-color-red-6)" stroke={1.5} />
              </Dropzone.Reject>
              <Dropzone.Idle>
                <IconFileSpreadsheet size={42} color="var(--mantine-color-dimmed)" stroke={1.5} />
              </Dropzone.Idle>
              <div>
                <Text fw={500}>Drop a CSV file here or click to choose</Text>
                <Text size="sm" c="dimmed">
                  Up to 10 MB. Invalid rows are skipped and listed after import.
                </Text>
              </div>
            </Group>
          </Dropzone>
        </Stack>
      )}
    </Modal>
  )
}

function ImportSummary({ result, onDone, onAgain }: { result: InvoiceImportResult; onDone: () => void; onAgain: () => void }) {
  return (
    <Stack>
      <Group gap="sm">
        <ThemeIcon color={result.skipped.length ? 'yellow' : 'teal'} variant="light" radius="xl" size="lg">
          {result.skipped.length ? <IconAlertTriangle size={18} /> : <IconCircleCheck size={18} />}
        </ThemeIcon>
        <Text fw={500}>
          {result.imported} of {result.totalRows} rows imported
        </Text>
      </Group>
      <SimpleGrid cols={3}>
        <Stat label="Rows in file" value={result.totalRows} />
        <Stat label="Imported" value={result.imported} color="teal" />
        <Stat label="Skipped" value={result.skipped.length} color={result.skipped.length ? 'red' : undefined} />
      </SimpleGrid>
      {result.skipped.length > 0 && (
        <Paper p={0} mah={260} style={{ overflow: 'auto' }}>
          <Table striped fz="sm" stickyHeader>
            <Table.Thead>
              <Table.Tr>
                <Table.Th w={60}>Line</Table.Th>
                <Table.Th w={140}>Invoice</Table.Th>
                <Table.Th>Reason</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {result.skipped.map((row) => (
                <Table.Tr key={row.line}>
                  <Table.Td className="tabular">{row.line}</Table.Td>
                  <Table.Td>{row.invoiceNumber ?? '–'}</Table.Td>
                  <Table.Td>{row.reason}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Paper>
      )}
      <Text size="xs" c="dimmed">
        Detected encoding: {result.detectedEncoding}
      </Text>
      <Group justify="flex-end">
        <Button variant="default" onClick={onAgain}>
          Import another file
        </Button>
        <Button onClick={onDone}>Done</Button>
      </Group>
    </Stack>
  )
}

function Stat({ label, value, color }: { label: string; value: number; color?: string }) {
  return (
    <Paper p="sm">
      <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
        {label}
      </Text>
      <Text fz={22} fw={650} c={color} className="tabular">
        {value}
      </Text>
    </Paper>
  )
}
