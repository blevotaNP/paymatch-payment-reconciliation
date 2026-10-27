import { ActionIcon, Button, Chip, Group, Paper, SegmentedControl, Stack, Text, TextInput, Tooltip } from '@mantine/core'
import { useDebouncedValue, useDisclosure } from '@mantine/hooks'
import { modals } from '@mantine/modals'
import { IconFileImport, IconFileInvoice, IconPencil, IconPlus, IconSearch, IconTrash } from '@tabler/icons-react'
import { DataTable, type DataTableSortStatus } from 'mantine-datatable'
import { useState } from 'react'
import { useDeleteInvoice, useInvoices } from '../../api/invoices'
import type { Invoice, InvoiceStatus } from '../../api/types'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { InvoiceStatusBadge } from '../../components/InvoiceStatusBadge'
import { INVOICE_STATUS_OPTIONS } from '../../lib/invoiceStatus'
import { MoneyText } from '../../components/MoneyText'
import { notifyError, notifySuccess } from '../../components/notify'
import { PageHeader } from '../../components/PageHeader'
import { formatDate } from '../../lib/dates'
import { isZero } from '../../lib/money'
import { formatNip } from '../../lib/validation'
import { InvoiceFormDrawer } from './InvoiceFormDrawer'
import { InvoiceImportModal } from './InvoiceImportModal'

const PAGE_SIZES = [10, 20, 50, 100]
const STATUS_FILTERS = [{ value: 'ALL', label: 'All' }, ...INVOICE_STATUS_OPTIONS]

