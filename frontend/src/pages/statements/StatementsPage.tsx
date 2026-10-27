import { ActionIcon, Badge, Button, Group, Menu, Modal, Paper, Stack, Text, Tooltip } from '@mantine/core'
import { modals } from '@mantine/modals'
import { IconArrowBackUp, IconDots, IconFileDescription, IconList } from '@tabler/icons-react'
import { DataTable } from 'mantine-datatable'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useImports, useUndoImport } from '../../api/statements'
import type { StatementImport } from '../../api/types'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { notifyError, notifySuccess } from '../../components/notify'
import { PageHeader } from '../../components/PageHeader'
import { formatDate, formatDateTime } from '../../lib/dates'
import { formatIban, formatIbanShort } from '../../lib/validation'
import { ImportSummary } from './ImportSummary'
import { StatementUpload } from './StatementUpload'

const PAGE_SIZE = 10

export function StatementsPage() {
  const [page, setPage] = useState(1)
  const [shown, setShown] = useState<{ result: StatementImport; justImported: boolean } | null>(null)
  const { data, isFetching, error, refetch } = useImports(page - 1, PAGE_SIZE)
  const undo = useUndoImport()
  const navigate = useNavigate()

  const confirmUndo = (item: StatementImport) =>
    modals.openConfirmModal({
      title: 'Undo import',
      children: (
        <Text size="sm">
          Remove all <b>{item.importedCount}</b> transactions imported from <b>{item.fileName}</b>? The import stays in the
          history as undone, and the file can be imported again later.
        </Text>
      ),
      labels: { confirm: 'Undo import', cancel: 'Cancel' },
      confirmProps: { color: 'red' },
      onConfirm: () =>
        undo.mutate(item.id, {
          onSuccess: () => notifySuccess(`Import of ${item.fileName} was undone`),
          onError: (e) => notifyError(e, 'Could not undo import'),
        }),
    })

  return (
    <>
      <PageHeader title="Bank statements" description="Upload statements from your bank. Every import is recorded and can be undone." />

      <Stack gap="lg">
        <StatementUpload onImported={(result) => setShown({ result, justImported: true })} />

        <Paper p={0}>
          <Text fw={600} p="md" pb={0}>
            Import history
          </Text>
          {error ? (
            <ErrorState error={error} onRetry={() => refetch()} />
          ) : data?.totalElements === 0 ? (
            <EmptyState
              icon={<IconFileDescription size={24} />}
              title="No statements imported yet"
              description="Sample files for every supported bank are in the samples/statements folder of the project."
            />
          ) : (
            <DataTable<StatementImport>
              minHeight={200}
              verticalSpacing="sm"
              horizontalSpacing="md"
              highlightOnHover
              records={data?.content ?? []}
              fetching={isFetching}
              totalRecords={data?.totalElements ?? 0}
              recordsPerPage={PAGE_SIZE}
              page={page}
              onPageChange={setPage}
              onRowClick={({ record }) => setShown({ result: record, justImported: false })}
              rowStyle={(item) => (item.status === 'UNDONE' ? { opacity: 0.55 } : undefined)}
              columns={[
                {
                  accessor: 'importedAt',
                  title: 'Imported',
                  render: (item) => (
                    <Stack gap={0}>
                      <Text size="sm" className="tabular">
                        {formatDateTime(item.importedAt)}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {item.importedBy}
                      </Text>
                    </Stack>
                  ),
                },
                {
                  accessor: 'fileName',
                  title: 'File',
                  render: (item) => (
                    <Stack gap={2}>
                      <Text size="sm" fw={500} lineClamp={1} title={item.fileName}>
                        {item.fileName}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {item.bankLabel}
                      </Text>
                    </Stack>
                  ),
                },
                {
                  accessor: 'accountIban',
                  title: 'Account',
                  noWrap: true,
                  render: (item) =>
                    item.accountIban ? (
                      <Tooltip label={formatIban(item.accountIban)}>
                        <Text size="sm" ff="monospace">
                          {formatIbanShort(item.accountIban)}
                        </Text>
                      </Tooltip>
                    ) : (
                      <Text size="sm" c="dimmed">
                        not in file
                      </Text>
                    ),
                },
                {
                  accessor: 'period',
                  title: 'Period',
                  noWrap: true,
                  render: (item) => (
                    <Text size="sm" className="tabular">
                      {item.periodFrom ? `${formatDate(item.periodFrom)} – ${formatDate(item.periodTo)}` : '–'}
                    </Text>
                  ),
                },
                { accessor: 'importedCount', title: 'New', textAlign: 'right', render: (item) => <Count value={item.importedCount} color="teal" /> },
                { accessor: 'duplicateCount', title: 'Duplicates', textAlign: 'right', render: (item) => <Count value={item.duplicateCount} color="blue" /> },
                { accessor: 'skippedCount', title: 'Skipped', textAlign: 'right', render: (item) => <Count value={item.skippedCount} color="red" /> },
                {
                  accessor: 'status',
                  title: 'Status',
                  width: 110,
                  render: (item) =>
                    item.status === 'ACTIVE' ? (
                      <Badge color="teal">
                        Active
                      </Badge>
                    ) : (
                      <Badge color="gray" title={item.undoneAt ? `Undone ${formatDateTime(item.undoneAt)} by ${item.undoneBy}` : undefined}>
                        Undone
                      </Badge>
                    ),
                },
                {
                  accessor: 'actions',
                  title: '',
                  width: 60,
                  textAlign: 'right',
                  render: (item) => (
                    <Group justify="flex-end" onClick={(e) => e.stopPropagation()}>
                      <Menu position="bottom-end" withinPortal>
                        <Menu.Target>
                          <ActionIcon variant="subtle" color="gray" aria-label="Actions">
                            <IconDots size={16} />
                          </ActionIcon>
                        </Menu.Target>
                        <Menu.Dropdown>
                          <Menu.Item leftSection={<IconFileDescription size={16} />} onClick={() => setShown({ result: item, justImported: false })}>
                            Import details
                          </Menu.Item>
                          <Menu.Item
                            leftSection={<IconList size={16} />}
                            disabled={item.status === 'UNDONE'}
                            onClick={() => navigate(`/transactions?importId=${item.id}`)}
                          >
                            Show transactions
                          </Menu.Item>
                          <Menu.Divider />
                          <Menu.Item
                            color="red"
                            leftSection={<IconArrowBackUp size={16} />}
                            disabled={item.status === 'UNDONE'}
                            onClick={() => confirmUndo(item)}
                          >
                            Undo import
                          </Menu.Item>
                        </Menu.Dropdown>
                      </Menu>
                    </Group>
                  ),
                },
              ]}
            />
          )}
        </Paper>
      </Stack>

      <Modal
        opened={shown != null}
        onClose={() => setShown(null)}
        size="xl"
        title={<Text fw={600}>{shown?.justImported ? 'Import finished' : 'Import details'}</Text>}
      >
        {shown && (
          <Stack>
            <ImportSummary result={shown.result} />
            <Group justify="flex-end">
              <Button variant="default" onClick={() => setShown(null)}>
                Close
              </Button>
              {shown.result.status === 'ACTIVE' && shown.result.importedCount > 0 && (
                <Button onClick={() => navigate(`/transactions?importId=${shown.result.id}`)}>Show transactions</Button>
              )}
            </Group>
          </Stack>
        )}
      </Modal>
    </>
  )
}

function Count({ value, color }: { value: number; color: string }) {
  return (
    <Text size="sm" className="tabular" c={value ? color : 'dimmed'} fw={value ? 600 : undefined}>
      {value}
    </Text>
  )
}
