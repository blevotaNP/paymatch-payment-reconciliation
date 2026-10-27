import { ActionIcon, Button, Group, Paper, Text, TextInput, Tooltip } from '@mantine/core'
import { useDebouncedValue, useDisclosure } from '@mantine/hooks'
import { modals } from '@mantine/modals'
import { IconBuildingStore, IconPencil, IconPlus, IconSearch, IconTrash } from '@tabler/icons-react'
import { DataTable, type DataTableSortStatus } from 'mantine-datatable'
import { useState } from 'react'
import { useCounterparties, useDeleteCounterparty } from '../../api/counterparties'
import type { Counterparty } from '../../api/types'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { notifyError, notifySuccess } from '../../components/notify'
import { PageHeader } from '../../components/PageHeader'
import { formatIban, formatNip } from '../../lib/validation'
import { CounterpartyFormModal } from './CounterpartyFormModal'

const PAGE_SIZES = [10, 20, 50]

export function CounterpartiesPage() {
  const [search, setSearch] = useState('')
  const [debouncedSearch] = useDebouncedValue(search, 300)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [sortStatus, setSortStatus] = useState<DataTableSortStatus<Counterparty>>({
    columnAccessor: 'name',
    direction: 'asc',
  })
  const [editing, setEditing] = useState<Counterparty | null>(null)
  const [formOpened, form] = useDisclosure(false)

  const { data, isFetching, error, refetch } = useCounterparties({
    search: debouncedSearch,
    page: page - 1,
    size: pageSize,
    sort: { column: sortStatus.columnAccessor, direction: sortStatus.direction },
  })
  const remove = useDeleteCounterparty()

  const openForm = (counterparty: Counterparty | null) => {
    setEditing(counterparty)
    form.open()
  }

  const confirmDelete = (counterparty: Counterparty) =>
    modals.openConfirmModal({
      title: 'Delete counterparty',
      children: (
        <Text size="sm">
          Delete <b>{counterparty.name}</b>? This is only possible when it has no invoices.
        </Text>
      ),
      labels: { confirm: 'Delete', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: () =>
        remove.mutate(counterparty.id, {
          onSuccess: () => notifySuccess('Counterparty deleted'),
          onError: (e) => notifyError(e, 'Could not delete counterparty'),
        }),
    })

  const hasNoData = !debouncedSearch && data?.totalElements === 0

  return (
    <>
      <PageHeader
        title="Counterparties"
        description="Clients you issue invoices to. NIP and bank account help with automatic matching."
        actions={
          <Button leftSection={<IconPlus size={16} />} onClick={() => openForm(null)}>
            New counterparty
          </Button>
        }
      />

      <Paper p={0}>
        <Group p="md" pb="sm">
          <TextInput
            placeholder="Search by name or NIP"
            leftSection={<IconSearch size={16} />}
            value={search}
            onChange={(e) => {
              setSearch(e.currentTarget.value)
              setPage(1)
            }}
            w={320}
          />
        </Group>

        {error ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : hasNoData ? (
          <EmptyState
            icon={<IconBuildingStore size={24} />}
            title="No counterparties yet"
            description="They are created automatically when you import invoices, or you can add one manually."
            action={<Button onClick={() => openForm(null)}>Add counterparty</Button>}
          />
        ) : (
          <DataTable<Counterparty>
            minHeight={240}
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
            noRecordsText="No counterparties match your search"
            onRowClick={({ record }) => openForm(record)}
            columns={[
              { accessor: 'name', title: 'Name', sortable: true, render: (c) => <Text size="sm" fw={500}>{c.name}</Text> },
              { accessor: 'nip', title: 'NIP', sortable: true, render: (c) => <Text size="sm" className="tabular">{formatNip(c.nip)}</Text> },
              {
                accessor: 'iban',
                title: 'Bank account',
                render: (c) => (
                  <Text size="sm" ff="monospace" c={c.iban ? undefined : 'dimmed'}>
                    {formatIban(c.iban)}
                  </Text>
                ),
              },
              {
                accessor: 'actions',
                title: '',
                textAlign: 'right',
                width: 90,
                render: (c) => (
                  <Group gap={4} justify="flex-end" wrap="nowrap" onClick={(e) => e.stopPropagation()}>
                    <Tooltip label="Edit">
                      <ActionIcon variant="subtle" color="gray" onClick={() => openForm(c)}>
                        <IconPencil size={16} />
                      </ActionIcon>
                    </Tooltip>
                    <Tooltip label="Delete">
                      <ActionIcon variant="subtle" color="red" onClick={() => confirmDelete(c)}>
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

      <CounterpartyFormModal opened={formOpened} counterparty={editing} onClose={form.close} />
    </>
  )
}
