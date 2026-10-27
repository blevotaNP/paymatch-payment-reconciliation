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
