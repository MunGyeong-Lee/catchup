import { useEffect, useState } from 'react'
import SourceAttachments from './SourceAttachments.jsx'
import SourceList from './SourceList.jsx'
import CareerLibraryHeader from './CareerLibraryHeader.jsx'

const sourceTypes = {
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

function SourceField({ field, value, error, onChange, disabled, prefix }) {
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

function SourceForm({ edit, onList, onSaved }) {
  const [sourceType, setSourceType] = useState(edit?.sourceType ?? 'CAREER_HISTORY')
  const [sourceName, setSourceName] = useState(edit?.name ?? '')
  const [drafts, setDrafts] = useState(() => {
    if (!edit) return {}
    const storedFields = sourceTypes[edit.sourceType].fields.flatMap(({ name }) => {
        const value = edit[name === 'name' ? 'certificateName' : name]
      return value == null ? [] : [[name, value]]
    })
    return { [edit.sourceType]: Object.fromEntries(storedFields) }
  })
  const [fieldErrors, setFieldErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [files, setFiles] = useState([])
  const [requestId, setRequestId] = useState(null)
  const [savedSource, setSavedSource] = useState(null)
  const [removedIds, setRemovedIds] = useState([])
  const details = drafts[sourceType] ?? {}
  const prefix = edit ? 'edit-' : ''

  function changeSourceType(event) {
    setSourceType(event.target.value)
    setRequestId(null)
    setFieldErrors({})
    setNotice(null)
  }

  function validationFieldName(name) {
    return name === 'name' ? (edit ? 'details.name' : 'certificateName') : name
  }

  function changeField(name, value) {
    setRequestId(null)
    setDrafts(previous => {
      const updatedDetails = { ...previous[sourceType], [name]: value }
      if (name === 'ongoing' && value) delete updatedDetails.endMonth
      return { ...previous, [sourceType]: updatedDetails }
    })
    setFieldErrors(previous => ({ ...previous, [validationFieldName(name)]: undefined }))
    setNotice(null)
  }

  async function submitSource(event) {
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
    const source = { sourceType, name: sourceName }
    const registrationFields = Object.fromEntries(Object.entries(details).map(([name, value]) => [
      name === 'name' ? 'certificateName' : name, value,
    ]))
    const json = JSON.stringify(edit
      ? { ...source, details, removeAttachmentIds: removedIds }
      : { ...source, ...registrationFields })
    let body = json
    if (files.length) {
      const uploadId = requestId ?? crypto.randomUUID()
      setRequestId(uploadId)
      body = new FormData()
      body.append('source', new Blob([json], { type: 'application/json' }))
      body.append('requestId', uploadId)
      for (const file of files) body.append('files', file)
    }

    setSubmitting(true)
    setFieldErrors({})
    setNotice(null)
    try {
      const response = await fetch(edit ? `/api/sources/${edit.sourceId}` : '/api/sources', {
        method: edit ? 'PUT' : 'POST',
        headers: files.length ? undefined : { 'Content-Type': 'application/json' },
        body,
      })
      const payload = await response.json().catch(() => null)
      if (!response.ok) {
        const fields = payload?.field_errors ?? (response.status === 413
          ? { attachments: payload?.error_message ?? '첨부 파일 크기 제한을 초과했어요.' } : {})
        setFieldErrors(fields)
        setNotice({ kind: 'error', text: payload?.error_message ?? '소스를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.' })
        return
      }
      const registered = payload?.response
      if (response.status !== (edit ? 200 : 201) || payload?.error !== null || !registered?.sourceId) {
        throw new Error('Invalid registration response')
      }
      if (edit) {
        onSaved()
        return
      }
      setSavedSource(files.length ? registered.sourceId : null)
      setFiles([])
      setRequestId(null)
      setSourceName('')
      setDrafts(previous => ({ ...previous, [sourceType]: {} }))
      setNotice({ kind: 'success', text: `${sourceTypes[sourceType].label} 등록을 완료했어요.` })
    } catch {
      setNotice({ kind: 'error', text: '소스를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.' })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="registration">
      <header>
        <button type="button" className="career-editor-back" disabled={submitting} onClick={onList}>← 소스 목록</button>
        <h1>{edit ? '취업 서류 소스 수정' : '취업 서류 소스 등록'}</h1>
      </header>
      <form onSubmit={submitSource}>
        <fieldset disabled={submitting}>
          <fieldset className="career-editor-type">
            <legend>소스 유형</legend>
            <div className="career-editor-options">
              {Object.entries(sourceTypes).map(([value, type]) => (
                <label key={value}>
                  <input type="radio" name="sourceType" value={value}
                    checked={sourceType === value} onChange={changeSourceType} />
                  {type.label}
                </label>
              ))}
            </div>
          </fieldset>
          <div className="career-editor-fields">
            <h2 className="career-editor-wide">기본 정보</h2>
            <div className="career-editor-wide">
              <SourceField field={{ name: 'name', label: '소스 이름', required: true }} prefix={prefix}
                value={sourceName} error={fieldErrors.name} onChange={(_, value) => {
                  setSourceName(value)
                  setRequestId(null)
                  setFieldErrors(previous => ({ ...previous, name: undefined }))
                  setNotice(null)
                }} />
            </div>
            {sourceTypes[sourceType].fields.map(field => (
              <div key={field.name} className={field.type === 'textarea' ? 'career-editor-wide' : undefined}>
                  <SourceField field={field} prefix={field.name === 'name' ? `${prefix}details-` : prefix}
                value={details[field.name]}
                error={fieldErrors[validationFieldName(field.name)]} onChange={changeField}
                disabled={field.name === 'endMonth' && Boolean(details.ongoing)} />
                </div>
            ))}
          </div>
          <div className="career-editor-files">
            <h2>첨부 파일</h2>
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
              <input id={`${prefix}attachments`} type="file" multiple
                className="career-attachment-input"
                aria-describedby={`${prefix}attachment-limits`}
                accept="image/jpeg,image/png,image/webp,application/pdf"
                onChange={event => {
                  const selected = Array.from(event.target.files)
                  setFiles(previous => [...previous, ...selected])
                  setRequestId(null)
                  setFieldErrors(previous => ({ ...previous, attachments: undefined }))
                  event.target.value = ''
                }} />
              <div className="career-attachment-picker">
                <label htmlFor={`${prefix}attachments`}>파일 선택</label>
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
        <div className="career-editor-actions">
          <button className="career-editor-cancel" type="button" disabled={submitting} onClick={onList}>{edit ? '수정 취소' : '등록 취소'}</button>
          <button type="submit" disabled={submitting}>{submitting ? '저장 중…' : edit ? '수정 저장' : '소스 등록'}</button>
        </div>
        {notice && (
          <p className={notice.kind} role={notice.kind === 'error' ? 'alert' : 'status'}>{notice.text}</p>
        )}
      </form>
      {savedSource && (
        <section>
          <h2>등록한 첨부 파일</h2>
          <SourceAttachments key={savedSource} sourceId={savedSource} />
        </section>
      )}
    </main>
  )
}


function SourceEditor({ sourceId, onList, onSaved }) {
  const [result, setResult] = useState(null)
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    fetch(`/api/sources/${sourceId}`, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (response.status === 404) {
          setResult({ missing: true })
          return
        }
        if (!response.ok) throw new Error('Source edit load failed')
        const payload = await response.json()
        if (!payload.response || payload.error !== null) throw new Error('Invalid edit response')
        setResult({ edit: payload.response })
      })
      .catch(() => {
        if (!controller.signal.aborted) setResult({ failed: true })
      })
    return () => controller.abort()
  }, [sourceId, retry])

  if (result?.edit) return <SourceForm edit={result.edit} onList={onList} onSaved={onSaved} />

  return (
    <main className="registration">
      <h1>취업 서류 소스 수정</h1>
      {result?.missing ? <p role="alert">소스를 찾을 수 없어요.</p> : result?.failed ? (
        <>
          <p role="alert">수정할 소스를 불러오지 못했어요.</p>
          <button type="button" onClick={() => { setResult(null); setRetry(value => value + 1) }}>다시 시도</button>
        </>
      ) : <p role="status">소스를 불러오는 중이에요.</p>}
      <button type="button" onClick={onList}>소스 목록</button>
    </main>
  )
}

export default function App() {
  const [showList, setShowList] = useState(true)
  const [editingId, setEditingId] = useState(null)
  const [listNotice, setListNotice] = useState(null)

  return (
    <>
      <div className="career-library career-editor" hidden={showList || Boolean(editingId)}>
        <CareerLibraryHeader />
        <SourceForm onList={() => { setListNotice(null); setShowList(true) }} />
      </div>
      {editingId ? (
        <div className="career-library career-editor">
          <CareerLibraryHeader />
          <SourceEditor key={editingId} sourceId={editingId}
            onList={() => setEditingId(null)}
            onSaved={() => { setListNotice('소스를 수정했어요.'); setEditingId(null) }} />
        </div>
      ) : showList && (
        <SourceList notice={listNotice} onEdit={id => { setListNotice(null); setEditingId(id) }}
          onRegister={() => { setListNotice(null); setShowList(false) }} />
      )}
    </>
  )
}
