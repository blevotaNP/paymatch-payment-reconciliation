import { Accordion, Alert, Badge, Group, Paper, SimpleGrid, Stack, Table, Text } from '@mantine/core'
import { IconAlertTriangle } from '@tabler/icons-react'
import type { StatementImport } from '../../api/types'
import { StatCard } from '../../components/StatCard'
import { formatDate, formatDateTime } from '../../lib/dates'
import { formatSignedMoney } from '../../lib/money'
import { formatIban } from '../../lib/validation'

export function ImportSummary({ result }: { result: StatementImport }) {
  const period = result.periodFrom ? `${formatDate(result.periodFrom)} – ${formatDate(result.periodTo)}` : '–'

  return (
    <Stack>
      <Group gap="xs">
        <Badge variant="outline" color="gray">
          {result.bankLabel}
        </Badge>
        <Text size="sm" c="dimmed">
          {result.fileName}
        </Text>
      </Group>

      <SimpleGrid cols={{ base: 2, sm: 4 }}>
        <StatCard label="Rows in file" value={result.totalRows} />
        <StatCard label="New" value={result.importedCount} color="teal" />
        <StatCard label="Duplicates" value={result.duplicateCount} color={result.duplicateCount ? 'blue' : undefined} hint="already imported" />
        <StatCard label="Skipped" value={result.skippedCount} color={result.skippedCount ? 'red' : undefined} hint="could not be read" />
      </SimpleGrid>

      <Paper p="sm">
        <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="xs">
          <Detail label="Account" value={result.accountIban ? formatIban(result.accountIban) : 'not in file'} mono={!!result.accountIban} />
          <Detail label="Period" value={period} />
          <Detail label="Encoding" value={result.encoding} />
          <Detail label="Imported" value={`${formatDateTime(result.importedAt)} by ${result.importedBy}`} />
        </SimpleGrid>
      </Paper>

      {result.warnings.map((warning) => (
        <Alert key={warning} color="yellow" icon={<IconAlertTriangle size={18} />} py="xs">
          {warning}
        </Alert>
      ))}

      {(result.skippedRows.length > 0 || (result.duplicates?.length ?? 0) > 0) && (
        <Accordion variant="separated" defaultValue={result.skippedRows.length ? 'skipped' : null}>
          {result.skippedRows.length > 0 && (
            <Accordion.Item value="skipped">
              <Accordion.Control>
                <Text size="sm" fw={500}>
                  Skipped rows ({result.skippedRows.length})
                </Text>
              </Accordion.Control>
              <Accordion.Panel>
                <Table fz="sm" striped>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th w={60}>Line</Table.Th>
                      <Table.Th>Reason</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {result.skippedRows.map((row) => (
                      <Table.Tr key={row.line}>
                        <Table.Td className="tabular">{row.line}</Table.Td>
                        <Table.Td>
                          <Text size="sm">{row.reason}</Text>
                          {row.content && (
                            <Text size="xs" c="dimmed" ff="monospace" lineClamp={1}>
                              {row.content}
                            </Text>
                          )}
                        </Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Accordion.Panel>
            </Accordion.Item>
          )}
          {result.duplicates && result.duplicates.length > 0 && (
            <Accordion.Item value="duplicates">
              <Accordion.Control>
                <Text size="sm" fw={500}>
                  Duplicates ({result.duplicateCount})
                </Text>
              </Accordion.Control>
              <Accordion.Panel>
                <Table fz="sm" striped>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th w={60}>Line</Table.Th>
                      <Table.Th w={100}>Date</Table.Th>
                      <Table.Th>Title</Table.Th>
                      <Table.Th ta="right">Amount</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {result.duplicates.map((row) => (
                      <Table.Tr key={row.line}>
                        <Table.Td className="tabular">{row.line}</Table.Td>
                        <Table.Td className="tabular">{formatDate(row.bookingDate)}</Table.Td>
                        <Table.Td>
                          <Text size="sm" lineClamp={1}>
                            {row.title ?? '–'}
                          </Text>
                        </Table.Td>
                        <Table.Td ta="right" className="tabular">
                          {formatSignedMoney(row.amount)}
                        </Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Accordion.Panel>
            </Accordion.Item>
          )}
        </Accordion>
      )}
    </Stack>
  )
}

function Detail({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      <Text size="sm" ff={mono ? 'monospace' : undefined}>
        {value}
      </Text>
    </div>
  )
}
