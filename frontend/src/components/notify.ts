import { notifications } from '@mantine/notifications'
import { ApiError } from '../api/client'

export function notifySuccess(message: string) {
  notifications.show({ color: 'teal', title: 'Done', message })
}

export function notifyError(error: unknown, title = 'Something went wrong') {
  const message = error instanceof ApiError ? error.message : 'Unexpected error, please try again.'
  notifications.show({ color: 'red', title, message })
}
