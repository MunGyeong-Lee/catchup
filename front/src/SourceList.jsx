import { useEffect, useRef, useState } from 'react'
import SourceAttachments from './SourceAttachments.jsx'
import CareerLibraryHeader from './CareerLibraryHeader.jsx'
import { formatSourceCreatedAt, formatSourceDate, formatSourceMonth } from './sourceDates.js'
import './SourceList.css'

const typeNames = { CAREER_HISTORY: '이력', EXPERIENCE: '경험', CERTIFICATION: '자격증' }
const historyNames = { WORK: '근무', STUDY: '학업', TRAINING: '교육', OTHER: '기타' }

function SourceAttachmentsDisclosure({ sourceId }) {
  const [open, setOpen] = useState(false)

  return (
    <details onToggle={event => setOpen(event.currentTarget.open)}>
      <summary>첨부 파일</summary>
      {open && <SourceAttachments sourceId={sourceId} />}
    </details>
  )
}

function DeleteConfirmation({ item, deleting, onCancel, onDelete }) {
  const dialog = useRef(null)

  useEffect(() => {
    dialog.current.showModal()
  }, [])

  return (
    <dialog ref={dialog} aria-labelledby="delete-title" onCancel={event => {
      event.preventDefault()
      if (!deleting) onCancel()
    }}>
      <h2 id="delete-title">‘{item.name}’ 소스를 삭제할까요?</h2>
      {item.hasAttachments && <p>첨부 파일도 함께 삭제돼요.</p>}
      <button type="button" disabled={deleting} onClick={onCancel}>취소</button>{' '}
      <button type="button" disabled={deleting} onClick={onDelete}>삭제</button>
    </dialog>
  )
}

export default function SourceList({ onRegister, onEdit, notice }) {
  const timeZone = localStorage.getItem('catchup.timeZone')
  const [result, setResult] = useState(null)
  const [pageNumber, setPageNumber] = useState(1)
  const [retry, setRetry] = useState(0)
  const [confirming, setConfirming] = useState(null)
  const [pending, setPending] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deletionNotice, setDeletionNotice] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    fetch(`/api/sources?page_number=${pageNumber}`, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error('Source list failed')
        const payload = await response.json()
        if (payload.error !== null || !Array.isArray(payload.response?.items)) throw new Error('Invalid list response')
        return payload.response
      })
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
        if (!controller.signal.aborted) setResult({ failed: true })
      })
    return () => controller.abort()
  }, [pageNumber, retry])

  function changePage(nextPage) {
    setResult(null)
    setPageNumber(nextPage)
  }

  async function prepareDelete(item) {
    setDeletionNotice(null)
    setPending(true)
    try {
      const response = await fetch(`/api/sources/${item.sourceId}`, { cache: 'no-store' })
      if (response.status === 404) {
        setDeletionNotice({ error: '소스를 찾을 수 없어요.' })
        return
      }
      if (!response.ok) throw new Error('Unable to load attachment information')
      const payload = await response.json()
      if (payload.error !== null || !Array.isArray(payload.response?.attachments)) throw new Error('Invalid edit response')
      setConfirming({ ...item, hasAttachments: payload.response.attachments.length > 0 })
    } catch {
      setDeletionNotice({ error: '소스를 불러오지 못했어요. 다시 시도해 주세요.' })
    } finally {
      setPending(false)
    }
  }

  async function deleteSource() {
    setDeleting(true)
    try {
      const response = await fetch(`/api/sources/${confirming.sourceId}`, { method: 'DELETE' })
      if (response.status === 404) {
        setDeletionNotice({ error: '소스를 찾을 수 없어요.' })
        return
      }
      if (!response.ok || (await response.json()).error !== null) throw new Error('Delete failed')
      setResult(null)
      setRetry(value => value + 1)
      setDeletionNotice({ success: '소스를 삭제했어요.' })
    } catch {
      setDeletionNotice({ error: '소스를 삭제하지 못했어요. 다시 시도해 주세요.' })
    } finally {
      setConfirming(null)
      setDeleting(false)
    }
  }

  return (
    <div className="career-library">
      <CareerLibraryHeader />
      <main className="career-library-content">
        <div className="career-library-heading">
          <h1>커리어 소스함</h1>
          <button className="career-register-button" type="button" disabled={pending} onClick={onRegister}>
            소스 등록하기
          </button>
        </div>
        {deletionNotice?.error && <p role="alert">{deletionNotice.error}</p>}
        {(deletionNotice?.success ?? (!deletionNotice && notice)) && <p role="status">{deletionNotice?.success ?? notice}</p>}
        {confirming && <DeleteConfirmation item={confirming} deleting={deleting}
          onCancel={() => setConfirming(null)} onDelete={deleteSource} />}
        {!result && <p role="status">소스를 불러오는 중이에요.</p>}
        {result?.failed && (
          <>
            <p role="alert">소스를 불러오지 못했어요.</p>
            <button type="button" onClick={() => { setResult(null); setRetry(value => value + 1) }}>다시 시도</button>
          </>
        )}
        {result?.items?.length === 0 && <p>아직 등록한 소스가 없어요.</p>}
        {result?.items?.length > 0 && (
          <ul className="career-card-grid" aria-label="소스 목록">
            {result.items.map(source => (
              <li key={source.sourceId}>
                <article className="career-card">
                  <span className="career-card-type" data-type={source.sourceType}>
                    {typeNames[source.sourceType]}
                  </span>
                  <h2>{source.name}</h2>
                  <p className="career-card-meta">
                    {[historyNames[source.historyType], formatSourceMonth(source.startMonth),
                      source.issuedOn && `취득일 ${formatSourceDate(source.issuedOn)}`].filter(Boolean).join(' · ')}
                  </p>
                  {source.createdAt && (
                    <p className="career-card-meta">
                      등록 <time dateTime={source.createdAt}>{formatSourceCreatedAt(source.createdAt, timeZone)}</time>
                    </p>
                  )}
                  <div className="career-card-actions">
                    <button type="button" disabled={pending} onClick={() => onEdit(source.sourceId)}>수정</button>
                    <button className="career-card-delete" type="button" disabled={pending} onClick={() => prepareDelete(source)}>삭제</button>
                  </div>
                  <SourceAttachmentsDisclosure sourceId={source.sourceId} />
                </article>
              </li>
            ))}
          </ul>
        )}
        {result?.totalPages > 1 && (
          <nav aria-label="소스 목록 페이지" className="career-card-actions">
            <button type="button" disabled={pending || pageNumber === 1}
              onClick={() => changePage(pageNumber - 1)}>이전</button>
            <span>{result.pageNumber} / {result.totalPages}</span>
            <button type="button" disabled={pending || pageNumber === result.totalPages}
              onClick={() => changePage(pageNumber + 1)}>다음</button>
          </nav>
        )}
      </main>
    </div>
  )
}
