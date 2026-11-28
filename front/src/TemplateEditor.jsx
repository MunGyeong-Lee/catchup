import { useRef, useState } from 'react'
import CareerLibraryHeader from './CareerLibraryHeader.jsx'
import { buildTemplateRequest, gridColumnCount, isParagraphTemplate, placementsOverlap, templateDocumentTypes } from './templateDraft.js'
import './TemplateEditor.css'

const gridRowCount = 16
const blockTypes = { TEXT: '텍스트', IMAGE_PLACEHOLDER: '이미지 자리' }
const resizeKeys = { ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1] }

function gridCell(event) {
  const bounds = event.currentTarget.getBoundingClientRect()
  const row = Math.floor((event.clientY - bounds.top) * gridRowCount / bounds.height) + 1
  const column = Math.floor((event.clientX - bounds.left) * gridColumnCount / bounds.width) + 1
  return {
    row: Math.max(1, Math.min(gridRowCount, row)),
    column: Math.max(1, Math.min(gridColumnCount, column)),
  }
}

function gridArea(start, end) {
  return {
    row: Math.min(start.row, end.row),
    column: Math.min(start.column, end.column),
    rowSpan: Math.abs(end.row - start.row) + 1,
    columnSpan: Math.abs(end.column - start.column) + 1,
  }
}

function resizeArea(placement, end) {
  return {
    ...placement,
    rowSpan: Math.max(1, end.row - placement.row + 1),
    columnSpan: Math.max(1, end.column - placement.column + 1),
  }
}

function gridStyle(placement) {
  return { gridRow: placement.row + ' / span ' + placement.rowSpan,
    gridColumn: placement.column + ' / span ' + placement.columnSpan }
}

function moveItem(items, index, offset) {
  const nextItems = [...items]
  const [item] = nextItems.splice(index, 1)
  nextItems.splice(index + offset, 0, item)
  return nextItems
}

