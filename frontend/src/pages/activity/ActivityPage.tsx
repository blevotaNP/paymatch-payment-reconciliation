import { Badge, Group, MultiSelect, Paper, Text } from '@mantine/core'
import { IconHistory } from '@tabler/icons-react'
import { DataTable } from 'mantine-datatable'
import { useState } from 'react'
import { useAuditLog } from '../../api/review'
import type { AuditAction, AuditEvent } from '../../api/types'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { PageHeader } from '../../components/PageHeader'
import { AUDIT_ACTIONS } from '../../lib/audit'
import { formatDateTime } from '../../lib/dates'
import { TransactionDrawer } from '../transactions/TransactionDrawer'

const PAGE_SIZE = 30
const ACTION_OPTIONS = Object.entries(AUDIT_ACTIONS).map(([value, { label }]) => ({ value, label }))

export function ActivityPage() {
  const [page, setPage] = useState(1)
  const [actions, setActions] = useState<string[]>([])
  const [openedTransaction, setOpenedTransaction] = useState<number | null>(null)
  const { data, isFetching, error, refetch } = useAuditLog(page - 1, PAGE_SIZE, actions as AuditAction[])

  return (
    <>
      <PageHeader title="Activity" description="Everything that happened, who did it and when. Entries are never changed or deleted." />
      <Paper p={0}>
        <Group p="md" pb="sm">
          <MultiSelect
            placeholder={actions.length ? undefined : 'All actions'}
            data={ACTION_OPTIONS}
            value={actions}
            onChange={(value) => {
              setActions(value)
              setPage(1)
            }}
            clearable
            w={420}
          />
        </Group>
        {error ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : data?.totalElements === 0 && actions.length === 0 ? (
          <EmptyState icon={<IconHistory size={24} />} title="No activity yet" description="Imports, matches and invoice changes will show up here." />
        ) : (
          <DataTable<AuditEvent>
            minHeight={240}
            verticalSpacing="sm"
            horizontalSpacing="md"
            highlightOnHover
            records={data?.content ?? []}
            fetching={isFetching}
            totalRecords={data?.totalElements ?? 0}
            recordsPerPage={PAGE_SIZE}
            page={page}
            onPageChange={setPage}
            noRecordsText="No entries for this filter"
            onRowClick={({ record }) => record.entityType === 'TRANSACTION' && record.entityId && setOpenedTransaction(record.entityId)}
            columns={[
              {
                accessor: 'occurredAt',
                title: 'When',
                width: 150,
                render: (e) => (
                  <Text size="sm" className="tabular">
                    {formatDateTime(e.occurredAt)}
                  </Text>
                ),
              },
              {
                accessor: 'user',
                title: 'Who',
                width: 150,
                render: (e) => (
                  <Text size="sm" c={e.user ? undefined : 'dimmed'}>
                    {e.user ?? 'System'}
                  </Text>
                ),
              },
              {
                accessor: 'action',
                title: 'Action',
                width: 210,
                render: (e) => <Badge color={AUDIT_ACTIONS[e.action].color}>{AUDIT_ACTIONS[e.action].label}</Badge>,
              },
              { accessor: 'summary', title: 'Details', render: (e) => <Text size="sm">{e.summary}</Text> },
            ]}
          />
        )}
      </Paper>
      <TransactionDrawer id={openedTransaction} onClose={() => setOpenedTransaction(null)} />
    </>
  )
}
