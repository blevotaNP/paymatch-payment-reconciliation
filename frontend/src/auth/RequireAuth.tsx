import { Center, Loader } from '@mantine/core'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useCurrentUser } from '../api/auth'
import { ErrorState } from '../components/ErrorState'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { data: user, isPending, error, refetch } = useCurrentUser()
  const location = useLocation()

  if (isPending) {
    return (
      <Center h="100vh">
        <Loader />
      </Center>
    )
  }
  if (error) {
    return (
      <Center h="100vh">
        <ErrorState error={error} onRetry={() => refetch()} />
      </Center>
    )
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return children
}
