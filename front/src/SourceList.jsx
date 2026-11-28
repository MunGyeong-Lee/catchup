import { useEffect, useRef, useState } from 'react'
import CareerLibraryHeader from './CareerLibraryHeader.jsx'
import { formatSourceCreatedAt, formatSourceDate, formatSourceMonth } from './sourceDates.js'
import { sourceTypeLabels } from './sourceTypeLabels.js'
import './SourceList.css'

const SOURCE_PAGE_SIZE = 9

const historyNames = { WORK: '근무', STUDY: '학업', TRAINING: '교육', OTHER: '기타' }

async function fetchSourcePage(pageNumber, sourceType, signal) {
  const query = new URLSearchParams({ page_number: pageNumber, page_size: SOURCE_PAGE_SIZE })
  if (sourceType) query.set('sourceType', sourceType)
  const response = await fetch(`/api/sources?${query}`, {
    signal, cache: 'no-store',
  })
  if (!response.ok) throw new Error('Source list failed')
  const payload = await response.json()
  if (payload.error !== null || !Array.isArray(payload.response?.items)) throw new Error('Invalid list response')
  return payload.response
}

function SourceCard({ source, timeZone, pending, onDetail, onEdit, onDelete }) {
  return (
    <article className="career-card">
      <span className="career-card-type" data-type={source.sourceType}>{sourceTypeLabels[source.sourceType]}</span>
      <div className="career-card-title">
        <h2>
          <button className="career-card-detail" type="button" disabled={pending}
            onClick={event => onDetail(source.sourceId, event.currentTarget)}>{source.name}</button>
        </h2>
        {source.hasAttachments && (
          <span className="career-clip-indicator" role="img" aria-label="첨부 파일 있음">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m21 11.5-8.6 8.6a5 5 0 0 1-7.1-7.1l10-10a3.5 3.5 0 0 1 5 5l-10 10a2 2 0 0 1-2.8-2.8l8.6-8.6" />
            </svg>
          </span>
        )}
      </div>
      <span className="career-card-meta">
        {[historyNames[source.historyType], formatSourceMonth(source.startMonth),
          source.issuedOn && `취득일 ${formatSourceDate(source.issuedOn)}`].filter(Boolean).join(' · ')}
      </span>
      {source.createdAt && (
        <span className="career-card-meta">
          <time dateTime={source.createdAt}>{formatSourceCreatedAt(source.createdAt, timeZone)}</time>
        </span>
      )}
      <div className="career-card-actions">
        <button type="button" disabled={pending} onClick={() => onEdit(source.sourceId)}
          aria-label={`${source.name} 수정`} title="수정">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="m14 6 4 4M4 20l4.5-1 11-11a2.8 2.8 0 0 0-4-4l-11 11L4 20Z" />
          </svg>
        </button>
        <button type="button" disabled={pending} onClick={() => onDelete(source)}
          aria-label={`${source.name} 삭제`} title="삭제">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 7h16M9 7V4h6v3m3 0-1 13H7L6 7m4 4v5m4-5v5" />
          </svg>
        </button>
      </div>
    </article>
  )
}

function DeleteConfirmation({ source, deleting, onCancel, onDelete }) {
  const dialog = useRef(null)

  useEffect(() => {
    dialog.current.showModal()
  }, [])

  return (
    <dialog ref={dialog} aria-labelledby="delete-title" onCancel={event => {
      event.preventDefault()
      if (!deleting) onCancel()
    }}>
      <span className="career-card-meta">{sourceTypeLabels[source.sourceType]}</span>
      <h2 id="delete-title">‘{source.name}’ 소스를 삭제할까요?</h2>
      {source.hasAttachments && <p>첨부 파일도 함께 삭제돼요.</p>}
      <button type="button" disabled={deleting} onClick={onCancel}>취소</button>{' '}
      <button type="button" disabled={deleting} onClick={onDelete}>삭제</button>
    </dialog>
  )
}

function SourcePagination({ pageNumber, totalPages, pending, onChange }) {
  const firstPage = Math.max(1, Math.min(pageNumber - 1, totalPages - 2))
  const visiblePages = Array.from({ length: Math.min(3, totalPages) }, (_, index) => firstPage + index)

  return (
    <nav aria-label="소스 목록 페이지" className="career-pagination">
      <button type="button" aria-label="이전 페이지" disabled={pending || pageNumber === 1}
        onClick={() => onChange(pageNumber - 1)}>«</button>
      {visiblePages.map(page => (
        <button key={page} type="button" aria-label={`${page} 페이지`}
          aria-current={page === pageNumber ? 'page' : undefined} disabled={pending}
          onClick={() => { if (page !== pageNumber) onChange(page) }}>
          {page}
        </button>
      ))}
      <button type="button" aria-label="다음 페이지" disabled={pending || pageNumber === totalPages}
        onClick={() => onChange(pageNumber + 1)}>»</button>
    </nav>
  )
}

