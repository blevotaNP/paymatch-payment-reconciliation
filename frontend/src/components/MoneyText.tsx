import { Text, type TextProps } from '@mantine/core'
import { formatMoney } from '../lib/money'

interface MoneyTextProps extends TextProps {
  amount: string | null | undefined
  currency?: string
}

export function MoneyText({ amount, currency = 'PLN', ...props }: MoneyTextProps) {
  return (
    <Text component="span" size="sm" className="tabular" {...props}>
      {formatMoney(amount, currency)}
    </Text>
  )
}
