import { createTheme, type MantineColorsTuple } from '@mantine/core'

const brand: MantineColorsTuple = [
  '#e8f0ff',
  '#d0ddff',
  '#a0b9fb',
  '#6c92f7',
  '#4271f4',
  '#285df2',
  '#1c52f2',
  '#0f43d8',
  '#033bc2',
  '#0032ab',
]

export const theme = createTheme({
  primaryColor: 'brand',
  colors: { brand },
  fontFamily: "'Inter Variable', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
  headings: { fontFamily: "'Inter Variable', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" },
  defaultRadius: 'md',
  cursorType: 'pointer',
  components: {
    Button: { defaultProps: { fw: 500 } },
    Badge: { defaultProps: { variant: 'light', radius: 'sm' } },
    Paper: { defaultProps: { withBorder: true, radius: 'md' } },
    TextInput: { defaultProps: { size: 'sm' } },
  },
})
