import { ActionIcon, Group, Select, Tooltip } from '@mantine/core'
import { useDebouncedValue, useDisclosure } from '@mantine/hooks'
import { IconPlus } from '@tabler/icons-react'
import { useMemo, useState } from 'react'
import { useCounterparties } from '../api/counterparties'
import type { Counterparty } from '../api/types'
import { CounterpartyFormModal } from '../pages/counterparties/CounterpartyFormModal'
import { formatNip } from '../lib/validation'

interface CounterpartySelectProps {
  value: number | null
  onChange: (id: number | null) => void
  /** The currently selected counterparty, so its label shows before any search results arrive. */
  selected?: Pick<Counterparty, 'id' | 'name' | 'nip'> | null
  error?: string
}

export function CounterpartySelect({ value, onChange, selected, error }: CounterpartySelectProps) {
  const [search, setSearch] = useState('')
  const [debouncedSearch] = useDebouncedValue(search, 250)
  const [created, setCreated] = useState<Counterparty | null>(null)
  const [modalOpened, modal] = useDisclosure(false)
  const { data, isFetching } = useCounterparties({ search: debouncedSearch, page: 0, size: 20, sort: { column: 'name', direction: 'asc' } })

  const options = useMemo(() => {
    const byId = new Map<number, Pick<Counterparty, 'id' | 'name' | 'nip'>>()
    for (const c of [selected, created, ...(data?.content ?? [])]) {
      if (c) byId.set(c.id, c)
    }
    return [...byId.values()].map((c) => ({
      value: String(c.id),
      label: c.nip ? `${c.name} · NIP ${formatNip(c.nip)}` : c.name,
    }))
  }, [data, selected, created])

  return (
    <>
      <Group gap={6} align="flex-start" wrap="nowrap">
        <Select
          label="Counterparty"
          placeholder="Search by name or NIP"
          withAsterisk
          searchable
          clearable
          nothingFoundMessage={isFetching ? 'Searching…' : 'No counterparties found'}
          data={options}
          value={value ? String(value) : null}
          onChange={(v) => onChange(v ? Number(v) : null)}
          searchValue={search}
          onSearchChange={setSearch}
          filter={({ options }) => options}
          error={error}
          style={{ flex: 1 }}
        />
        <Tooltip label="New counterparty">
          <ActionIcon variant="default" size={36} mt={25} onClick={modal.open} aria-label="New counterparty">
            <IconPlus size={16} />
          </ActionIcon>
        </Tooltip>
      </Group>
      <CounterpartyFormModal
        opened={modalOpened}
        onClose={modal.close}
        onSaved={(counterparty) => {
          setCreated(counterparty)
          onChange(counterparty.id)
        }}
      />
    </>
  )
}
