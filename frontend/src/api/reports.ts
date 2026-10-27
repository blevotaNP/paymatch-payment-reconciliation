import { useQuery } from '@tanstack/react-query'
import { api, buildQuery } from './client'
import type { AgingReport, Dashboard } from './types'

export function useDashboard() {
  return useQuery({ queryKey: ['dashboard'], queryFn: () => api<Dashboard>('/api/dashboard') })
}

export function useAgingReport() {
  return useQuery({ queryKey: ['reports', 'aging'], queryFn: () => api<AgingReport>('/api/reports/aging') })
}

export type ExportFormat = 'CSV' | 'XLSX'

/** Exports are plain GET downloads; the session cookie is sent with them, so a link is enough. */
export function download(path: string, params: Record<string, string | null | undefined>) {
  const link = document.createElement('a')
  link.href = `${path}${buildQuery(params)}`
  link.click()
}
