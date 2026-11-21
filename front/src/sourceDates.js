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
  if (Number.isNaN(createdAt.getTime())) return ''

  const options = {
    year: 'numeric', month: 'numeric', day: 'numeric',
    hour: 'numeric', minute: '2-digit', hourCycle: 'h12',
  }
  let formatter
  try {
    formatter = new Intl.DateTimeFormat('en-US', { ...options, timeZone: timeZone || undefined })
  } catch (error) {
    if (!(error instanceof RangeError)) throw error
    formatter = new Intl.DateTimeFormat('en-US', options)
  }
  const { year, month, day, hour, minute, dayPeriod } = Object.fromEntries(
    formatter.formatToParts(createdAt).map(({ type, value }) => [type, value]),
  )
  return `${year}. ${month}. ${day}. ${hour}:${minute}${dayPeriod}`
}
