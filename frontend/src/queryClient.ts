import { QueryCache, QueryClient } from '@tanstack/react-query'
import { ApiError } from './api/client'
import { meQueryKey } from './api/auth'

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => {
      // session expired: drop the user, the route guard sends them to the login page
      if (error instanceof ApiError && error.status === 401) {
        queryClient.setQueryData(meQueryKey, null)
      }
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 2,
    },
  },
})
