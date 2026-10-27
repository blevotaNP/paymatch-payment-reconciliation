import { Select } from '@mantine/core'
import { useDebouncedValue } from '@mantine/hooks'
import { useMemo, useState } from 'react'
import { useInvoices } from '../../api/invoices'
import { formatMoney } from '../../lib/money'

export interface PickedInvoice {
  id: number
  number: string
  counterpartyName: string
  remainingAmount: string
  currency: string
}

interface InvoicePickerProps {
  value: PickedInvoice | null
  onChange: (invoice: PickedInvoice | null) => void
  excludeIds: number[]
  error?: string
}

/** Searchable list of open invoices (unpaid and partly paid), by number, counterparty or NIP. */
export function InvoicePicker({ value, onChange, excludeIds, error }: InvoicePickerProps) {
  const [search, setSearch] = useState('')
  const [debounced] = useDebouncedValue(search, 250)
  const { data, isFetching } = useInvoices({
    search: debounced,
    status: ['UNPAID', 'PARTIALLY_PAID'],
    page: 0,
    size: 20,
    sort: { column: 'dueDate', direction: 'asc' },
  })

  const byId = useMemo(() => {
    const map = new Map<number, PickedInvoice>()
    if (value) map.set(value.id, value)
    for (const invoice of data?.content ?? []) {
      map.set(invoice.id, {
        id: invoice.id,
        number: invoice.number,
        counterpartyName: invoice.counterparty.name,
        remainingAmount: invoice.remainingAmount,
        currency: invoice.currency,
      })
    }
    return map
  }, [data, value])

  const options = [...byId.values()]
    .filter((invoice) => invoice.id === value?.id || !excludeIds.includes(invoice.id))
    .map((invoice) => ({
      value: String(invoice.id),
      label: `${invoice.number} · ${invoice.counterpartyName} · ${formatMoney(invoice.remainingAmount, invoice.currency)} left`,
    }))

  return (
    <Select
      placeholder="Search invoice number, counterparty or NIP"
      searchable
      data={options}
      value={value ? String(value.id) : null}
      onChange={(id) => onChange(id ? (byId.get(Number(id)) ?? null) : null)}
      searchValue={search}
      onSearchChange={setSearch}
      filter={({ options }) => options}
      nothingFoundMessage={isFetching ? 'Searching…' : 'No open invoices found'}
      error={error}
      style={{ flex: 1 }}
      comboboxProps={{ withinPortal: true }}
    />
  )
}
