import { Button, Group, Menu, Paper, Progress, SegmentedControl, SimpleGrid, Stack, Table, Text, Tooltip } from '@mantine/core'
import { DatePickerInput, type DatesRangeValue } from '@mantine/dates'
import { IconChevronDown, IconChevronRight, IconDownload, IconReportMoney } from '@tabler/icons-react'
import { DataTable } from 'mantine-datatable'
import { useState } from 'react'
import { download, useAgingReport, type ExportFormat } from '../../api/reports'
import type { AgingReport } from '../../api/types'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { MoneyText } from '../../components/MoneyText'
import { PageHeader } from '../../components/PageHeader'
import { AGING_BUCKETS } from '../../lib/aging'
import { formatDate } from '../../lib/dates'
import { formatMoney, isZero, toChartNumber } from '../../lib/money'
import { formatNip } from '../../lib/validation'

type Row = AgingReport['rows'][number]

export function ReportsPage() {
  return (
    <>
      <PageHeader title="Reports" description="Receivables aging and exports for your accounting software or spreadsheet." />
      <Stack gap="lg">
        <AgingSection />
        <ExportSection />
      </Stack>
    </>
  )
}

function AgingSection() {
  const { data, isPending, error, refetch } = useAgingReport()
  const [expanded, setExpanded] = useState<number[]>([])

  return (
    <Paper p={0}>
      <Group justify="space-between" p="md" pb="sm">
        <div>
          <Text fw={600}>Receivables aging</Text>
          <Text size="sm" c="dimmed">
            Open invoices by how long they are overdue{data ? `, as of ${formatDate(data.asOf)}` : ''}. Click a row to see the
            invoices.
          </Text>
        </div>
        <ExportMenu label="Export" onExport={(format) => download('/api/exports/aging', { format })} />
      </Group>

      {error ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : data && data.rows.length === 0 && !isPending ? (
        <EmptyState icon={<IconReportMoney size={24} />} title="No open receivables" description="All invoices are paid." />
      ) : (
        <>
          {data && <BucketBar report={data} />}
          <DataTable<Row>
            minHeight={200}
            fetching={isPending}
            verticalSpacing="sm"
            horizontalSpacing="md"
            highlightOnHover
            idAccessor="counterpartyId"
            records={data?.rows ?? []}
            rowExpansion={{
              allowMultiple: true,
              expanded: { recordIds: expanded, onRecordIdsChange: setExpanded },
              content: ({ record }) => <InvoiceLines row={record} />,
            }}
            columns={[
              {
                accessor: 'counterpartyName',
                title: 'Counterparty',
                footer: (
                  <Text size="sm" fw={700}>
                    Total {data?.currency}
                  </Text>
                ),
                render: (row) => (
                  <Group gap={6} wrap="nowrap">
                    {expanded.includes(row.counterpartyId) ? <IconChevronDown size={14} /> : <IconChevronRight size={14} />}
                    <div>
                      <Text size="sm" fw={500}>
                        {row.counterpartyName}
                      </Text>
                      {row.nip && (
                        <Text size="xs" c="dimmed" className="tabular">
                          NIP {formatNip(row.nip)}
                        </Text>
                      )}
                    </div>
                  </Group>
                ),
              },
              ...AGING_BUCKETS.map((bucket) => ({
                accessor: `buckets.${bucket.key}`,
                title: bucket.label,
                textAlign: 'right' as const,
                footer: data && <MoneyText amount={data.totals[bucket.key]} fw={700} />,
                render: (row: Row) => (
                  <MoneyText
                    amount={row.buckets[bucket.key]}
                    c={isZero(row.buckets[bucket.key]) ? 'dimmed' : bucket.key === 'NOT_DUE' ? undefined : 'red.7'}
                  />
                ),
              })),
              {
                accessor: 'total',
                title: 'Total',
                textAlign: 'right',
                footer: data && <MoneyText amount={data.total} fw={700} />,
                render: (row) => <MoneyText amount={row.total} fw={600} />,
              },
            ]}
          />
        </>
      )}
    </Paper>
  )
}

