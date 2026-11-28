export const templateDocumentTypes = {
  RESUME: '이력서',
  PORTFOLIO: '포트폴리오',
  CAREER_DESCRIPTION: '경력기술서',
  COVER_LETTER: '자기소개서',
}

export const gridColumnCount = 12
const maxInteger = 2147483647

export function isParagraphTemplate(documentType) {
  return documentType === 'COVER_LETTER'
}

export function isGridPlacementValid({ row, column, columnSpan, rowSpan = 1 }) {
  return [row, column, columnSpan, rowSpan].every(value => Number.isInteger(value) && value > 0 && value <= maxInteger)
    && column + columnSpan - 1 <= gridColumnCount && row + rowSpan - 1 <= maxInteger
}

export function placementsOverlap(first, second) {
  return first.row < second.row + (second.rowSpan ?? 1) && second.row < first.row + (first.rowSpan ?? 1)
    && first.column < second.column + second.columnSpan && second.column < first.column + first.columnSpan
}

function gridPagePayload(page, fieldErrors) {
  const placementList = []
  if (!page.blocks.length) fieldErrors.blocks = '페이지마다 블록을 한 개 이상 추가해 주세요.'

  for (const { placement } of page.blocks) {
    if (!isGridPlacementValid(placement)) {
      fieldErrors.placement = '블록이 배치 범위를 벗어났습니다.'
      continue
    }
    if (placementList.some(existing => placementsOverlap(placement, existing))) {
      fieldErrors.blocks = '블록이 겹칩니다.'
    }
    placementList.push(placement)
  }
  return { orientation: page.orientation, blocks: page.blocks }
}

function paragraphPagePayload(page, fieldErrors) {
  if (!page.paragraphs.length) fieldErrors.paragraphIds = '문항마다 문단을 한 개 이상 추가해 주세요.'
  return { headingId: page.id, paragraphIds: page.paragraphs.map((_, index) => index + 1) }
}

/** 현재 화면의 배치와 순서를 등록 API의 구조로 변환한다. */
export function buildTemplateRequest(documentType, title, pages) {
  const fieldErrors = {}
  if (!Object.hasOwn(templateDocumentTypes, documentType)) fieldErrors.documentType = '문서 유형을 선택해 주세요.'
  if (!title.trim()) fieldErrors.title = '템플릿 제목을 입력해 주세요.'
  if (!pages.length) fieldErrors.pages = '페이지를 한 개 이상 추가해 주세요.'

  const toPagePayload = isParagraphTemplate(documentType) ? paragraphPagePayload : gridPagePayload
  const structure = { pages: pages.map(page => toPagePayload(page, fieldErrors)) }
  return { payload: { documentType, title, structure }, fieldErrors }
}
