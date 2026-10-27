import type { AgingBucket } from '../api/types'

export const AGING_BUCKETS: { key: AgingBucket; label: string; color: string }[] = [
  { key: 'NOT_DUE', label: 'Not yet due', color: 'teal.5' },
  { key: 'DAYS_1_30', label: '1–30 days', color: 'yellow.5' },
  { key: 'DAYS_31_60', label: '31–60 days', color: 'orange.5' },
  { key: 'DAYS_61_90', label: '61–90 days', color: 'red.5' },
  { key: 'DAYS_90_PLUS', label: 'Over 90 days', color: 'red.8' },
]