export default function SourceList({ onRegister, onDetail, onEdit, onCreateTemplate, notify }) {
  const timeZone = localStorage.getItem('catchup.timeZone')
  const [result, setResult] = useState(null)
  const [category, setCategory] = useState('')
  const [categoriesOpen, setCategoriesOpen] = useState(false)
  const [showTemplates, setShowTemplates] = useState(false)
  const [pageNumber, setPageNumber] = useState(1)
  const [retry, setRetry] = useState(0)
  const [confirming, setConfirming] = useState(null)
  const [pending, setPending] = useState(false)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    fetchSourcePage(pageNumber, category, controller.signal)
      .then(page => {
        if (controller.signal.aborted) return
        const lastPage = Math.max(1, page.totalPages)
        if (pageNumber > lastPage) {
          setPageNumber(lastPage)
          return
        }
        setResult(page)
      })
      .catch(() => {
        if (controller.signal.aborted) return
        setResult({ failed: true })
        notify('error', '소스를 불러오지 못했어요.')
      })
    return () => controller.abort()
  }, [category, pageNumber, retry, notify])

  function changePage(nextPage) {
    setResult(null)
    setPageNumber(nextPage)
  }

  async function prepareDelete(source) {
    setPending(true)
    try {
      const response = await fetch(`/api/sources/${source.sourceId}`, { cache: 'no-store' })
      if (response.status === 404) {
        notify('error', '소스를 찾을 수 없어요.')
        return
      }
      if (!response.ok) throw new Error('Unable to load attachment information')
      const payload = await response.json()
      if (payload.error !== null || !Array.isArray(payload.response?.attachments)) throw new Error('Invalid edit response')
      setConfirming({ ...source, hasAttachments: payload.response.attachments.length > 0 })
    } catch {
      notify('error', '소스를 불러오지 못했어요. 다시 시도해 주세요.')
    } finally {
      setPending(false)
    }
  }

  async function deleteSource() {
    setDeleting(true)
    try {
      const response = await fetch(`/api/sources/${confirming.sourceId}`, { method: 'DELETE' })
      if (response.status === 404) {
        notify('error', '소스를 찾을 수 없어요.')
        return
      }
      if (!response.ok || (await response.json()).error !== null) throw new Error('Delete failed')
      setResult(null)
      setRetry(value => value + 1)
      notify('success', '소스를 삭제했어요.')
    } catch {
      notify('error', '소스를 삭제하지 못했어요. 다시 시도해 주세요.')
    } finally {
      setConfirming(null)
      setDeleting(false)
    }
  }

  return (
    <div className="career-library career-browser">
      <CareerLibraryHeader />
      <div className="career-library-layout">
        <nav className="career-categories" aria-label="소스 카테고리">
          <button className="career-category-toggle" type="button" aria-expanded={categoriesOpen}
            aria-current={showTemplates ? undefined : 'page'} aria-controls="career-category-panel"
            onClick={() => {
              setShowTemplates(false)
              setCategoriesOpen(open => !open)
            }}>
            자료보관
          </button>
          <div className="career-category-panel" id="career-category-panel" inert={!categoriesOpen}>
            <div className="career-category-panel-content">
              <div className="career-category-options">
                {[['', '전체'], ...Object.entries(sourceTypeLabels)].map(([sourceType, label]) => (
                  <button key={sourceType} type="button" aria-pressed={category === sourceType} disabled={pending}
                    onClick={() => {
                      if (category === sourceType) return
                      changePage(1)
                      setCategory(sourceType)
                    }}>
                    {label}
                  </button>
                ))}
              </div>
            </div>
          </div>
          <button className="career-category-toggle" type="button" aria-current={showTemplates ? 'page' : undefined}
            onClick={() => {
              setShowTemplates(true)
              setCategoriesOpen(false)
            }}>
            템플릿
          </button>
        </nav>
        <main className="career-library-content">
          <div className="career-library-heading">
            <h1>{showTemplates ? '템플릿' : '자료보관'}</h1>
            <button className="career-register-button" type="button" disabled={!showTemplates && pending}
              onClick={showTemplates ? onCreateTemplate : onRegister}>
              {showTemplates ? '템플릿 생성' : '소스 등록'}
            </button>
          </div>
          {!showTemplates && <>
            {confirming && <DeleteConfirmation source={confirming} deleting={deleting}
              onCancel={() => setConfirming(null)} onDelete={deleteSource} />}
            {!result && <p role="status">소스를 불러오는 중이에요.</p>}
            {result?.failed && (
              <button type="button" onClick={() => { setResult(null); setRetry(value => value + 1) }}>목록 다시 불러오기</button>
            )}
            {result?.items?.length === 0 && (
              <div className="career-empty">
                <h2>{category ? `등록된 ${sourceTypeLabels[category]} 소스가 없어요.` : '등록된 소스가 없어요.'}</h2>
                {!category && <p>이력, 경험, 자격증을 등록해 커리어 기록을 모아 보세요.</p>}
              </div>
            )}
            {result?.items?.length > 0 && (
              <ul className="career-card-grid" aria-label="소스 목록">
                {result.items.map(source => (
                  <li key={source.sourceId}>
                    <SourceCard source={source} timeZone={timeZone} pending={pending}
                      onDetail={onDetail} onEdit={onEdit} onDelete={prepareDelete} />
                  </li>
                ))}
              </ul>
            )}
            {result?.totalPages > 1 && (
              <SourcePagination pageNumber={pageNumber} totalPages={result.totalPages}
                pending={pending} onChange={changePage} />
            )}
          </>}
        </main>
      </div>
    </div>
  )
}
