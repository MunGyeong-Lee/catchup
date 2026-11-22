const assert = require('node:assert/strict')
const { randomUUID } = require('node:crypto')
const { test } = require('node:test')

// Run explicitly with CATCHUP_BROWSER_URL, CATCHUP_PLAYWRIGHT_MODULE and CATCHUP_CHROMIUM set.
// Use a disposable, initially empty Catchup database and object store.
const browserUrl = process.env.CATCHUP_BROWSER_URL
const playwrightModule = process.env.CATCHUP_PLAYWRIGHT_MODULE
const chromiumPath = process.env.CATCHUP_CHROMIUM
const configured = browserUrl && playwrightModule && chromiumPath
const pdf = Buffer.from('%PDF-1.7\nCatchup delete fixture')

test('deletes extended sources and refreshes the list after cancel and retry', {
  skip: configured ? false : 'Configure the disposable frontend, Playwright and Chromium',
}, async () => {
  const { chromium } = require(playwrightModule)
  const browser = await chromium.launch({ executablePath: chromiumPath })
  const page = await browser.newPage()
  page.setDefaultTimeout(10000)
  const sources = new URL('/api/sources', browserUrl).href
  const fixtureIds = new Set()
  const button = name => page.getByRole('button', { name, exact: true })
  const dialog = page.getByRole('dialog')
  const category = name => page.getByRole('navigation', { name: '소스 카테고리' })
    .getByRole('button', { name, exact: true })

  async function createSource(sourceType, name, fields, filename) {
    const source = { sourceType, name, ...fields }
    let response
    if (filename) {
      const multipart = new FormData()
      multipart.append('source', new Blob([JSON.stringify(source)], { type: 'application/json' }), 'source.json')
      multipart.append('requestId', randomUUID())
      multipart.append('files', new Blob([pdf], { type: 'application/pdf' }), filename)
      response = await page.request.post(sources, { multipart })
    } else {
      response = await page.request.post(sources, { data: source })
    }
    assert.equal(response.status(), 201, await response.text())
    const { sourceId } = (await response.json()).response
    fixtureIds.add(sourceId)
    return { sourceId, name, url: sources + '/' + sourceId }
  }

  async function openConfirmation(source) {
    await button(source.name + ' 삭제').click()
    await dialog.getByRole('heading', { name: '‘' + source.name + '’ 소스를 삭제할까요?', exact: true }).waitFor()
  }

  function deleteResponse(source, status) {
    return page.waitForResponse(response =>
      response.url() === source.url && response.request().method() === 'DELETE' && response.status() === status)
  }

  function listResponse(sourceType, pageNumber) {
    return page.waitForResponse(response => {
      const url = new URL(response.url())
      return url.pathname === '/api/sources' && response.request().method() === 'GET' && response.ok()
        && url.searchParams.get('sourceType') === sourceType
        && url.searchParams.get('page_number') === String(pageNumber)
    })
  }

  async function confirmDeletion(source) {
    const deleted = deleteResponse(source, 200)
    await dialog.getByRole('button', { name: '삭제', exact: true }).click()
    await deleted
    fixtureIds.delete(source.sourceId)
    await dialog.waitFor({ state: 'hidden' })
    await button(source.name).waitFor({ state: 'hidden' })
    assert.equal((await page.request.get(source.url)).status(), 404)
  }

  try {
    const initial = await page.request.get(sources)
    assert.equal(initial.status(), 200)
    assert.equal((await initial.json()).response.totalElements, 0, 'Use an empty disposable database')
    const company = await createSource('TARGET_COMPANY', 'Delete company', { companyName: 'Company' }, 'removed.pdf')
    const pageOneSources = []
    for (let index = 1; index <= 9; index += 1) {
      pageOneSources.push(await createSource('TARGET_COMPANY', 'Company page fixture ' + index, { companyName: 'Company' }))
    }
    const style = await createSource('WRITING_STYLE', 'Delete style', { content: 'Style draft' })
    const note = await createSource('OTHER_TEXT', 'Delete note', { content: 'Note draft' })
    const retained = await createSource('EXPERIENCE', 'Retained source', { title: 'Experience', contribution: 'Worked' }, 'retained.pdf')
    const companyAttachment = (await (await page.request.get(company.url)).json()).response.attachments[0]
    const retainedBefore = (await (await page.request.get(retained.url)).json()).response

    await page.goto(browserUrl)
    await category('자료보관').click()
    await category('지원회사').click()
    await button('2 페이지').click()
    await openConfirmation(company)
    await dialog.getByText('첨부 파일도 함께 삭제돼요.', { exact: true }).waitFor()
    const canceledDeleteRequests = []
    const observeDelete = request => {
      if (request.method() === 'DELETE' && request.url() === company.url) canceledDeleteRequests.push(request)
    }
    page.on('request', observeDelete)
    await dialog.getByRole('button', { name: '취소', exact: true }).click()
    await dialog.waitFor({ state: 'hidden' })
    await button(company.name).waitFor()
    page.off('request', observeDelete)
    assert.equal(canceledDeleteRequests.length, 0)
    assert.equal((await page.request.get(company.url)).status(), 200)

    await openConfirmation(company)
    await page.route(company.url, route => route.fulfill({
      status: 500, json: { error: 'INTERNAL_ERROR', error_message: 'Injected failure', response: null },
    }), { times: 1 })
    const injectedFailure = deleteResponse(company, 500)
    await dialog.getByRole('button', { name: '삭제', exact: true }).click()
    await injectedFailure
    await page.getByText('소스를 삭제하지 못했어요. 다시 시도해 주세요.', { exact: true }).waitFor()
    await button(company.name).waitFor()
    const originalUrl = company.url + '/attachments/' + companyAttachment.attachmentId + '/download'
    assert.deepEqual(await (await page.request.get(originalUrl)).body(), pdf)

    await openConfirmation(company)
    const refreshedFirst = listResponse('TARGET_COMPANY', 1)
    await confirmDeletion(company)
    const refreshedPage = await refreshedFirst
    assert.equal((await refreshedPage.json()).response.items.length, 9)
    await button(pageOneSources[0].name).waitFor()
    assert.equal(await button('2 페이지').count(), 0)
    assert.equal((await page.request.get(originalUrl)).status(), 404)

    for (const [source, sourceType, label] of [
      [style, 'WRITING_STYLE', '문체 및 어투'],
      [note, 'OTHER_TEXT', '기타'],
    ]) {
      await category(label).click()
      await openConfirmation(source)
      const refresh = listResponse(sourceType, 1)
      await confirmDeletion(source)
      assert.equal((await (await refresh).json()).response.totalElements, 0)
      await page.getByRole('heading', { name: '등록된 ' + label + ' 소스가 없어요.', exact: true }).waitFor()
    }
    assert.deepEqual((await (await page.request.get(retained.url)).json()).response, retainedBefore)
    const retainedOriginal = await page.request.get(retained.url + '/attachments/'
      + retainedBefore.attachments[0].attachmentId + '/download')
    assert.equal(retainedOriginal.status(), 200)
    assert.deepEqual(await retainedOriginal.body(), pdf)

    for (const source of pageOneSources) {
      assert.equal((await page.request.delete(source.url)).status(), 200)
      fixtureIds.delete(source.sourceId)
    }
    await category('전체').click()
    await openConfirmation(retained)
    const empty = listResponse(null, 1)
    await confirmDeletion(retained)
    assert.equal((await (await empty).json()).response.totalElements, 0)
    await page.getByRole('heading', { name: '등록된 소스가 없어요.', exact: true }).waitFor()
  } finally {
    try {
      for (const sourceId of fixtureIds) await page.request.delete(sources + '/' + sourceId)
    } finally {
      await browser.close()
    }
  }
})
