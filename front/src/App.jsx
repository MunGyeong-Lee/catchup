import { useEffect, useState } from 'react'
import CareerItemAttachments from './CareerItemAttachments.jsx'
import CareerItemList from './CareerItemList.jsx'
import CareerLibraryHeader from './CareerLibraryHeader.jsx'

const careerItemTypes = {
  CAREER_HISTORY: {
    label: '이력',
    fields: [
      { name: 'organization', label: '소속명', required: true },
      { name: 'historyType', label: '이력 구분', required: true, options: {
        WORK: '근무', STUDY: '학업', TRAINING: '교육', OTHER: '기타',
      } },
      { name: 'startMonth', label: '시작 연월', type: 'month', required: true },
      { name: 'role', label: '직무·전공·과정명' },
      { name: 'ongoing', label: '진행 중', type: 'checkbox' },
      { name: 'endMonth', label: '종료 연월', type: 'month' },
      { name: 'description', label: '상세 설명', type: 'textarea' },
    ],
  },
  EXPERIENCE: {
    label: '경험',
    fields: [
      { name: 'title', label: '경험 제목', required: true },
      { name: 'contribution', label: '내가 한 일', type: 'textarea', required: true },
      { name: 'startMonth', label: '시작 연월', type: 'month' },
      { name: 'endMonth', label: '종료 연월', type: 'month' },
      { name: 'organization', label: '소속 또는 팀' },
      { name: 'outcome', label: '결과와 성과', type: 'textarea' },
      { name: 'technologies', label: '사용한 기술' },
      { name: 'relatedLink', label: '관련 링크', type: 'url' },
    ],
  },
  CERTIFICATION: {
    label: '자격증',
    fields: [
      { name: 'name', label: '자격증 이름', required: true },
      { name: 'issuedOn', label: '취득일', type: 'date', required: true },
      { name: 'issuer', label: '발급 기관' },
      { name: 'expiresOn', label: '만료일', type: 'date' },
      { name: 'certificateNumber', label: '자격증 번호' },
      { name: 'verificationLink', label: '확인 링크', type: 'url' },
    ],
  },
}

function CareerItemField({ field, value, error, onChange, disabled, prefix }) {
  const { name, label, required, options, type = 'text' } = field
  const fieldProps = {
    id: `${prefix}${name}`,
    name,
    required,
    disabled,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': error ? `${prefix}${name}-error` : undefined,
  }
  const textProps = { ...fieldProps, value: value ?? '', onChange: event => onChange(name, event.target.value) }

  if (type === 'checkbox') {
    return (
      <label className="checkbox" htmlFor={`${prefix}${name}`}>
        <input {...fieldProps} type="checkbox" checked={value ?? false}
          onChange={event => onChange(name, event.target.checked)} />
        {label}
      </label>
    )
  }

  return (
    <div className="field">
      <label htmlFor={`${prefix}${name}`}>{label}{required && <span className="required"> 필수</span>}</label>
      {options ? (
        <select {...textProps}>
          <option value="">선택해 주세요</option>
          {Object.entries(options).map(([option, title]) => <option key={option} value={option}>{title}</option>)}
        </select>
      ) : type === 'textarea' ? (
        <textarea {...textProps} rows={4} />
      ) : (
        <input {...textProps} type={type} />
      )}
      {error && <small id={`${prefix}${name}-error`} className="error">{error}</small>}
    </div>
  )
}

