// The calendar values are already in display order and carry no time zone.
export function formatSourceMonth(value) {
  if (!value) return ''
  const [year, month] = value.split('-')
  return `${year}년 ${Number(month)}월`
}

export function formatSourceDate(value) {
  if (!value) return ''
  const [year, month, day] = value.split('-')
  return `${year}년 ${Number(month)}월 ${Number(day)}일`
}

// Only creation timestamps represent instants. Omitting timeZone uses the browser setting.
export function formatSourceCreatedAt(value, timeZone) {
  if (!value) return ''
  const createdAt = new Date(value)
  const options = { dateStyle: 'medium', timeStyle: 'short' }
  try {
    return new Intl.DateTimeFormat('ko-KR', { ...options, timeZone: timeZone || undefined }).format(createdAt)
  } catch (error) {
    if (!(error instanceof RangeError) || Number.isNaN(createdAt.getTime())) return ''
    return new Intl.DateTimeFormat('ko-KR', options).format(createdAt)
  }
}
