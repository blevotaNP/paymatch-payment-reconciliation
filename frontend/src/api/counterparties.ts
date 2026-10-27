import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, buildQuery } from './client'
import type { Counterparty, CounterpartyRequest, Page, SortParam } from './types'

interface CounterpartyListParams {
  search?: string
  page: number
  size: number
  sort?: SortParam
}

export function useCounterparties({ search, page, size, sort }: CounterpartyListParams) {
  return useQuery({
    queryKey: ['counterparties', { search, page, size, sort }],
    queryFn: () =>
      api<Page<Counterparty>>(
        `/api/counterparties${buildQuery({
          search,
          page,
          size,
          sort: sort ? `${sort.column},${sort.direction}` : undefined,
        })}`,
      ),
    placeholderData: keepPreviousData,
  })
}

export function useSaveCounterparty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id?: number; request: CounterpartyRequest }) =>
      id
        ? api<Counterparty>(`/api/counterparties/${id}`, { method: 'PUT', body: request })
        : api<Counterparty>('/api/counterparties', { method: 'POST', body: request }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['counterparties'] })
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
  })
}

export function useDeleteCounterparty() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/counterparties/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['counterparties'] }),
  })
}
