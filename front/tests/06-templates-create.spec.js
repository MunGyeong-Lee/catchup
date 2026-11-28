import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import { buildTemplateRequest } from '../src/templateDraft.js'

const browserUrl = process.env.CATCHUP_BROWSER_URL
const playwrightModule = process.env.CATCHUP_PLAYWRIGHT_MODULE
const chromiumPath = process.env.CATCHUP_CHROMIUM
const browserOptions = { skip: !(browserUrl && playwrightModule && chromiumPath)
  && 'Configure the disposable frontend, Playwright and Chromium' }

test('renumbers paragraphs within each question in the final display order', () => {
  const { payload, fieldErrors } = buildTemplateRequest('COVER_LETTER', ' 자기소개서 ', [
    { id: 8, paragraphs: [43, 12, 26] },
    { id: 3, paragraphs: [44] },
  ])
  assert.deepEqual(fieldErrors, {})
  assert.deepEqual(payload, {
    documentType: 'COVER_LETTER',
    title: ' 자기소개서 ',
    structure: { pages: [{ headingId: 8, paragraphIds: [1, 2, 3] }, { headingId: 3, paragraphIds: [1] }] },
  })
  assert.equal(buildTemplateRequest('COVER_LETTER', '자기소개서', [{ id: 8, paragraphs: [] }])
    .fieldErrors.paragraphIds, '문항마다 문단을 한 개 이상 추가해 주세요.')
})

test('validates grid bounds and overlap per page', () => {
  const block = (id, column, columnSpan) => ({ id, placement: { row: 1, column, columnSpan } })
  const page = blocks => ({ orientation: 'LANDSCAPE', blocks })
  assert.deepEqual(buildTemplateRequest('RESUME', '이력서', [
    page([block(1, 1, 6), block(2, 7, 6)]),
    page([block(3, 1, 12)]),
  ]).fieldErrors, {})
  assert.deepEqual(buildTemplateRequest('RESUME', '  ', [
    page([block(1, 1, 6), block(2, 6, 7), block(3, 12, 2)]),
  ]).fieldErrors, {
    title: '템플릿 제목을 입력해 주세요.',
    blocks: '블록이 겹칩니다.',
    placement: '블록이 배치 범위를 벗어났습니다.',
  })
})

async function openEditor(run) {
  const { chromium } = createRequire(import.meta.url)(playwrightModule)
  const browser = await chromium.launch({ executablePath: chromiumPath })
  try {
    const page = await browser.newPage()
    page.setDefaultTimeout(10000)
    await page.goto(browserUrl)
    const heading = page.locator('.career-library-heading')
    await heading.getByRole('button', { name: '소스 등록', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '템플릿 생성', exact: true }).count(), 0)
    await page.getByRole('navigation', { name: '소스 카테고리' })
      .getByRole('button', { name: '템플릿', exact: true }).click()
    await heading.getByRole('heading', { name: '템플릿', exact: true }).waitFor()
    await heading.getByRole('button', { name: '템플릿 생성', exact: true }).click()
    await page.getByRole('heading', { name: '템플릿 작성' }).waitFor()
    await run(page)
  } finally {
    await browser.close()
  }
}

function templateResponse(page, status) {
  return page.waitForResponse(response => new URL(response.url()).pathname === '/api/templates'
    && response.request().method() === 'POST' && response.status() === status)
}

function gridPoint(bounds, column, row) {
  return [bounds.x + (column - 0.5) * bounds.width / 12, bounds.y + (row - 0.5) * bounds.height / 16]
}

async function dragBlock(page, start, end) {
  await page.mouse.move(...start)
  await page.mouse.down()
  await page.mouse.move(...end, { steps: 5 })
  await page.mouse.up()
}

async function drawBlock(page, canvas, fromColumn, fromRow, toColumn, toRow) {
  await canvas.scrollIntoViewIfNeeded()
  const bounds = await canvas.boundingBox()
  await dragBlock(page, gridPoint(bounds, fromColumn, fromRow), gridPoint(bounds, toColumn, toRow))
}

