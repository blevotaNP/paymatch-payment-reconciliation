import { BarChart } from '@mantine/charts'
import { Anchor, Grid, Group, Loader, Paper, Progress, SimpleGrid, Stack, Table, Text, ThemeIcon, UnstyledButton } from '@mantine/core'
import { IconAlertTriangle, IconArrowsExchange, IconChecklist, IconFileInvoice, type Icon } from '@tabler/icons-react'
import { Link, useNavigate } from 'react-router'
import { useDashboard } from '../../api/reports'
import { ErrorState } from '../../components/ErrorState'
import { HistoryTimeline } from '../../components/HistoryTimeline'
import { MoneyText } from '../../components/MoneyText'
import { PageHeader } from '../../components/PageHeader'
import { formatMoney, formatMoneyCompact, toChartNumber } from '../../lib/money'

const MONTH_NAMES = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

export function DashboardPage() {
  const { data, isPending, error, refetch } = useDashboard()

  if (isPending) return <Loader />
  if (error) return <ErrorState error={error} onRetry={() => refetch()} />

  const { matching } = data
  const decided = matching.autoMatched + matching.manualMatched + matching.ignored
  const percent = (part: number) => (matching.incoming === 0 ? 0 : Math.round((part / matching.incoming) * 100))
  const chartData = data.months.map((m) => ({
    month: MONTH_NAMES[Number(m.month.slice(5)) - 1],
    Invoiced: toChartNumber(m.invoiced),
    Received: toChartNumber(m.received),
  }))

  return (
    <>
      <PageHeader title="Dashboard" description="Where the money stands today. Amounts in PLN." />

      <SimpleGrid cols={{ base: 1, xs: 2, lg: 4 }} mb="md">
        <Tile
          icon={IconChecklist}
          color="blue"
          label="Payments to review"
          value={String(data.toReview)}
          hint={data.toReview ? 'suggestions waiting for a decision' : 'nothing waiting'}
          to="/review"
        />
        <Tile
          icon={IconArrowsExchange}
          color="orange"
          label="Unmatched payments"
          value={formatMoney(data.unmatchedPayments.amount)}
          hint={`${data.unmatchedPayments.count} incoming transfers not linked to invoices`}
          to="/transactions?matchStatus=UNMATCHED"
        />
        <Tile
          icon={IconFileInvoice}
          color="brand"
          label="Open receivables"
          value={formatMoney(data.openInvoices.amount)}
          hint={`${data.openInvoices.count} unpaid or partly paid invoices`}
          to="/invoices"
        />
        <Tile
          icon={IconAlertTriangle}
          color="red"
          label="Overdue"
          value={formatMoney(data.overdueInvoices.amount)}
          hint={`${data.overdueInvoices.count} invoices past the due date`}
          to="/invoices?overdue=true"
          danger={data.overdueInvoices.count > 0}
        />
      </SimpleGrid>

      <Grid gap="md">
        <Grid.Col span={{ base: 12, lg: 8 }}>
          <Paper p="md" h="100%">
            <Text fw={600}>Invoiced vs received</Text>
            <Text size="sm" c="dimmed" mb="md">
              Last 6 months, by invoice issue date and payment booking date
            </Text>
            <BarChart
              h={260}
              data={chartData}
              dataKey="month"
              series={[
                { name: 'Invoiced', color: 'brand.3' },
                { name: 'Received', color: 'teal.6' },
              ]}
              valueFormatter={formatMoneyCompact}
              yAxisProps={{ width: 84 }}
              withLegend
              legendProps={{ verticalAlign: 'top', height: 36 }}
              gridAxis="y"
              tickLine="none"
            />
          </Paper>
        </Grid.Col>

        <Grid.Col span={{ base: 12, lg: 4 }}>
          <Paper p="md" h="100%">
            <Text fw={600}>Matching</Text>
            <Text size="sm" c="dimmed" mb="md">
              {matching.incoming} incoming payments in total
            </Text>
            <Text fz={36} fw={700} lh={1} className="tabular">
              {percent(matching.autoMatched)}%
            </Text>
            <Text size="sm" c="dimmed" mb="md">
              matched automatically, without anyone looking
            </Text>
            <Progress.Root size={14} mb="md">
              <Progress.Section value={percent(matching.autoMatched)} color="teal.6" />
              <Progress.Section value={percent(matching.manualMatched)} color="blue.6" />
              <Progress.Section value={percent(matching.ignored)} color="gray.5" />
              <Progress.Section value={percent(matching.open)} color="orange.4" />
            </Progress.Root>
            <Stack gap={6}>
              <Legend color="teal.6" label="Automatically" value={matching.autoMatched} />
              <Legend color="blue.6" label="By a person" value={matching.manualMatched} />
              <Legend color="gray.5" label="Not an invoice payment" value={matching.ignored} />
              <Legend color="orange.4" label="Still open" value={matching.open} />
            </Stack>
            {matching.incoming > 0 && (
              <Text size="xs" c="dimmed" mt="md">
                {percent(decided)}% of incoming payments are reconciled.
              </Text>
            )}
          </Paper>
        </Grid.Col>

        <Grid.Col span={{ base: 12, lg: 6 }}>
          <Paper p="md" h="100%">
            <Group justify="space-between" mb="sm">
              <Text fw={600}>Most overdue counterparties</Text>
              <Anchor component={Link} to="/reports" size="sm">
                Aging report
              </Anchor>
            </Group>
            {data.topOverdue.length === 0 ? (
              <Text size="sm" c="dimmed">
                No overdue invoices.
              </Text>
            ) : (
              <Table.ScrollContainer minWidth={420}>
                <Table fz="sm" verticalSpacing="xs">
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>Counterparty</Table.Th>
                      <Table.Th ta="right">Invoices</Table.Th>
                      <Table.Th ta="right">Oldest</Table.Th>
                      <Table.Th ta="right">Overdue</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {data.topOverdue.map((row) => (
                      <Table.Tr key={row.counterpartyId}>
                        <Table.Td>{row.name}</Table.Td>
                        <Table.Td ta="right" className="tabular">
                          {row.invoices}
                        </Table.Td>
                        <Table.Td ta="right" className="tabular">
                          {row.maxDaysOverdue} d
                        </Table.Td>
                        <Table.Td ta="right">
                          <MoneyText amount={row.amount} c="red.7" fw={500} />
                        </Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Table.ScrollContainer>
            )}
          </Paper>
        </Grid.Col>

        <Grid.Col span={{ base: 12, lg: 6 }}>
          <Paper p="md" h="100%">
            <Group justify="space-between" mb="sm">
              <Text fw={600}>Recent activity</Text>
              <Anchor component={Link} to="/activity" size="sm">
                All activity
              </Anchor>
            </Group>
            <HistoryTimeline events={data.recentActivity} />
          </Paper>
        </Grid.Col>
      </Grid>
    </>
  )
}

interface TileProps {
  icon: Icon
  color: string
  label: string
  value: string
  hint: string
  to: string
  danger?: boolean
}

function Tile({ icon: TileIcon, color, label, value, hint, to, danger }: TileProps) {
  const navigate = useNavigate()
  return (
    <UnstyledButton onClick={() => navigate(to)} style={{ display: 'block' }}>
      <Paper p="md" style={{ transition: 'box-shadow 120ms' }} className="tile">
        <Group justify="space-between" align="flex-start" wrap="nowrap">
          <div>
            <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
              {label}
            </Text>
            <Text fz={24} fw={700} mt={4} className="tabular" c={danger ? 'red.7' : undefined}>
              {value}
            </Text>
          </div>
          <ThemeIcon variant="light" color={color} size={36} radius="md">
            <TileIcon size={20} />
          </ThemeIcon>
        </Group>
        <Text size="xs" c="dimmed" mt={6}>
          {hint}
        </Text>
      </Paper>
    </UnstyledButton>
  )
}

function Legend({ color, label, value }: { color: string; label: string; value: number }) {
  return (
    <Group justify="space-between">
      <Group gap={8}>
        <div style={{ width: 10, height: 10, borderRadius: 3, background: `var(--mantine-color-${color.replace('.', '-')})` }} />
        <Text size="sm">{label}</Text>
      </Group>
      <Text size="sm" className="tabular" fw={500}>
        {value}
      </Text>
    </Group>
  )
}