function GridPageEditor({ page, updatePage, createId }) {
  const dragStart = useRef(null)
  const [selection, setSelection] = useState(null)
  const [selectedBlockId, setSelectedBlockId] = useState(null)
  const [placementError, setPlacementError] = useState('')
  const selectionOverlaps = selection && overlapsBlock(selection.placement, selection.blockId)

  function overlapsBlock(placement, excludedBlockId) {
    return page.blocks.some(block => block.id !== excludedBlockId && placementsOverlap(placement, block.placement))
  }

  function addBlock(placement) {
    if (overlapsBlock(placement)) {
      setPlacementError('블록이 겹칩니다.')
      return
    }
    setPlacementError('')
    const id = createId()
    setSelectedBlockId(id)
    updatePage({ blocks: [...page.blocks, { id, type: 'TEXT', title: '', placement }] })
  }

  function resizeBlock(block, placement) {
    if (overlapsBlock(placement, block.id)) {
      setPlacementError('블록이 겹칩니다.')
      return
    }
    setPlacementError('')
    changeBlock(block.id, { placement })
  }

  function resizeWithKeyboard(event, block) {
    if (!Object.hasOwn(resizeKeys, event.key)) return
    event.preventDefault()
    const [rowOffset, columnOffset] = resizeKeys[event.key]
    const { row, column, rowSpan, columnSpan } = block.placement
    const end = {
      row: Math.min(gridRowCount, row + rowSpan - 1 + rowOffset),
      column: Math.min(gridColumnCount, column + columnSpan - 1 + columnOffset),
    }
    resizeBlock(block, resizeArea(block.placement, end))
  }

  function startSelection(event) {
    if (!event.isPrimary || event.button !== 0 || event.currentTarget.closest('fieldset:disabled')) return
    setPlacementError('')
    const cell = gridCell(event)
    const blockAtCell = page.blocks.find(block => placementsOverlap(gridArea(cell, cell), block.placement))
    const resizeHandle = event.target.closest('.template-block-resize')
    const block = resizeHandle ? page.blocks.find(item => item.id === Number(resizeHandle.dataset.blockId)) : blockAtCell
    setSelectedBlockId(block?.id ?? null)
    if (block && !resizeHandle) return

    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    dragStart.current = { cell, block, pointerId: event.pointerId }
    setSelection({ placement: block ? block.placement : gridArea(cell, cell), blockId: block?.id })
  }

  function getSelection(event) {
    const drag = dragStart.current
    if (!drag || event.pointerId !== drag.pointerId) return null
    const end = gridCell(event)
    return drag.block ? resizeArea(drag.block.placement, end) : gridArea(drag.cell, end)
  }

  function updateSelection(event) {
    const placement = getSelection(event)
    if (placement) setSelection({ placement, blockId: dragStart.current.block?.id })
  }

  function cancelSelection() {
    dragStart.current = null
    setSelection(null)
  }

  function finishSelection(event) {
    const placement = getSelection(event)
    if (!placement) return
    const { block } = dragStart.current
    cancelSelection()
    if (block) resizeBlock(block, placement)
    else addBlock(placement)
  }

  let firstFreeCell
  for (let row = 1; row <= gridRowCount && !firstFreeCell; row += 1) {
    for (let column = 1; column <= gridColumnCount && !firstFreeCell; column += 1) {
      const cell = { row, column, rowSpan: 1, columnSpan: 1 }
      if (!overlapsBlock(cell)) firstFreeCell = cell
    }
  }

  function changeBlock(blockId, changes) {
    updatePage({ blocks: page.blocks.map(block => block.id === blockId ? { ...block, ...changes } : block) })
  }

  return (
    <div className="template-grid-workspace">
      <section className="template-grid-canvas" aria-label="용지 편집">
        <div className="template-grid-toolbar">
          <div>
            <h2>배치 용지</h2>
            <p className="template-drag-hint">빈 영역을 드래그해 추가하고, 블록 모서리를 드래그해 크기를 조절하세요.</p>
          </div>
          <label className="template-orientation">
            <span>용지 방향</span>
            <select value={page.orientation} onChange={event => updatePage({ orientation: event.target.value })}>
              <option value="PORTRAIT">세로</option>
              <option value="LANDSCAPE">가로</option>
            </select>
          </label>
        </div>
        <div className="template-grid-stage">
          <div className="template-grid-preview" data-orientation={page.orientation} aria-label="블록 배치 용지"
            style={{ '--template-grid-rows': gridRowCount, '--template-grid-columns': gridColumnCount }}
            onPointerDown={startSelection} onPointerMove={updateSelection}
            onPointerUp={finishSelection} onPointerCancel={cancelSelection} onLostPointerCapture={cancelSelection}>
            {page.blocks.map((block, index) => (
              <div key={block.id} className="template-preview-block" data-type={block.type}
                data-selected={selectedBlockId === block.id} style={gridStyle(block.placement)}>
                <span>{index + 1}. {block.title || blockTypes[block.type]}</span>
                <button type="button" className="template-block-resize" data-block-id={block.id}
                  aria-label={`블록 ${index + 1} 크기 조절`} aria-keyshortcuts="ArrowUp ArrowDown ArrowLeft ArrowRight"
                  onFocus={() => setSelectedBlockId(block.id)} onKeyDown={event => resizeWithKeyboard(event, block)} />
              </div>
            ))}
            {selection && <div className="template-grid-selection" data-overlap={Boolean(selectionOverlaps)}
              style={gridStyle(selection.placement)} />}
          </div>
        </div>
        <div className="template-grid-footer">
          <button className="template-secondary" type="button" disabled={!firstFreeCell}
            onClick={() => addBlock(firstFreeCell)}>첫 빈 칸에 블록 추가</button>
          <FieldError message={placementError} />
        </div>
      </section>
      <section className="template-grid-fields" aria-label="블록 설정">
        <h2>블록 설정</h2>
        {page.blocks.length === 0 && (
          <p className="template-grid-empty">영역을 만들면 여기에 제목과 유형을 입력할 수 있어요.</p>
        )}
        {page.blocks.map((block, index) => (
          <fieldset key={block.id} className="template-block" data-selected={selectedBlockId === block.id}
            onFocus={() => setSelectedBlockId(block.id)}>
            <legend>블록 {index + 1}</legend>
            <label className="field">
              블록 유형
              <select value={block.type} onChange={event => changeBlock(block.id, { type: event.target.value })}>
                {Object.entries(blockTypes).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
              </select>
            </label>
            <label className="field">
              블록 제목
              <input value={block.title} onChange={event => changeBlock(block.id, { title: event.target.value })} />
            </label>
            <button type="button" className="template-secondary template-block-delete" onClick={() => {
              setPlacementError('')
              updatePage({ blocks: page.blocks.filter(item => item.id !== block.id) })
            }}>블록 {index + 1} 삭제</button>
          </fieldset>
        ))}
      </section>
    </div>
  )
}

function ParagraphPageEditor({ page, updatePage, createId }) {
  return (
    <>
      <p className="template-heading-slot">소제목</p>
      <ol className="template-paragraphs" aria-label="문단 순서">
        {page.paragraphs.map((paragraphId, index) => (
          <li key={paragraphId} data-paragraph-key={paragraphId}>
            <span>문단 {index + 1}</span>
            <div className="template-actions">
              <button type="button" className="template-secondary" disabled={index === 0}
                aria-label={`문단 ${index + 1} 위로 이동`}
                onClick={() => updatePage({ paragraphs: moveItem(page.paragraphs, index, -1) })}>↑</button>
              <button type="button" className="template-secondary" disabled={index === page.paragraphs.length - 1}
                aria-label={`문단 ${index + 1} 아래로 이동`}
                onClick={() => updatePage({ paragraphs: moveItem(page.paragraphs, index, 1) })}>↓</button>
              <button type="button" className="template-secondary" aria-label={`문단 ${index + 1} 삭제`}
                onClick={() => updatePage({ paragraphs: page.paragraphs.filter(id => id !== paragraphId) })}>삭제</button>
            </div>
          </li>
        ))}
      </ol>
      <button className="template-secondary" type="button" onClick={() => updatePage({
        paragraphs: [...page.paragraphs, createId()],
      })}>문단 추가</button>
    </>
  )
}

function FieldError({ id, message }) {
  return message ? <p className="template-field-error" id={id} role="alert">{message}</p> : null
}

export default function TemplateEditor({ onBack, notify }) {
  const nextId = useRef(4)
  const savingRequest = useRef(false)
  const [documentType, setDocumentType] = useState('RESUME')
  const [title, setTitle] = useState('')
  const [gridPages, setGridPages] = useState([{ id: 1, orientation: 'PORTRAIT', blocks: [] }])
  const [paragraphPages, setParagraphPages] = useState([{ id: 2, paragraphs: [3] }])
  const [fieldErrors, setFieldErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [savedId, setSavedId] = useState(null)
  const paragraphMode = isParagraphTemplate(documentType)
  const pages = paragraphMode ? paragraphPages : gridPages
  const setPages = paragraphMode ? setParagraphPages : setGridPages
  const pageLabel = paragraphMode ? '문항' : '페이지'

  function createId() {
    return nextId.current++
  }

  function clearResult() {
    setFieldErrors({})
    setSavedId(null)
  }

  function updatePages(update) {
    clearResult()
    setPages(update)
  }

  function addPage() {
    const id = createId()
    const page = paragraphMode
      ? { id, paragraphs: [createId()] }
      : { id, orientation: 'PORTRAIT', blocks: [] }
    updatePages(current => [...current, page])
  }

  async function saveTemplate(event) {
    event.preventDefault()
    if (savingRequest.current) return
    const { payload, fieldErrors: errors } = buildTemplateRequest(documentType, title, pages)
    setSavedId(null)
    setFieldErrors(errors)
    if (Object.keys(errors).length) return

    savingRequest.current = true
    setSaving(true)
    try {
      const response = await fetch('/api/templates', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
      })
      const body = await response.json()
      if (!response.ok || body.error !== null || !Number.isSafeInteger(body.response?.templateId)) {
        setFieldErrors(body.field_errors ?? {})
        notify('error', body.error_message || '템플릿을 저장하지 못했어요. 다시 시도해 주세요.')
        return
      }
      setSavedId(body.response.templateId)
      notify('success', '템플릿을 저장했어요.')
    } catch {
      notify('error', '템플릿을 저장하지 못했어요. 다시 시도해 주세요.')
    } finally {
      savingRequest.current = false
      setSaving(false)
    }
  }

  return (
    <div className="career-library career-editor">
      <CareerLibraryHeader />
      <main className="registration template-editor">
        <header className="template-editor-header">
          <button type="button" className="template-secondary" onClick={onBack} disabled={saving}>← 템플릿</button>
          <h1>템플릿 작성</h1>
        </header>
        <form onSubmit={saveTemplate} noValidate aria-busy={saving}>
          <fieldset disabled={saving}>
            <div className="template-basics">
              <div className="field">
                <label htmlFor="template-document-type">문서 유형</label>
                <select id="template-document-type" value={documentType} onChange={event => {
                  clearResult()
                  setDocumentType(event.target.value)
                }} aria-invalid={Boolean(fieldErrors.documentType)}
                aria-describedby={fieldErrors.documentType ? 'template-type-error' : undefined}>
                  {Object.entries(templateDocumentTypes).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
                </select>
                <FieldError id="template-type-error" message={fieldErrors.documentType} />
              </div>
              <div className="field">
                <label htmlFor="template-title">템플릿 제목</label>
                <input id="template-title" value={title} required onChange={event => {
                  clearResult()
                  setTitle(event.target.value)
                }} aria-invalid={Boolean(fieldErrors.title)}
                aria-describedby={fieldErrors.title ? 'template-title-error' : undefined} />
                <FieldError id="template-title-error" message={fieldErrors.title} />
              </div>
            </div>
            <div className="template-pages">
              {pages.map((page, index) => (
                <fieldset className="template-page" key={page.id}>
                  <legend>{pageLabel} {index + 1}</legend>
                  {paragraphMode
                    ? <ParagraphPageEditor page={page} createId={createId} updatePage={changes => updatePages(
                        current => current.map(item => item.id === page.id ? { ...item, ...changes } : item),
                      )} />
                    : <GridPageEditor page={page} createId={createId} updatePage={changes => updatePages(
                        current => current.map(item => item.id === page.id ? { ...item, ...changes } : item),
                      )} />}
                  <div className="template-actions">
                    <button type="button" className="template-secondary" disabled={index === 0}
                      onClick={() => updatePages(current => moveItem(current, index, -1))}
                      aria-label={`${pageLabel} ${index + 1} 위로 이동`}>↑</button>
                    <button type="button" className="template-secondary" disabled={index === pages.length - 1}
                      onClick={() => updatePages(current => moveItem(current, index, 1))}
                      aria-label={`${pageLabel} ${index + 1} 아래로 이동`}>↓</button>
                    <button type="button" className="template-secondary"
                      onClick={() => updatePages(current => current.filter(item => item.id !== page.id))}>
                      {pageLabel} {index + 1} 삭제
                    </button>
                  </div>
                </fieldset>
              ))}
              {Object.entries(fieldErrors).filter(([field]) => field !== 'title' && field !== 'documentType')
                .map(([field, message]) => <FieldError key={field} message={message} />)}
              <button type="button" className="template-secondary" onClick={addPage}>{pageLabel} 추가</button>
            </div>
            <div className="template-actions template-submit">
              {savedId !== null && <p role="status">저장한 템플릿 번호 {savedId}</p>}
              <button type="submit">{saving ? '저장 중…' : '템플릿 저장'}</button>
            </div>
          </fieldset>
        </form>
      </main>
    </div>
  )
}