test('registers a grid draft and keeps edits after a failed save', browserOptions, async () => {
  await openEditor(async page => {
    const save = page.getByRole('button', { name: '템플릿 저장' })
    const title = page.getByLabel('템플릿 제목')
    await save.click()
    await page.getByText('템플릿 제목을 입력해 주세요.').waitFor()
    const enteredTitle = '  그리드 브라우저 검증  '
    await title.fill(enteredTitle)
    await page.getByLabel('용지 방향').selectOption('LANDSCAPE')
    const canvas = page.getByLabel('블록 배치 용지')
    const blockInputs = page.getByLabel('블록 제목')
    assert.equal(await blockInputs.count(), 0)
    await drawBlock(page, canvas, 6, 3, 1, 1)
    const firstBlock = page.getByRole('group', { name: '블록 1' })
    await firstBlock.getByLabel('블록 제목').fill('프로젝트')
    assert.equal(await blockInputs.count(), 1)
    await canvas.getByText('1. 프로젝트', { exact: true }).waitFor()

    await drawBlock(page, canvas, 7, 1, 12, 2)
    const secondBlock = page.getByRole('group', { name: '블록 2' })
    assert.equal(await blockInputs.count(), 2)
    assert.equal(await secondBlock.getByLabel('블록 제목').inputValue(), '')
    await secondBlock.getByLabel('블록 제목').fill('사진')
    await secondBlock.getByLabel('블록 유형').selectOption('IMAGE_PLACEHOLDER')
    await canvas.getByText('2. 사진', { exact: true }).waitFor()

    for (const coordinates of [[6, 4, 6, 3], [4, 4, 8, 2]]) {
      await drawBlock(page, canvas, ...coordinates)
      await page.getByText('블록이 겹칩니다.').waitFor()
      assert.equal(await blockInputs.count(), 2)
      assert.equal(await canvas.locator('.template-preview-block').count(), 2)
    }

    const preview = canvas.locator('.template-preview-block').filter({ hasText: '1. 프로젝트' })
    await preview.getByText('1. 프로젝트', { exact: true }).click()
    assert.equal(await preview.getAttribute('data-selected'), 'true')
    await page.getByText('블록이 겹칩니다.').waitFor({ state: 'hidden' })
    assert.equal(await blockInputs.count(), 2)

    const resizeHandle = preview.getByRole('button', { name: '블록 1 크기 조절' })
    async function resize(toColumn, toRow) {
      await canvas.scrollIntoViewIfNeeded()
      const handle = await resizeHandle.boundingBox()
      await dragBlock(page, [handle.x + handle.width / 2, handle.y + handle.height / 2],
        gridPoint(await canvas.boundingBox(), toColumn, toRow))
    }

    await resize(4, 2)
    assert.equal(await preview.evaluate(block => block.style.gridRow), '1 / span 2')
    assert.equal(await preview.evaluate(block => block.style.gridColumn), '1 / span 4')
    await resize(6, 4)
    assert.equal(await preview.evaluate(block => block.style.gridRow), '1 / span 4')
    assert.equal(await preview.evaluate(block => block.style.gridColumn), '1 / span 6')
    await resize(7, 4)
    await page.getByText('블록이 겹칩니다.').waitFor()
    assert.equal(await preview.evaluate(block => block.style.gridColumn), '1 / span 6')
    assert.equal(await blockInputs.count(), 2)
    assert.equal(await firstBlock.getByLabel('블록 제목').inputValue(), '프로젝트')
    assert.equal(await secondBlock.getByLabel('블록 제목').inputValue(), '사진')

    await page.route('**/api/templates', async route => {
      await page.getByRole('button', { name: '저장 중…' }).waitFor()
      assert.ok(await page.getByRole('button', { name: '저장 중…' }).isDisabled())
      await route.fulfill({ status: 503, json: { error: 'INTERNAL_ERROR', error_message: '검증용 저장 실패' } })
    }, { times: 1 })
    const failed = templateResponse(page, 503)
    await save.click()
    await failed
    await page.getByText('검증용 저장 실패').waitFor()
    assert.equal(await title.inputValue(), enteredTitle)
    assert.equal(await secondBlock.getByLabel('블록 유형').inputValue(), 'IMAGE_PLACEHOLDER')

    const saved = templateResponse(page, 201)
    await save.click()
    const response = await saved
    const payload = response.request().postDataJSON()
    assert.equal(payload.documentType, 'RESUME')
    assert.equal(payload.title, enteredTitle)
    assert.equal(payload.structure.pages[0].orientation, 'LANDSCAPE')
    assert.deepEqual(payload.structure.pages[0].blocks.map(block => block.placement), [
      { row: 1, column: 1, rowSpan: 4, columnSpan: 6 }, { row: 1, column: 7, rowSpan: 2, columnSpan: 6 },
    ])
    const templateId = (await response.json()).response.templateId
    await page.getByText(`저장한 템플릿 번호 ${templateId}`).waitFor()
    await page.getByRole('button', { name: '← 템플릿' }).click()
    await page.getByRole('heading', { name: '템플릿', exact: true }).waitFor()
    await page.getByRole('button', { name: '템플릿 생성', exact: true }).waitFor()
    const categories = page.getByRole('navigation', { name: '소스 카테고리' })
    assert.equal(await categories.getByRole('button', { name: '템플릿', exact: true }).getAttribute('aria-current'), 'page')
    await categories.getByRole('button', { name: '자료보관', exact: true }).click()
    await page.getByRole('button', { name: '소스 등록', exact: true }).waitFor()
  })
})