function BucketBar({ report }: { report: AgingReport }) {
  const total = toChartNumber(report.total)
  if (total === 0) return null
  return (
    <Stack gap={6} px="md" pb="md">
      <Progress.Root size={18}>
        {AGING_BUCKETS.map((bucket) => {
          const share = (toChartNumber(report.totals[bucket.key]) / total) * 100
          return (
            <Tooltip key={bucket.key} label={`${bucket.label}: ${formatMoney(report.totals[bucket.key], report.currency)}`}>
              <Progress.Section value={share} color={bucket.color} />
            </Tooltip>
          )
        })}
      </Progress.Root>
      <Group gap="md">
        {AGING_BUCKETS.map((bucket) => (
          <Group key={bucket.key} gap={6}>
            <div
              style={{
                width: 10,
                height: 10,
                borderRadius: 3,
                background: `var(--mantine-color-${bucket.color.replace('.', '-')})`,
              }}
            />
            <Text size="xs" c="dimmed">
              {bucket.label}
            </Text>
          </Group>
        ))}
      </Group>
    </Stack>
  )
}

function InvoiceLines({ row }: { row: Row }) {
  return (
    <Table fz="sm" ml={36} mr="md" my="xs" w="auto" withRowBorders={false} verticalSpacing={4}>
      <Table.Thead>
        <Table.Tr>
          <Table.Th>Invoice</Table.Th>
          <Table.Th>Due</Table.Th>
          <Table.Th ta="right">Days overdue</Table.Th>
          <Table.Th ta="right">Gross</Table.Th>
          <Table.Th ta="right">Left to pay</Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {row.invoices.map((invoice) => (
          <Table.Tr key={invoice.invoiceId}>
            <Table.Td fw={500}>{invoice.number}</Table.Td>
            <Table.Td className="tabular">{formatDate(invoice.dueDate)}</Table.Td>
            <Table.Td ta="right" className="tabular" c={invoice.daysOverdue > 0 ? 'red.7' : 'dimmed'}>
              {invoice.daysOverdue > 0 ? invoice.daysOverdue : '–'}
            </Table.Td>
            <Table.Td ta="right">
              <MoneyText amount={invoice.grossAmount} c="dimmed" />
            </Table.Td>
            <Table.Td ta="right">
              <MoneyText amount={invoice.remainingAmount} fw={500} />
            </Table.Td>
          </Table.Tr>
        ))}
      </Table.Tbody>
    </Table>
  )
}

function ExportSection() {
  const [range, setRange] = useState<DatesRangeValue<string>>([null, null])
  const [format, setFormat] = useState<ExportFormat>('XLSX')

  return (
    <SimpleGrid cols={{ base: 1, md: 2 }}>
      <Paper p="md">
        <Text fw={600}>Reconciliation result</Text>
        <Text size="sm" c="dimmed" mb="md">
          Every incoming payment with the invoices it paid, who matched it and when. One row per invoice, so split payments
          add up. The Excel file also has a sheet with all invoices.
        </Text>
        <Group align="flex-end" gap="sm">
          <DatePickerInput
            type="range"
            label="Booking date"
            placeholder="All dates"
            valueFormat="DD.MM.YYYY"
            clearable
            value={range}
            onChange={setRange}
            w={250}
          />
          <SegmentedControl value={format} onChange={(value) => setFormat(value as ExportFormat)} data={['XLSX', 'CSV']} />
          <Button
            leftSection={<IconDownload size={16} />}
            onClick={() => download('/api/exports/reconciliation', { format, from: range[0], to: range[1] })}
          >
            Download
          </Button>
        </Group>
      </Paper>
      <Paper p="md">
        <Text fw={600}>Invoices</Text>
        <Text size="sm" c="dimmed" mb="md">
          All invoices with paid and remaining amounts, status and days overdue. CSV uses ";" and decimal commas, so Polish Excel
          opens it directly.
        </Text>
        <ExportMenu label="Download invoices" onExport={(f) => download('/api/exports/invoices', { format: f })} />
      </Paper>
    </SimpleGrid>
  )
}

function ExportMenu({ label, onExport }: { label: string; onExport: (format: ExportFormat) => void }) {
  return (
    <Menu position="bottom-end">
      <Menu.Target>
        <Button variant="default" leftSection={<IconDownload size={16} />} rightSection={<IconChevronDown size={14} />}>
          {label}
        </Button>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Item onClick={() => onExport('XLSX')}>Excel (.xlsx)</Menu.Item>
        <Menu.Item onClick={() => onExport('CSV')}>CSV (.csv)</Menu.Item>
      </Menu.Dropdown>
    </Menu>
  )
}
