// Same rules as the backend validators, so the user sees mistakes before submitting.

const NIP_WEIGHTS = [6, 5, 7, 2, 3, 4, 5, 6, 7]

export function normalizeNip(value: string): string {
  return value.replace(/[\s-]/g, '').toUpperCase().replace(/^PL/, '')
}

export function isValidNip(value: string): boolean {
  const nip = normalizeNip(value)
  if (!/^\d{10}$/.test(nip)) return false
  const sum = NIP_WEIGHTS.reduce((acc, weight, i) => acc + weight * Number(nip[i]), 0)
  const checksum = sum % 11
  return checksum !== 10 && checksum === Number(nip[9])
}

export function normalizeIban(value: string): string {
  const cleaned = value.replace(/[\s-]/g, '').toUpperCase()
  return /^\d{26}$/.test(cleaned) ? `PL${cleaned}` : cleaned
}

export function isValidIban(value: string): boolean {
  const iban = normalizeIban(value)
  if (!/^[A-Z]{2}\d{2}[A-Z0-9]{10,30}$/.test(iban)) return false
  if (iban.startsWith('PL') && iban.length !== 28) return false
  const rearranged = iban.slice(4) + iban.slice(0, 4)
  const digits = rearranged.replace(/[A-Z]/g, (letter) => String(letter.charCodeAt(0) - 55))
  return BigInt(digits) % 97n === 1n
}

/** "PL61109010140000071219812874" -> "PL61 1090 1014 0000 0712 1981 2874" */
export function formatIban(iban: string | null | undefined): string {
  if (!iban) return '–'
  return iban.replace(/(.{4})/g, '$1 ').trim()
}

/** "5260181596" -> "526-018-15-96" */
export function formatNip(nip: string | null | undefined): string {
  if (!nip) return '–'
  return nip.length === 10 ? `${nip.slice(0, 3)}-${nip.slice(3, 6)}-${nip.slice(6, 8)}-${nip.slice(8)}` : nip
}

/** "PL61109010140000071219812874" -> "PL61 … 2874", for narrow table columns. */
export function formatIbanShort(iban: string | null | undefined): string {
  if (!iban) return '–'
  return `${iban.slice(0, 4)} … ${iban.slice(-4)}`
}
