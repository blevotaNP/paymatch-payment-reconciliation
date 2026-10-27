import { Alert, Button, Drawer, Group, Select, SimpleGrid, Stack, Tabs, Text, TextInput } from '@mantine/core'
import { DateInput } from '@mantine/dates'
import { useForm } from '@mantine/form'
import { IconInfoCircle } from '@tabler/icons-react'
import { useEffect } from 'react'
import { ApiError } from '../../api/client'
import { useSaveInvoice } from '../../api/invoices'
import { useInvoiceHistory } from '../../api/review'
import type { Invoice } from '../../api/types'
import { CounterpartySelect } from '../../components/CounterpartySelect'
import { HistoryTimeline } from '../../components/HistoryTimeline'
import { notifyError, notifySuccess } from '../../components/notify'
import { addDaysIso, todayIso } from '../../lib/dates'
import { amountToInput, formatMoney, isZero, normalizeAmountInput } from '../../lib/money'
import { InvoicePayments } from './InvoicePayments'

interface InvoiceFormDrawerProps {
  opened: boolean
  invoice: Invoice | null
  onClose: () => void
}

interface FormValues {
  number: string
  counterpartyId: number | null
  issueDate: string | null
  dueDate: string | null
  grossAmount: string
  currency: string
}

const PAYMENT_TERMS = [7, 14, 30]

export function InvoiceFormDrawer({ opened, invoice, onClose }: InvoiceFormDrawerProps) {
  const save = useSaveInvoice()
  const history = useInvoiceHistory(opened ? invoice?.id : null)
  const form = useForm<FormValues>({
    initialValues: emptyValues(),
    validate: {
      number: (value) => (value.trim() ? null : 'Invoice number is required'),
      counterpartyId: (value) => (value ? null : 'Choose a counterparty'),
      issueDate: (value) => (value ? null : 'Issue date is required'),
      dueDate: (value, values) => {
        if (!value) return 'Due date is required'
        if (values.issueDate && value < values.issueDate) return 'Due date cannot be before issue date'
        return null
      },
      grossAmount: (value) => {
        const amount = normalizeAmountInput(value)
        if (!amount) return 'Enter an amount like 1 230,50'
        return isZero(amount) ? 'Amount must be greater than zero' : null
      },
    },
  })

  useEffect(() => {
    if (!opened) return
    form.setValues(
      invoice
        ? {
            number: invoice.number,
            counterpartyId: invoice.counterparty.id,
            issueDate: invoice.issueDate,
            dueDate: invoice.dueDate,
            grossAmount: amountToInput(invoice.grossAmount),
            currency: invoice.currency,
          }
        : emptyValues(),
    )
    form.resetDirty()
    form.clearErrors()
    // form is intentionally left out: only reset when the dialog opens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [opened, invoice])

  const handleSubmit = form.onSubmit((values) => {
    const request = {
      number: values.number.trim(),
      counterpartyId: values.counterpartyId!,
      issueDate: values.issueDate!,
      dueDate: values.dueDate!,
      grossAmount: normalizeAmountInput(values.grossAmount)!,
      currency: values.currency,
    }
    save.mutate(
      { id: invoice?.id, request },
      {
        onSuccess: (saved) => {
          notifySuccess(invoice ? `Invoice ${saved.number} updated` : `Invoice ${saved.number} created`)
          onClose()
        },
        onError: (error) => {
          if (error instanceof ApiError && error.problem.errors) {
            const errors = error.fieldErrors
            if (errors.dueDateValid) errors.dueDate = errors.dueDateValid
            form.setErrors(errors)
          } else if (error instanceof ApiError && error.status === 409) {
            form.setFieldError('number', error.message)
          } else {
            notifyError(error, 'Could not save invoice')
          }
        },
      },
    )
  })

  const hasPayments = invoice != null && !isZero(invoice.paidAmount)

  const formContent = (
    <form onSubmit={handleSubmit}>
      <Stack>
        {hasPayments && (
          <Alert color="blue" icon={<IconInfoCircle size={18} />} py="xs">
            {formatMoney(invoice.paidAmount, invoice.currency)} is already matched to this invoice. The status will
            be recalculated if you change the amount.
          </Alert>
        )}
        <TextInput label="Invoice number" placeholder="FV/10/2026" withAsterisk data-autofocus {...form.getInputProps('number')} />
        <CounterpartySelect
          value={form.values.counterpartyId}
          onChange={(id) => form.setFieldValue('counterpartyId', id)}
          selected={invoice?.counterparty}
          error={form.errors.counterpartyId as string | undefined}
        />
        <SimpleGrid cols={2}>
          <DateInput label="Issue date" valueFormat="DD.MM.YYYY" withAsterisk {...form.getInputProps('issueDate')} />
          <DateInput label="Due date" valueFormat="DD.MM.YYYY" withAsterisk {...form.getInputProps('dueDate')} />
        </SimpleGrid>
        <Group gap={6} mt={-8}>
          <Text size="xs" c="dimmed">
            Payment term:
          </Text>
          {PAYMENT_TERMS.map((days) => (
            <Button
              key={days}
              size="compact-xs"
              variant="light"
              color="gray"
              onClick={() => form.setFieldValue('dueDate', addDaysIso(form.values.issueDate ?? todayIso(), days))}
            >
              {days} days
            </Button>
          ))}
        </Group>
        <SimpleGrid cols={2}>
          <TextInput
            label="Gross amount"
            placeholder="1 230,50"
            withAsterisk
            styles={{ input: { textAlign: 'right' } }}
            className="tabular"
            {...form.getInputProps('grossAmount')}
          />
          <Select
            label="Currency"
            data={['PLN', 'EUR']}
            allowDeselect={false}
            disabled={hasPayments}
            {...form.getInputProps('currency')}
          />
        </SimpleGrid>
        <Group justify="flex-end" mt="md">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {invoice ? 'Save changes' : 'Create invoice'}
          </Button>
        </Group>
      </Stack>
    </form>
  )

  return (
    <Drawer
      opened={opened}
      onClose={onClose}
      position="right"
      size="md"
      title={<Text fw={600}>{invoice ? `Invoice ${invoice.number}` : 'New invoice'}</Text>}
    >
      {invoice ? (
        <Tabs defaultValue="details" keepMounted={false}>
          <Tabs.List mb="md">
            <Tabs.Tab value="details">Details</Tabs.Tab>
            <Tabs.Tab value="payments">Payments</Tabs.Tab>
            <Tabs.Tab value="history">History</Tabs.Tab>
          </Tabs.List>
          <Tabs.Panel value="details">{formContent}</Tabs.Panel>
          <Tabs.Panel value="payments">
            <InvoicePayments invoice={invoice} />
          </Tabs.Panel>
          <Tabs.Panel value="history">
            <HistoryTimeline events={history.data} loading={history.isPending} />
          </Tabs.Panel>
        </Tabs>
      ) : (
        formContent
      )}
    </Drawer>
  )
}

function emptyValues(): FormValues {
  const today = todayIso()
  return {
    number: '',
    counterpartyId: null,
    issueDate: today,
    dueDate: addDaysIso(today, 14),
    grossAmount: '',
    currency: 'PLN',
  }
}
