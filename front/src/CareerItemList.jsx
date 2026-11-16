import { useEffect, useRef, useState } from 'react'
import CareerItemAttachments from './CareerItemAttachments.jsx'
import CareerLibraryHeader from './CareerLibraryHeader.jsx'
import './CareerItemList.css'

const typeNames = { CAREER_HISTORY: '이력', EXPERIENCE: '경험', CERTIFICATION: '자격증' }
const historyNames = { WORK: '근무', STUDY: '학업', TRAINING: '교육', OTHER: '기타' }

function CareerItemAttachmentsDisclosure({ careerItemId }) {
  const [open, setOpen] = useState(false)

  return (
    <details onToggle={event => setOpen(event.currentTarget.open)}>
      <summary>첨부 파일</summary>
      {open && <CareerItemAttachments careerItemId={careerItemId} />}
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
      <h2 id="delete-title">‘{item.displayName}’ 자료를 삭제할까요?</h2>
      {item.hasAttachments && <p>첨부 파일도 함께 삭제돼요.</p>}
      <button type="button" disabled={deleting} onClick={onCancel}>취소</button>{' '}
      <button type="button" disabled={deleting} onClick={onDelete}>삭제</button>
    </dialog>
  )
}

export default function CareerItemList({ onRegister, onEdit, notice }) {
  const [result, setResult] = useState(null)
  const [retry, setRetry] = useState(0)
  const [confirming, setConfirming] = useState(null)
  const [pending, setPending] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deletionNotice, setDeletionNotice] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/career-items', { signal: controller.signal })
      .then(async response => {
        if (!response.ok) throw new Error('CareerItem list failed')
        const payload = await response.json()
        if (payload.error !== null || !Array.isArray(payload.response)) throw new Error('Invalid list response')
        return payload.response
      })
      .then(careerItems => setResult({ careerItems }))
      .catch(() => {
        if (!controller.signal.aborted) setResult({ failed: true })
      })
    return () => controller.abort()
  }, [retry])

  async function prepareDelete(item) {
    setDeletionNotice(null)
    setPending(true)
    try {
      const response = await fetch(`/api/career-items/${item.careerItemId}`, { cache: 'no-store' })
      if (response.status === 404) {
        setDeletionNotice({ error: '자료를 찾을 수 없어요.' })
        return
      }
      if (!response.ok) throw new Error('Unable to load attachment information')
      const payload = await response.json()
      if (payload.error !== null || !Array.isArray(payload.response?.attachments)) throw new Error('Invalid edit response')
      setConfirming({ ...item, hasAttachments: payload.response.attachments.length > 0 })
    } catch {
      setDeletionNotice({ error: '자료를 불러오지 못했어요. 다시 시도해 주세요.' })
    } finally {
      setPending(false)
    }
  }

  async function deleteCareerItem() {
    setDeleting(true)
    try {
      const response = await fetch(`/api/career-items/${confirming.careerItemId}`, { method: 'DELETE' })
      if (response.status === 404) {
        setDeletionNotice({ error: '자료를 찾을 수 없어요.' })
        return
      }
      if (!response.ok || (await response.json()).error !== null) throw new Error('Delete failed')
      setResult(previous => ({
        careerItems: previous.careerItems.filter(item => item.careerItemId !== confirming.careerItemId),
      }))
      setDeletionNotice({ success: '자료를 삭제했어요.' })
    } catch {
      setDeletionNotice({ error: '자료를 삭제하지 못했어요. 다시 시도해 주세요.' })
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
          <h1>커리어 자료함</h1>
          <button className="career-register-button" type="button" disabled={pending} onClick={onRegister}>
            자료 등록하기
          </button>
        </div>
        {deletionNotice?.error && <p role="alert">{deletionNotice.error}</p>}
        {(deletionNotice?.success ?? (!deletionNotice && notice)) && <p role="status">{deletionNotice?.success ?? notice}</p>}
        {confirming && <DeleteConfirmation item={confirming} deleting={deleting}
          onCancel={() => setConfirming(null)} onDelete={deleteCareerItem} />}
        {!result && <p role="status">자료를 불러오는 중이에요.</p>}
        {result?.failed && (
          <>
            <p role="alert">자료를 불러오지 못했어요.</p>
            <button type="button" onClick={() => { setResult(null); setRetry(value => value + 1) }}>다시 시도</button>
          </>
        )}
        {result?.careerItems?.length === 0 && <p>아직 등록한 자료가 없어요.</p>}
        {result?.careerItems?.length > 0 && (
          <ul className="career-card-grid" aria-label="자료 목록">
            {result.careerItems.map(careerItem => (
              <li key={careerItem.careerItemId}>
                <article className="career-card">
                  <span className="career-card-type" data-type={careerItem.careerItemType}>
                    {typeNames[careerItem.careerItemType]}
                  </span>
                  <h2>{careerItem.displayName}</h2>
                  <p className="career-card-meta">
                    {[historyNames[careerItem.historyType], careerItem.startMonth,
                      careerItem.issuedOn && `취득일 ${careerItem.issuedOn}`].filter(Boolean).join(' · ')}
                  </p>
                  <div className="career-card-actions">
                    <button type="button" disabled={pending} onClick={() => onEdit(careerItem.careerItemId)}>수정</button>
                    <button className="career-card-delete" type="button" disabled={pending} onClick={() => prepareDelete(careerItem)}>삭제</button>
                  </div>
                  <CareerItemAttachmentsDisclosure careerItemId={careerItem.careerItemId} />
                </article>
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  )
}
