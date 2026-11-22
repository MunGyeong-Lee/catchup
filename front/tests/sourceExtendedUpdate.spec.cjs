const assert = require('node:assert/strict')
const { randomUUID } = require('node:crypto')
const { test } = require('node:test')

// Run explicitly with CATCHUP_BROWSER_URL, CATCHUP_PLAYWRIGHT_MODULE and CATCHUP_CHROMIUM set.
// The URL must point to a frontend backed by a disposable Catchup database and object store.
const browserUrl = process.env.CATCHUP_BROWSER_URL
const playwrightModule = process.env.CATCHUP_PLAYWRIGHT_MODULE
const chromiumPath = process.env.CATCHUP_CHROMIUM
const configured = browserUrl && playwrightModule && chromiumPath
const pdf = Buffer.from('%PDF-1.7\nCatchup update fixture')

test('edits the three extended source types and preserves a failed multipart draft', {
  skip: configured ? false : 'Configure the disposable frontend, Playwright and Chromium',
}, async () => {
  const { chromium } = require(playwrightModule)
  const browser = await chromium.launch({ executablePath: chromiumPath })
  const page = await browser.newPage()
  page.setDefaultTimeout(10000)
  const fixtureIds = []
  const fixtureName = 'Edit regression ' + randomUUID().slice(0, 8)
  const sources = new URL('/api/sources', browserUrl).href
  const button = name => page.getByRole('button', { name, exact: true })
  const requestFor = (sourceId, method, status) => page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/sources/' + sourceId
    && response.request().method() === method && response.status() === status)

  try {
    const multipart = new FormData()
    multipart.append('source', new Blob([JSON.stringify({
      sourceType: 'TARGET_COMPANY', name: fixtureName, companyName: 'Stored company', jobTitle: 'Stored role',
    })], { type: 'application/json' }), 'source.json')
    multipart.append('requestId', randomUUID())
    for (const filename of ['remove.pdf', 'retain.pdf']) {
      multipart.append('files', new Blob([pdf], { type: 'application/pdf' }), filename)
    }
    const seed = await page.request.post(sources, { multipart })
    assert.equal(seed.status(), 201, await seed.text())
    const sourceId = (await seed.json()).response.sourceId
    fixtureIds.push(sourceId)
    const detailUrl = sources + '/' + sourceId

    await page.addInitScript(() => {
      const originalFetch = window.fetch
      window.updateRequestIds = []
      window.fetch = (url, options) => {
        if (options?.method === 'PUT' && options.body instanceof FormData) {
          window.updateRequestIds.push(options.body.get('requestId'))
        }
        return originalFetch(url, options)
      }
    })
    await page.goto(browserUrl)
    await button(fixtureName + ' 수정').click()
    const name = page.locator('#edit-name')
    const company = page.locator('#edit-companyName')
    assert.equal(await name.inputValue(), fixtureName)
    assert.equal(await company.inputValue(), 'Stored company')
    assert.equal(await page.locator('#edit-jobTitle').inputValue(), 'Stored role')

    await company.fill('Updated company')
    const removedFile = page.locator('li').filter({ hasText: 'remove.pdf' })
    await removedFile.getByRole('button', { name: '첨부 삭제' }).click()
    await page.locator('#edit-attachments').setInputFiles({ name: 'replacement.pdf', mimeType: 'application/pdf', buffer: pdf })
    await page.route(detailUrl, route => route.fulfill({
      status: 503, json: { error: 'ATTACHMENT_STORAGE_UNAVAILABLE', error_message: '검증용 저장 실패', response: null },
    }), { times: 1 })
    const failed = requestFor(sourceId, 'PUT', 503)
    await button('수정 저장').click()
    await failed
    await page.getByText('검증용 저장 실패', { exact: true }).waitFor()
    assert.equal(await company.inputValue(), 'Updated company')
    assert.equal(await name.inputValue(), fixtureName)
    await removedFile.getByRole('button', { name: '삭제 취소' }).waitFor()
    await page.getByText('replacement.pdf', { exact: false }).waitFor()
    assert.equal((await (await page.request.get(detailUrl)).json()).response.companyName, 'Stored company')

    const saved = requestFor(sourceId, 'PUT', 200)
    await button('수정 저장').click()
    await saved
    await button(fixtureName).click()
    await page.getByText('Updated company', { exact: true }).waitFor()
    await page.getByText('retain.pdf', { exact: true }).waitFor()
    await page.getByText('replacement.pdf', { exact: true }).waitFor()
    assert.equal(await page.getByText('remove.pdf', { exact: true }).count(), 0)
    const requestIdList = await page.evaluate(() => window.updateRequestIds)
    assert.equal(requestIdList.length, 2)
    assert.ok(requestIdList[0])
    assert.equal(requestIdList[0], requestIdList[1])
    await button('← 소스 목록').click()

    for (const [sourceType, label, value] of [
      ['WRITING_STYLE', '문체 및 어투', 'Use short sentences.'],
      ['OTHER_TEXT', '기타', 'Reference notes.'],
    ]) {
      await button(fixtureName + ' 수정').click()
      await page.locator('#edit-source-type').click()
      await page.getByRole('option', { name: label, exact: true }).click()
      await page.locator('#edit-content').fill(value)
      const updated = requestFor(sourceId, 'PUT', 200)
      await button('수정 저장').click()
      const response = await updated
      assert.deepEqual(response.request().postDataJSON(), {
        sourceType, name: fixtureName, details: { content: value }, removeAttachmentIds: [],
      })
      await button(fixtureName + ' 수정').click()
      assert.equal(await page.locator('#edit-content').inputValue(), value)
      await page.getByText('retain.pdf', { exact: false }).waitFor()
      await page.getByText('replacement.pdf', { exact: false }).waitFor()
      await button('← 소스 목록').click()
    }
  } finally {
    try {
      for (const sourceId of fixtureIds) await page.request.delete(sources + '/' + sourceId)
    } finally {
      await browser.close()
    }
  }
})
