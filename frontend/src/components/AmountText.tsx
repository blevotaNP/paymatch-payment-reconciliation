import { Text, type TextProps } from '@mantine/core'
import { formatSignedMoney } from '../lib/money'

/** Signed transaction amount: incoming in green, outgoing in the default colour. */
export function AmountText({ amount, currency = 'PLN', ...props }: TextProps & { amount: string; currency?: string }) {
  const incoming = !amount.startsWith('-')
  return (
    <Text component="span" size="sm" fw={500} className="tabular" c={incoming ? 'teal.8' : undefined} {...props}>
      {formatSignedMoney(amount, currency)}
    </Text>
  )
}
