import { Button, Group, Modal, Stack, TextInput } from '@mantine/core'
import { useForm } from '@mantine/form'
import { useEffect } from 'react'
import { ApiError } from '../../api/client'
import { useSaveCounterparty } from '../../api/counterparties'
import type { Counterparty } from '../../api/types'
import { notifyError, notifySuccess } from '../../components/notify'
import { formatIban, isValidIban, isValidNip, normalizeIban, normalizeNip } from '../../lib/validation'

interface CounterpartyFormModalProps {
  opened: boolean
  counterparty?: Counterparty | null
  onClose: () => void
  onSaved?: (counterparty: Counterparty) => void
}

export function CounterpartyFormModal({ opened, counterparty, onClose, onSaved }: CounterpartyFormModalProps) {
  const save = useSaveCounterparty()
  const form = useForm({
    initialValues: { name: '', nip: '', iban: '' },
    validate: {
      name: (value) => (value.trim() ? null : 'Name is required'),
      nip: (value) => (!value.trim() || isValidNip(value) ? null : 'Invalid NIP (checksum does not match)'),
      iban: (value) => (!value.trim() || isValidIban(value) ? null : 'Invalid IBAN'),
    },
  })

  useEffect(() => {
    if (opened) {
      form.setValues({
        name: counterparty?.name ?? '',
        nip: counterparty?.nip ?? '',
        iban: counterparty?.iban ? formatIban(counterparty.iban) : '',
      })
      form.resetDirty()
      form.clearErrors()
    }
    // form is intentionally left out: only reset when the dialog opens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [opened, counterparty])

  const handleSubmit = form.onSubmit((values) => {
    const request = {
      name: values.name.trim(),
      nip: values.nip.trim() ? normalizeNip(values.nip) : null,
      iban: values.iban.trim() ? normalizeIban(values.iban) : null,
    }
    save.mutate(
      { id: counterparty?.id, request },
      {
        onSuccess: (saved) => {
          notifySuccess(counterparty ? 'Counterparty updated' : 'Counterparty added')
          onSaved?.(saved)
          onClose()
        },
        onError: (error) => {
          if (error instanceof ApiError && error.problem.errors) form.setErrors(error.fieldErrors)
          else notifyError(error, 'Could not save counterparty')
        },
      },
    )
  })

  return (
    <Modal opened={opened} onClose={onClose} title={counterparty ? 'Edit counterparty' : 'New counterparty'} centered>
      <form onSubmit={handleSubmit}>
        <Stack>
          <TextInput label="Name" placeholder="ACME Sp. z o.o." withAsterisk data-autofocus {...form.getInputProps('name')} />
          <TextInput label="NIP" placeholder="526-018-15-96" description="Optional for private persons" {...form.getInputProps('nip')} />
          <TextInput
            label="Bank account (IBAN)"
            placeholder="PL61 1090 1014 0000 0712 1981 2874"
            description="Helps match transfers that do not mention the invoice number"
            {...form.getInputProps('iban')}
          />
          <Group justify="flex-end" mt="sm">
            <Button variant="default" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={save.isPending}>
              Save
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  )
}