function CareerItemForm({ edit, onList, onSaved }) {
  const [careerItemType, setCareerItemType] = useState(edit?.careerItemType ?? 'CAREER_HISTORY')
  const [drafts, setDrafts] = useState(() => edit ? { [edit.careerItemType]: edit.details } : {})
  const [fieldErrors, setFieldErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [files, setFiles] = useState([])
  const [requestId, setRequestId] = useState(null)
  const [savedCareerItem, setSavedCareerItem] = useState(null)
  const [removedIds, setRemovedIds] = useState([])
  const details = drafts[careerItemType] ?? {}
  const prefix = edit ? 'edit-' : ''

  function changeCareerItemType(event) {
    setCareerItemType(event.target.value)
    setRequestId(null)
    setFieldErrors({})
    setNotice(null)
  }

  function changeField(name, value) {
    setRequestId(null)
    setDrafts(previous => {
      const updatedDetails = { ...previous[careerItemType], [name]: value }
      if (name === 'ongoing' && value) delete updatedDetails.endMonth
      return { ...previous, [careerItemType]: updatedDetails }
    })
    setFieldErrors(previous => ({ ...previous, [name]: undefined }))
    setNotice(null)
  }

  async function submitCareerItem(event) {
    event.preventDefault()
    if (submitting) return

    const oversizedFile = files.find(file => file.size === 0 || file.size > 20 * 1024 * 1024)
    if (oversizedFile || files.reduce((total, file) => total + file.size, 0) > 100 * 1024 * 1024) {
      const message = oversizedFile
        ? `${oversizedFile.name}: 비어 있거나 파일당 20MiB를 초과합니다.`
        : '첨부 파일 합계는 100MiB 이하여야 합니다.'
      setFieldErrors({ attachments: message })
      return
    }
    const json = JSON.stringify(edit ? { careerItemType, details, removeAttachmentIds: removedIds } : { careerItemType, details })
    let body = json
    if (files.length) {
      const uploadId = requestId ?? crypto.randomUUID()
      setRequestId(uploadId)
      body = new FormData()
      body.append('careerItem', new Blob([json], { type: 'application/json' }))
      body.append('requestId', uploadId)
      for (const file of files) body.append('files', file)
    }

    setSubmitting(true)
    setFieldErrors({})
    setNotice(null)
    try {
      const response = await fetch(edit ? `/api/career-items/${edit.careerItemId}` : '/api/career-items', {
        method: edit ? 'PUT' : 'POST',
        headers: files.length ? undefined : { 'Content-Type': 'application/json' },
        body,
      })
      const payload = await response.json().catch(() => null)
      if (!response.ok) {
        const fields = payload?.field_errors ?? (response.status === 413
          ? { attachments: payload?.error_message ?? '첨부 파일 크기 제한을 초과했어요.' } : {})
        setFieldErrors(fields)
        setNotice({ kind: 'error', text: payload?.error_message ?? '자료를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.' })
        return
      }
      const registered = payload?.response
      if (response.status !== (edit ? 200 : 201) || payload?.error !== null || !registered?.careerItemId) {
        throw new Error('Invalid registration response')
      }
      if (edit) {
        onSaved()
        return
      }
      setSavedCareerItem(files.length ? registered.careerItemId : null)
      setFiles([])
      setRequestId(null)
      setDrafts(previous => ({ ...previous, [careerItemType]: {} }))
      setNotice({ kind: 'success', text: `${careerItemTypes[careerItemType].label} 등록을 완료했어요.` })
    } catch {
      setNotice({ kind: 'error', text: '자료를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.' })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="registration">
      <header>
        {!edit && <p className="brand">캐쳡</p>}
        {edit && <button type="button" className="career-editor-back" disabled={submitting} onClick={onList}>← 자료 목록</button>}
        <h1>{edit ? '취업 서류 자료 수정' : '취업 서류 자료 등록'}</h1>
        {!edit && (
          <>
            <p>이력, 경험, 자격증을 한 건씩 기록해 두세요.</p>
            <button type="button" disabled={submitting} onClick={onList}>자료 목록</button>
          </>
        )}
      </header>
      <form onSubmit={submitCareerItem}>
        <fieldset disabled={submitting}>
          {edit ? (
            <fieldset className="career-editor-type">
              <legend>자료 유형</legend>
              <div className="career-editor-options">
                {Object.entries(careerItemTypes).map(([value, type]) => (
                  <label key={value}>
                    <input type="radio" name="careerItemType" value={value}
                      checked={careerItemType === value} onChange={changeCareerItemType} />
                    {type.label}
                  </label>
                ))}
              </div>
            </fieldset>
          ) : (
            <div className="field">
              <label htmlFor={`${prefix}careerItemType`}>자료 유형</label>
              <select id={`${prefix}careerItemType`} value={careerItemType} onChange={changeCareerItemType}>
                {Object.entries(careerItemTypes).map(([value, type]) => (
                  <option key={value} value={value}>{type.label}</option>
                ))}
              </select>
            </div>
          )}
          <div className={edit ? 'career-editor-fields' : 'career-registration-fields'}>
            {edit && <h2 className="career-editor-wide">기본 정보</h2>}
            {careerItemTypes[careerItemType].fields.map(field => (
              <div key={field.name} className={field.type === 'textarea' ? 'career-editor-wide' : undefined}>
                  <CareerItemField field={field} prefix={prefix} value={details[field.name]}
                error={fieldErrors[field.name]} onChange={changeField}
                disabled={field.name === 'endMonth' && Boolean(details.ongoing)} />
                </div>
            ))}
          </div>
          <div className={edit ? 'career-editor-files' : undefined}>
            {edit && <h2>첨부 파일</h2>}
            {edit?.attachments.length > 0 && (
              <div className="field">
                <p>기존 첨부 파일</p>
                <ul className="attachment-list">
                  {edit.attachments.map(file => {
                    const removed = removedIds.includes(file.attachmentId)
                    return (
                      <li key={file.attachmentId}>
                        {file.name}{' '}
                        <button type="button" onClick={() => {
                          setRemovedIds(previous => removed
                            ? previous.filter(id => id !== file.attachmentId)
                            : [...previous, file.attachmentId])
                          setRequestId(null)
                          setFieldErrors(previous => ({ ...previous, removeAttachmentIds: undefined }))
                        }}>{removed ? '삭제 취소' : '첨부 삭제'}</button>
                      </li>
                    )
                  })}
                </ul>
                {fieldErrors.removeAttachmentIds && <small role="alert">{fieldErrors.removeAttachmentIds}</small>}
              </div>
            )}
            <div className="field">
              {!edit && <label htmlFor={`${prefix}attachments`}>첨부 파일</label>}
              <input id={`${prefix}attachments`} type="file" multiple
                className={edit ? 'career-attachment-input' : undefined}
                aria-describedby={`${prefix}attachment-limits`}
                accept="image/jpeg,image/png,image/webp,application/pdf"
                onChange={event => {
                  const selected = Array.from(event.target.files)
                  setFiles(previous => [...previous, ...selected])
                  setRequestId(null)
                  setFieldErrors(previous => ({ ...previous, attachments: undefined }))
                  event.target.value = ''
                }} />
              <div className={edit ? 'career-attachment-picker' : undefined}>
                {edit && <label htmlFor={`${prefix}attachments`}>파일 선택</label>}
                <small id={`${prefix}attachment-limits`}>JPEG, PNG, WebP, PDF · 파일당 20MiB, 첨부 합계 100MiB</small>
              </div>
              {fieldErrors.attachments && <small className="error" role="alert">{fieldErrors.attachments}</small>}
              {files.length > 0 && (
                <ul className="attachment-list">
                  {files.map((file, index) => (
                    <li key={`${index}-${file.name}`}>
                      {file.name}{' '}
                      <button type="button" onClick={() => {
                        setFiles(previous => previous.filter((_, position) => position !== index))
                        setRequestId(null)
                        setFieldErrors(previous => ({ ...previous, attachments: undefined }))
                      }}>선택 해제</button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </fieldset>
        <div className={edit ? 'career-editor-actions' : undefined}>
          {edit && <button className="career-editor-cancel" type="button" disabled={submitting} onClick={onList}>수정 취소</button>}
          <button type="submit" disabled={submitting}>{submitting ? '저장 중…' : edit ? '수정 저장' : '자료 등록'}</button>
        </div>
        {notice && (
          <p className={notice.kind} role={notice.kind === 'error' ? 'alert' : 'status'}>{notice.text}</p>
        )}
      </form>
      {savedCareerItem && (
        <section>
          <h2>등록한 첨부 파일</h2>
          <CareerItemAttachments key={savedCareerItem} careerItemId={savedCareerItem} />
        </section>
      )}
    </main>
  )
}


function CareerItemEditor({ careerItemId, onList, onSaved }) {
  const [result, setResult] = useState(null)
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    fetch(`/api/career-items/${careerItemId}`, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (response.status === 404) {
          setResult({ missing: true })
          return
        }
        if (!response.ok) throw new Error('Career item edit load failed')
        const payload = await response.json()
        if (!payload.response || payload.error !== null) throw new Error('Invalid edit response')
        setResult({ edit: payload.response })
      })
      .catch(() => {
        if (!controller.signal.aborted) setResult({ failed: true })
      })
    return () => controller.abort()
  }, [careerItemId, retry])

  if (result?.edit) return <CareerItemForm edit={result.edit} onList={onList} onSaved={onSaved} />

  return (
    <main className="registration">
      <h1>취업 서류 자료 수정</h1>
      {result?.missing ? <p role="alert">자료를 찾을 수 없어요.</p> : result?.failed ? (
        <>
          <p role="alert">수정할 자료를 불러오지 못했어요.</p>
          <button type="button" onClick={() => { setResult(null); setRetry(value => value + 1) }}>다시 시도</button>
        </>
      ) : <p role="status">자료를 불러오는 중이에요.</p>}
      <button type="button" onClick={onList}>자료 목록</button>
    </main>
  )
}

export default function App() {
  const [showList, setShowList] = useState(true)
  const [editingId, setEditingId] = useState(null)
  const [listNotice, setListNotice] = useState(null)

  return (
    <>
      <div hidden={showList || Boolean(editingId)}>
        <CareerItemForm onList={() => { setListNotice(null); setShowList(true) }} />
      </div>
      {editingId ? (
        <div className="career-library career-editor">
          <CareerLibraryHeader />
          <CareerItemEditor key={editingId} careerItemId={editingId}
            onList={() => setEditingId(null)}
            onSaved={() => { setListNotice('자료를 수정했어요.'); setEditingId(null) }} />
        </div>
      ) : showList && (
        <CareerItemList notice={listNotice} onEdit={id => { setListNotice(null); setEditingId(id) }}
          onRegister={() => { setListNotice(null); setShowList(false) }} />
      )}
    </>
  )
}
