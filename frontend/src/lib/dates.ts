// Business dates (issue/due/booking) are plain "YYYY-MM-DD" strings and are never converted to Date,
// so a user's time zone cannot shift them by a day. Timestamps are shown in Polish time.

export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) return '–'
  const [year, month, day] = isoDate.split('-')
  return `${day}.${month}.${year}`
}

const dateTimeFormat = new Intl.DateTimeFormat('pl-PL', {
  timeZone: 'Europe/Warsaw',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatDateTime(instant: string | null | undefined): string {
  if (!instant) return '–'
  return dateTimeFormat.format(new Date(instant))
}

/** Today's date in Poland as "YYYY-MM-DD". */
export function todayIso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Warsaw' }).format(new Date())
}

export function addDaysIso(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}
