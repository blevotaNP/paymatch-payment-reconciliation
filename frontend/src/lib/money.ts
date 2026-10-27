// Amounts travel as decimal strings ("1230.50"). Everything here works on strings or BigInt,
// never on JS numbers, so there is no float rounding anywhere in the UI.

const CURRENCY_LABELS: Record<string, string> = { PLN: 'zł' }

const NBSP = ' '

/** "1230.5" -> "1 230,50 zł" (Polish grouping with non-breaking spaces). */
export function formatMoney(amount: string | null | undefined, currency = 'PLN'): string {
  if (amount == null || amount === '') return '–'
  const negative = amount.startsWith('-')
  const [intPart, fracPart = ''] = amount.replace(/^[-+]/, '').split('.')
  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, NBSP)
  const fraction = (fracPart + '00').slice(0, 2)
  const label = CURRENCY_LABELS[currency] ?? currency
  return `${negative ? '−' : ''}${grouped},${fraction}${NBSP}${label}`
}

const AMOUNT_INPUT = /^\d{1,17}([.,]\d{1,2})?$/

/** Accepts what people type ("1 230,5", "1230.50") and returns the API format ("1230.50"), or null if invalid. */
export function normalizeAmountInput(value: string): string | null {
  const compact = value.replace(/[\s ]/g, '')
  if (!AMOUNT_INPUT.test(compact)) return null
  const [intPart, fracPart = ''] = compact.replace(',', '.').split('.')
  return `${BigInt(intPart).toString()}.${(fracPart + '00').slice(0, 2)}`
}

/** "1230.50" -> "1230,50", for putting an API amount back into an input. */
export function amountToInput(amount: string): string {
  return amount.replace('.', ',')
}

export function isZero(amount: string): boolean {
  return /^-?0+(\.0+)?$/.test(amount)
}

/** For bank transactions: "+1 230,50 zł" for money in, "−45,00 zł" for money out. */
export function formatSignedMoney(amount: string, currency = 'PLN'): string {
  return amount.startsWith('-') ? formatMoney(amount, currency) : `+${formatMoney(amount, currency)}`
}

/** "1230.5" -> 123050n. Used for adding up amounts in forms without floating point. */
export function toCents(amount: string): bigint {
  const negative = amount.startsWith('-')
  const [intPart, fracPart = ''] = amount.replace(/^[-+]/, '').split('.')
  const cents = BigInt(intPart || '0') * 100n + BigInt((fracPart + '00').slice(0, 2))
  return negative ? -cents : cents
}

/** 123050n -> "1230.50" */
export function fromCents(cents: bigint): string {
  const negative = cents < 0n
  const abs = negative ? -cents : cents
  const fraction = (abs % 100n).toString().padStart(2, '0')
  return `${negative ? '-' : ''}${abs / 100n}.${fraction}`
}

/**
 * Only for drawing charts and proportions: bar heights do not need cent precision,
 * and nothing computed from this value is ever shown as an amount.
 */
export function toChartNumber(amount: string): number {
  return Number(amount)
}

/** "123456.78" -> "123,5 tys. zł", for chart axes and compact tiles. */
export function formatMoneyCompact(value: number): string {
  if (Math.abs(value) >= 1_000_000) return `${(value / 1_000_000).toFixed(1).replace('.', ',')} mln zł`
  if (Math.abs(value) >= 1_000) return `${(value / 1_000).toFixed(1).replace('.', ',')} tys. zł`
  return `${Math.round(value)} zł`
}
