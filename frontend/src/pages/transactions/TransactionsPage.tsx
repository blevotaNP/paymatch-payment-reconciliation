import { Badge, Button, CloseButton, Group, Paper, SegmentedControl, Select, Stack, Text, TextInput } from '@mantine/core'
import { DatePickerInput, type DatesRangeValue } from '@mantine/dates'
import { useDebouncedValue } from '@mantine/hooks'
import { IconArrowsExchange, IconCalendar, IconRefresh, IconSearch } from '@tabler/icons-react'
import { DataTable, type DataTableSortStatus } from 'mantine-datatable'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useImport, useRunMatching, useTransactions } from '../../api/statements'
import type { MatchStatus, Transaction } from '../../api/types'
import { AmountText } from '../../components/AmountText'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { MatchCell } from '../../components/MatchBadges'
import { notifyError, notifySuccess } from '../../components/notify'
import { PageHeader } from '../../components/PageHeader'
import { formatDate } from '../../lib/dates'
import { formatIban } from '../../lib/validation'
import { TransactionDrawer } from './TransactionDrawer'

const PAGE_SIZES = [20, 50, 100]
const DIRECTIONS = [
  { value: 'ALL', label: 'All' },
  { value: 'INCOMING', label: 'Incoming' },
  { value: 'OUTGOING', label: 'Outgoing' },
]
const MATCH_FILTERS = [
  { value: 'SUGGESTED', label: 'To review' },
  { value: 'UNMATCHED', label: 'Unmatched' },
  { value: 'MATCHED', label: 'Matched' },
  { value: 'IGNORED', label: 'Not an invoice' },
]

