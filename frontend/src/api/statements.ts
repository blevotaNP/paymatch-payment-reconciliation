import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, buildQuery } from './client'
import type {
  BankFormat,
  BankFormatInfo,
  MatchingRunResult,
  MatchStatus,
  Page,
  SortParam,
  StatementImport,
  Transaction,
  TransactionDetails,
} from './types'

export function useBankFormats() {
  return useQuery({
    queryKey: ['bank-formats'],
    queryFn: () => api<BankFormatInfo[]>('/api/imports/formats'),
    staleTime: Infinity,
  })
}

export function useImports(page: number, size: number) {
  return useQuery({
    queryKey: ['imports', { page, size }],
    queryFn: () => api<Page<StatementImport>>(`/api/imports${buildQuery({ page, size })}`),
    placeholderData: keepPreviousData,
  })
}

export function useImport(id: number | null) {
  return useQuery({
    queryKey: ['imports', id],
    queryFn: () => api<StatementImport>(`/api/imports/${id}`),
    enabled: id != null,
  })
}

export function useUploadStatement() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ file, format }: { file: File; format: BankFormat | null }) => {
      const form = new FormData()
      form.append('file', file)
      return api<StatementImport>(`/api/imports${buildQuery({ format })}`, { method: 'POST', body: form })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['imports'] })
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
    },
  })
}

export function useUndoImport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<StatementImport>(`/api/imports/${id}/undo`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['imports'] })
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
    },
  })
}

export interface TransactionListParams {
  search?: string
  direction?: 'INCOMING' | 'OUTGOING'
  importId?: number
  matchStatus?: MatchStatus[]
  from?: string | null
  to?: string | null
  page: number
  size: number
  sort?: SortParam
}

export function useTransactions({ search, direction, importId, matchStatus, from, to, page, size, sort }: TransactionListParams) {
  return useQuery({
    queryKey: ['transactions', { search, direction, importId, matchStatus, from, to, page, size, sort }],
    queryFn: () =>
      api<Page<Transaction>>(
        `/api/transactions${buildQuery({
          search,
          direction,
          importId,
          matchStatus,
          from,
          to,
          page,
          size,
          sort: sort ? `${sort.column},${sort.direction}` : undefined,
        })}`,
      ),
    placeholderData: keepPreviousData,
  })
}

export function useTransaction(id: number | null) {
  return useQuery({
    queryKey: ['transactions', 'details', id],
    queryFn: () => api<TransactionDetails>(`/api/transactions/${id}`),
    enabled: id != null,
  })
}

export function useRunMatching() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api<MatchingRunResult>('/api/matching/run', { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
  })
}