test('keeps the grid and block inputs usable on a narrow screen', browserOptions, async () => {
  await openEditor(async page => {
    const viewportWidth = 390
    await page.setViewportSize({ width: viewportWidth, height: 844 })
    const canvas = page.getByLabel('블록 배치 용지')
    const settings = page.getByRole('region', { name: '블록 설정' })
    await settings.getByText('영역을 만들면 여기에 제목과 유형을 입력할 수 있어요.').waitFor()

    await drawBlock(page, canvas, 1, 1, 6, 3)
    await settings.getByLabel('블록 제목').fill('모바일 프로젝트')
    await canvas.getByText('1. 모바일 프로젝트', { exact: true }).waitFor()
    assert.ok((await settings.boundingBox()).y > (await canvas.boundingBox()).y)
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
  })
})

test('sends final paragraph numbering and shows field errors from the server', browserOptions, async () => {
  await openEditor(async page => {
    const save = page.getByRole('button', { name: '템플릿 저장' })
    await page.getByLabel('문서 유형').selectOption('COVER_LETTER')
    await page.getByLabel('템플릿 제목').fill('문단 브라우저 검증')
    const firstQuestion = page.getByRole('group', { name: '문항 1', exact: true })
    await firstQuestion.getByRole('button', { name: '문단 1 삭제' }).click()
    await save.click()
    await page.getByText('문항마다 문단을 한 개 이상 추가해 주세요.').waitFor()
    await firstQuestion.getByRole('button', { name: '문단 추가' }).click()
    await firstQuestion.getByRole('button', { name: '문단 추가' }).click()
    const paragraphItems = firstQuestion.getByRole('listitem')
    const secondKey = await paragraphItems.nth(1).getAttribute('data-paragraph-key')
    await firstQuestion.getByRole('button', { name: '문단 2 위로 이동' }).click()
    assert.equal(await paragraphItems.first().getAttribute('data-paragraph-key'), secondKey)
    assert.equal(await paragraphItems.first().locator('span').innerText(), '문단 1')
    await page.getByRole('button', { name: '문항 추가', exact: true }).click()

    await page.route('**/api/templates', route => {
      const payload = route.request().postDataJSON()
      payload.structure.pages[0].paragraphIds = [1, 3]
      return route.continue({ postData: JSON.stringify(payload) })
    }, { times: 1 })
    const rejected = templateResponse(page, 400)
    await save.click()
    assert.equal((await (await rejected).json()).error, 'VALIDATION_FAILED')
    await page.getByText('문단 번호는 문항마다 1부터 순서대로 매겨 주세요.').waitFor()
    assert.equal(await paragraphItems.count(), 2)

    const saved = templateResponse(page, 201)
    await save.click()
    const response = await saved
    const payload = response.request().postDataJSON()
    assert.equal(payload.documentType, 'COVER_LETTER')
    assert.deepEqual(payload.structure.pages.map(question => question.paragraphIds), [[1, 2], [1]])
    const templateId = (await response.json()).response.templateId
    await page.getByText(`저장한 템플릿 번호 ${templateId}`).waitFor()
  })
})
