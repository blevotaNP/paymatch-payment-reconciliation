import { Button } from '@mantine/core'
import { IconMapOff } from '@tabler/icons-react'
import { Link } from 'react-router'
import { EmptyState } from '../components/EmptyState'

export function NotFoundPage() {
  return (
    <EmptyState
      icon={<IconMapOff size={24} />}
      title="Page not found"
      description="The page you are looking for does not exist."
      action={
        <Button component={Link} to="/" variant="default">
          Back to invoices
        </Button>
      }
    />
  )
}
