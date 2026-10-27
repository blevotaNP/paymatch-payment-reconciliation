import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, buildQuery } from './client'
import type { Allocation, AuditAction, AuditEvent, IgnoreReason, Page, TransactionDetails } from './types'

export function useReviewSummary() {
  return useQuery({
    queryKey: ['review', 'summary'],
    queryFn: () => api<{ toReview: number; unmatched: number }>('/api/review/summary'),
    refetchInterval: 60_000,
  })
}

export function useIgnoreReasons() {
  return useQuery({
    queryKey: ['review', 'ignore-reasons'],
    queryFn: () => api<{ code: IgnoreReason; label: string }[]>('/api/review/ignore-reasons'),
    staleTime: Infinity,
  })
}

type ReviewAction =
  | { type: 'confirm'; suggestionId: number }
  | { type: 'reject'; suggestionId: number }
  | { type: 'allocate'; parts: { invoiceId: number; amount: string }[]; note?: string }
  | { type: 'ignore'; reason: IgnoreReason; note?: string }
  | { type: 'undo' }

/** One mutation for all review actions; every action sends the version the user was looking at. */
export function useReviewAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ transactionId, version, action }: { transactionId: number; version: number; action: ReviewAction }) => {
      const { type, ...body } = action
      return api<TransactionDetails>(`/api/review/transactions/${transactionId}/${type}`, {
        method: 'POST',
        body: { ...body, version },
      })
    },
    onSuccess: (details) => {
      queryClient.setQueryData(['transactions', 'details', details.transaction.id], details)
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      queryClient.invalidateQueries({ queryKey: ['review'] })
      queryClient.invalidateQueries({ queryKey: ['audit'] })
    },
  })
}

export function useTransactionHistory(id: number | null) {
  return useQuery({
    queryKey: ['audit', 'transaction', id],
    queryFn: () => api<AuditEvent[]>(`/api/audit/transactions/${id}`),
    enabled: id != null,
  })
}

export function useInvoiceHistory(id: number | null | undefined) {
  return useQuery({
    queryKey: ['audit', 'invoice', id],
    queryFn: () => api<AuditEvent[]>(`/api/audit/invoices/${id}`),
    enabled: id != null,
  })
}

export function useInvoicePayments(id: number | null | undefined) {
  return useQuery({
    queryKey: ['invoices', 'payments', id],
    queryFn: () => api<Allocation[]>(`/api/invoices/${id}/payments`),
    enabled: id != null,
  })
}

export function useAuditLog(page: number, size: number, actions: AuditAction[]) {
  return useQuery({
    queryKey: ['audit', 'log', { page, size, actions }],
    queryFn: () => api<Page<AuditEvent>>(`/api/audit${buildQuery({ page, size, action: actions })}`),
    placeholderData: keepPreviousData,
  })
}