export function InvoicesPage() {
  const [search, setSearch] = useState('')
  const [debouncedSearch] = useDebouncedValue(search, 300)
  const [status, setStatus] = useState<string>('ALL')
  const [overdueOnly, setOverdueOnly] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [sortStatus, setSortStatus] = useState<DataTableSortStatus<Invoice>>({ columnAccessor: 'dueDate', direction: 'asc' })
  const [editing, setEditing] = useState<Invoice | null>(null)
  const [formOpened, formDrawer] = useDisclosure(false)
  const [importOpened, importModal] = useDisclosure(false)

  const filtersActive = Boolean(debouncedSearch) || status !== 'ALL' || overdueOnly
  const { data, isFetching, error, refetch } = useInvoices({
    search: debouncedSearch,
    status: status === 'ALL' ? undefined : [status as InvoiceStatus],
    overdue: overdueOnly,
    page: page - 1,
    size: pageSize,
    sort: { column: sortStatus.columnAccessor, direction: sortStatus.direction },
  })
  const remove = useDeleteInvoice()

  const openForm = (invoice: Invoice | null) => {
    setEditing(invoice)
    formDrawer.open()
  }

  const resetPage = <T,>(setter: (value: T) => void) => (value: T) => {
    setter(value)
    setPage(1)
  }

  const confirmDelete = (invoice: Invoice) =>
    modals.openConfirmModal({
      title: 'Delete invoice',
      children: (
        <Text size="sm">
          Delete invoice <b>{invoice.number}</b>? This cannot be undone.
        </Text>
      ),
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: () =>
        remove.mutate(invoice.id, {
          onSuccess: () => notifySuccess(`Invoice ${invoice.number} deleted`),
          onError: (e) => notifyError(e, 'Could not delete invoice'),
        }),
    })

  const actions = (
    <>
      <Button variant="default" leftSection={<IconFileImport size={16} />} onClick={importModal.open}>
        Import CSV
      </Button>
      <Button leftSection={<IconPlus size={16} />} onClick={() => openForm(null)}>
        New invoice
      </Button>
    </>
  )

  return (
    <>
      <PageHeader
        title="Invoices"
        description="Paid amounts and statuses come from confirmed payment matches."
        actions={actions}
      />

      <Paper p={0}>
        <Group p="md" pb="sm" gap="sm" wrap="wrap">
          <TextInput
            placeholder="Search number, counterparty or NIP"
            leftSection={<IconSearch size={16} />}
            value={search}
            onChange={(e) => resetPage(setSearch)(e.currentTarget.value)}
            w={300}
          />
          <SegmentedControl size="sm" data={STATUS_FILTERS} value={status} onChange={resetPage(setStatus)} />
          <Chip checked={overdueOnly} onChange={resetPage(setOverdueOnly)} color="red" variant="light">
            Overdue only
          </Chip>
        </Group>

        {error ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : !filtersActive && data?.totalElements === 0 ? (
          <EmptyState
            icon={<IconFileInvoice size={24} />}
            title="No invoices yet"
            description="Create your first invoice or import a list of invoices from a CSV file."
            action={<Group>{actions}</Group>}
          />
        ) : (
          <DataTable<Invoice>
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
            onRecordsPerPageChange={resetPage(setPageSize)}
            sortStatus={sortStatus}
            onSortStatusChange={setSortStatus}
            noRecordsText="No invoices match the filters"
            onRowClick={({ record }) => openForm(record)}
            columns={[
              {
                accessor: 'number',
                title: 'Number',
                sortable: true,
                render: (invoice) => (
                  <Text size="sm" fw={600}>
                    {invoice.number}
                  </Text>
                ),
              },
              {
                accessor: 'counterparty.name',
                title: 'Counterparty',
                sortable: true,
                render: ({ counterparty }) => (
                  <Stack gap={0}>
                    <Text size="sm" lineClamp={1}>
                      {counterparty.name}
                    </Text>
                    {counterparty.nip && (
                      <Text size="xs" c="dimmed" className="tabular">
                        NIP {formatNip(counterparty.nip)}
                      </Text>
                    )}
                  </Stack>
                ),
              },
              {
                accessor: 'issueDate',
                title: 'Issued',
                sortable: true,
                render: (invoice) => (
                  <Text size="sm" className="tabular">
                    {formatDate(invoice.issueDate)}
                  </Text>
                ),
              },
              {
                accessor: 'dueDate',
                title: 'Due',
                sortable: true,
                render: (invoice) => (
                  <Stack gap={0}>
                    <Text size="sm" className="tabular" c={invoice.overdue ? 'red.7' : undefined} fw={invoice.overdue ? 500 : undefined}>
                      {formatDate(invoice.dueDate)}
                    </Text>
                    {invoice.overdue && (
                      <Text size="xs" c="red.7">
                        {invoice.daysOverdue} {invoice.daysOverdue === 1 ? 'day' : 'days'} overdue
                      </Text>
                    )}
                  </Stack>
                ),
              },
              {
                accessor: 'grossAmount',
                title: 'Gross',
                sortable: true,
                textAlign: 'right',
                render: (invoice) => <MoneyText amount={invoice.grossAmount} currency={invoice.currency} fw={500} />,
              },
              {
                accessor: 'paidAmount',
                title: 'Paid',
                textAlign: 'right',
                render: (invoice) => (
                  <MoneyText amount={invoice.paidAmount} currency={invoice.currency} c={isZero(invoice.paidAmount) ? 'dimmed' : undefined} />
                ),
              },
              {
                accessor: 'remainingAmount',
                title: 'Remaining',
                textAlign: 'right',
                render: (invoice) => (
                  <MoneyText
                    amount={invoice.remainingAmount}
                    currency={invoice.currency}
                    c={isZero(invoice.remainingAmount) ? 'dimmed' : undefined}
                  />
                ),
              },
              {
                accessor: 'status',
                title: 'Status',
                sortable: true,
                render: (invoice) => <InvoiceStatusBadge status={invoice.status} />,
              },
              {
                accessor: 'actions',
                title: '',
                textAlign: 'right',
                width: 90,
                render: (invoice) => (
                  <Group gap={4} justify="flex-end" wrap="nowrap" onClick={(e) => e.stopPropagation()}>
                    <Tooltip label="Edit">
                      <ActionIcon variant="subtle" color="gray" onClick={() => openForm(invoice)} aria-label="Edit">
                        <IconPencil size={16} />
                      </ActionIcon>
                    </Tooltip>
                    <Tooltip label="Delete">
                      <ActionIcon variant="subtle" color="red" onClick={() => confirmDelete(invoice)} aria-label="Delete">
                        <IconTrash size={16} />
                      </ActionIcon>
                    </Tooltip>
                  </Group>
                ),
              },
            ]}
          />
        )}
      </Paper>

      <InvoiceFormDrawer opened={formOpened} invoice={editing} onClose={formDrawer.close} />
      <InvoiceImportModal opened={importOpened} onClose={importModal.close} />
    </>
  )
}
