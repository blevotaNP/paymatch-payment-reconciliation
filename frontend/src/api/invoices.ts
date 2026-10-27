import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, buildQuery } from './client'
import type { Invoice, InvoiceImportResult, InvoiceRequest, InvoiceStatus, Page, SortParam } from './types'

export interface InvoiceListParams {
  search?: string
  status?: InvoiceStatus[]
  overdue?: boolean
  page: number
  size: number
  sort?: SortParam
}

export function useInvoices({ search, status, overdue, page, size, sort }: InvoiceListParams) {
  return useQuery({
    queryKey: ['invoices', { search, status, overdue, page, size, sort }],
    queryFn: () =>
      api<Page<Invoice>>(
        `/api/invoices${buildQuery({
          search,
          status,
          overdue: overdue || undefined,
          page,
          size,
          sort: sort ? `${sort.column},${sort.direction}` : undefined,
        })}`,
      ),
    placeholderData: keepPreviousData,
  })
}

export function useSaveInvoice() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id?: number; request: InvoiceRequest }) =>
      id
        ? api<Invoice>(`/api/invoices/${id}`, { method: 'PUT', body: request })
        : api<Invoice>('/api/invoices', { method: 'POST', body: request }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['invoices'] }),
  })
}

export function useDeleteInvoice() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/api/invoices/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['invoices'] }),
  })
}

export function useImportInvoices() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return api<InvoiceImportResult>('/api/invoices/import', { method: 'POST', body: form })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      queryClient.invalidateQueries({ queryKey: ['counterparties'] })
    },
  })
}