export function TransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const importId = searchParams.get('importId') ? Number(searchParams.get('importId')) : undefined
  const { data: importInfo } = useImport(importId ?? null)

  const [search, setSearch] = useState('')
  const [debouncedSearch] = useDebouncedValue(search, 300)
  const [direction, setDirection] = useState('ALL')
  const [matchStatus, setMatchStatus] = useState<string | null>(searchParams.get('matchStatus'))
  const runMatching = useRunMatching()
  const [range, setRange] = useState<DatesRangeValue<string>>([null, null])
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [sortStatus, setSortStatus] = useState<DataTableSortStatus<Transaction>>({ columnAccessor: 'bookingDate', direction: 'desc' })
  const [openedId, setOpenedId] = useState<number | null>(null)

  const { data, isFetching, error, refetch } = useTransactions({
    search: debouncedSearch,
    direction: direction === 'ALL' ? undefined : (direction as 'INCOMING' | 'OUTGOING'),
    importId,
    matchStatus: matchStatus ? [matchStatus as MatchStatus] : undefined,
    from: range[0],
    to: range[1],
    page: page - 1,
    size: pageSize,
    sort: { column: sortStatus.columnAccessor, direction: sortStatus.direction },
  })

  const filtersActive =
    Boolean(debouncedSearch) || direction !== 'ALL' || importId != null || range[0] != null || matchStatus != null

  const rerun = () =>
    runMatching.mutate(undefined, {
      onSuccess: (result) =>
        notifySuccess(
          `Checked ${result.checked} payments: ${result.autoConfirmed} matched automatically, ${result.suggested} to review`,
        ),
      onError: (e) => notifyError(e, 'Matching failed'),
    })

  return (
    <>
      <PageHeader
        title="Transactions"
        description="All transactions from imported bank statements."
        actions={
          <Button variant="default" leftSection={<IconRefresh size={16} />} loading={runMatching.isPending} onClick={rerun}>
            Run matching
          </Button>
        }
      />

      <Paper p={0}>
        <Group p="md" pb="sm" gap="sm" wrap="wrap">
          <TextInput
            placeholder="Search title, counterparty, account"
            leftSection={<IconSearch size={16} />}
            value={search}
            onChange={(e) => {
              setSearch(e.currentTarget.value)
              setPage(1)
            }}
            w={300}
          />
          <SegmentedControl
            data={DIRECTIONS}
            value={direction}
            onChange={(value) => {
              setDirection(value)
              setPage(1)
            }}
          />
          <Select
            placeholder="Any match status"
            data={MATCH_FILTERS}
            value={matchStatus}
            onChange={(value) => {
              setMatchStatus(value)
              setPage(1)
            }}
            clearable
            w={180}
          />
          <DatePickerInput
            type="range"
            placeholder="Booking date"
            valueFormat="DD.MM.YYYY"
            leftSection={<IconCalendar size={16} />}
            clearable
            value={range}
            onChange={(value) => {
              setRange(value)
              setPage(1)
            }}
            w={250}
          />
          {importId != null && (
            <Badge
              size="lg"
              variant="light"
              rightSection={
                <CloseButton
                  size="xs"
                  aria-label="Clear import filter"
                  onClick={() => {
                    searchParams.delete('importId')
                    setSearchParams(searchParams)
                  }}
                />
              }
              style={{ textTransform: 'none' }}
            >
              Import: {importInfo?.fileName ?? `#${importId}`}
            </Badge>
          )}
        </Group>

        {error ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : !filtersActive && data?.totalElements === 0 ? (
          <EmptyState
            icon={<IconArrowsExchange size={24} />}
            title="No transactions yet"
            description="Transactions appear here after you import a bank statement."
            action={
              <Text size="sm" component={Link} to="/statements" c="brand" fw={500}>
                Import a statement →
              </Text>
            }
          />
        ) : (
          <DataTable<Transaction>
            minHeight={280}
            highlightOnHover
            verticalSpacing="sm"
            horizontalSpacing="md"
            records={data?.content ?? []}
            fetching={isFetching}
            totalRecords={data?.totalElements ?? 0}
            recordsPerPage={pageSize}
            page={page}
            onPageChange={setPage}
            recordsPerPageOptions={PAGE_SIZES}
            onRecordsPerPageChange={(size) => {
              setPageSize(size)
              setPage(1)
            }}
            sortStatus={sortStatus}
            onSortStatusChange={setSortStatus}
            noRecordsText="No transactions match the filters"
            onRowClick={({ record }) => setOpenedId(record.id)}
            columns={[
              {
                accessor: 'bookingDate',
                title: 'Booked',
                sortable: true,
                width: 110,
                render: (tx) => (
                  <Text size="sm" className="tabular">
                    {formatDate(tx.bookingDate)}
                  </Text>
                ),
              },
              {
                accessor: 'counterpartyName',
                title: 'Counterparty',
                sortable: true,
                width: '28%',
                render: (tx) => (
                  <Stack gap={0}>
                    <Text size="sm" lineClamp={1} c={tx.counterpartyName ? undefined : 'dimmed'}>
                      {tx.counterpartyName ?? '–'}
                    </Text>
                    {tx.counterpartyAccount && (
                      <Text size="xs" c="dimmed" ff="monospace" lineClamp={1}>
                        {formatIban(tx.counterpartyAccount)}
                      </Text>
                    )}
                  </Stack>
                ),
              },
              {
                accessor: 'title',
                title: 'Title',
                render: (tx) => (
                  <Text size="sm" lineClamp={2}>
                    {tx.title ?? '–'}
                  </Text>
                ),
              },
              {
                accessor: 'amount',
                title: 'Amount',
                sortable: true,
                textAlign: 'right',
                render: (tx) => <AmountText amount={tx.amount} currency={tx.currency} />,
              },
              {
                accessor: 'matchStatus',
                title: 'Match',
                width: 220,
                render: (tx) => <MatchCell tx={tx} />,
              },
              {
                accessor: 'bankFormat',
                title: 'Source',
                width: 90,
                render: (tx) => (
                  <Badge variant="outline" color="gray" size="sm">
                    {tx.bankFormat.replace('_CSV', '').replace('_', ' ')}
                  </Badge>
                ),
              },
            ]}
          />
        )}
      </Paper>

      <TransactionDrawer id={openedId} onClose={() => setOpenedId(null)} />
    </>
  )
}
