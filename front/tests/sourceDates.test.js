import assert from 'node:assert/strict'
import test from 'node:test'
import { formatSourceCreatedAt, formatSourceDate, formatSourceMonth } from '../src/sourceDates.js'

test('a single UTC instant displays on different dates across time zones', () => {
  const instant = '2026-11-21T16:30:00Z'
  assert.match(formatSourceCreatedAt(instant, 'Asia/Seoul'), /2026\. 11\. 22\./)
  assert.match(formatSourceCreatedAt(instant, 'America/Los_Angeles'), /2026\. 11\. 21\./)
  assert.equal(formatSourceCreatedAt(instant, 'Not/AZone'), formatSourceCreatedAt(instant))
})

test('calendar values display their stored parts without applying a time zone', () => {
  assert.equal(formatSourceMonth('2026-11'), '2026년 11월')
  assert.equal(formatSourceDate('2024-02-29'), '2024년 2월 29일')
})
