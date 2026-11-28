import { useCallback, useEffect, useRef, useState } from 'react'
import SourceAttachments from './SourceAttachments.jsx'
import SourceDetail from './SourceDetail.jsx'
import SourceList from './SourceList.jsx'
import TemplateEditor from './TemplateEditor.jsx'
import CareerLibraryHeader from './CareerLibraryHeader.jsx'
import ToastBar from './ToastBar.jsx'
import { sourceTypeLabels } from './sourceTypeLabels.js'

const sourceTypes = {
  CAREER_HISTORY: {
    fields: [
      { name: 'organization', label: '소속명', required: true },
      { name: 'historyType', label: '이력 구분', required: true, options: {
        WORK: '근무', STUDY: '학업', TRAINING: '교육', OTHER: '기타',
      } },
      { name: 'startMonth', label: '시작일', type: 'month', required: true },
      { name: 'role', label: '직무·전공·과정명' },
      { name: 'ongoing', label: '진행 중', type: 'checkbox' },
      { name: 'endMonth', label: '종료일', type: 'month' },
      { name: 'description', label: '상세 설명', type: 'textarea' },
    ],
  },
  EXPERIENCE: {
    fields: [
      { name: 'title', label: '경험 제목', required: true },
      { name: 'contribution', label: '내가 한 일', type: 'textarea', required: true },
      { name: 'startMonth', label: '시작일', type: 'month' },
      { name: 'endMonth', label: '종료일', type: 'month' },
      { name: 'organization', label: '소속 또는 팀' },
      { name: 'outcome', label: '결과와 성과', type: 'textarea' },
      { name: 'technologies', label: '사용한 기술' },
      { name: 'relatedLink', label: '관련 링크', type: 'url' },
    ],
  },
  CERTIFICATION: {
    fields: [
      { name: 'name', label: '자격증 이름', required: true },
      { name: 'issuedOn', label: '취득일', type: 'date', required: true },
      { name: 'issuer', label: '발급 기관' },
      { name: 'expiresOn', label: '만료일', type: 'date' },
      { name: 'certificateNumber', label: '자격증 번호' },
      { name: 'verificationLink', label: '확인 링크', type: 'url' },
    ],
  },
  TARGET_COMPANY: {
    fields: [
      { name: 'companyName', label: '회사명', required: true },
      { name: 'jobTitle', label: '지원 직무' },
      { name: 'content', label: '회사·공고 메모', type: 'textarea' },
      { name: 'idealCandidateProfile', label: '인재상', type: 'textarea' },
      { name: 'jobDescription', label: '직무 기술서 (JD)', type: 'textarea' },
    ],
  },
  WRITING_STYLE: {
    fields: [
      { name: 'content', label: '문체 설명 또는 예문', type: 'textarea', required: true },
    ],
  },
  OTHER_TEXT: {
    fields: [
      { name: 'content', label: '본문', type: 'textarea', required: true },
    ],
  },
}

function SourceDropdown({ id, name, required, disabled, value, options, onChange, ...accessibility }) {
  const [activeIndex, setActiveIndex] = useState(null)
  const trigger = useRef(null)
  const entries = Object.entries(options)
  const selectedIndex = entries.findIndex(([key]) => key === value)
  const open = activeIndex !== null
  const listId = `${id}-options`
  const optionId = index => `${listId}-${index}`

  function chooseOption(index) {
    onChange(entries[index][0])
    setActiveIndex(null)
  }

  function handleKeyDown(event) {
    switch (event.key) {
      case 'ArrowDown':
      case 'ArrowUp': {
        event.preventDefault()
        const direction = event.key === 'ArrowDown' ? 1 : -1
        setActiveIndex(index => index === null ? Math.max(selectedIndex, 0)
          : Math.max(0, Math.min(entries.length - 1, index + direction)))
        break
      }
      case 'Home':
      case 'End':
        if (open) {
          event.preventDefault()
          setActiveIndex(event.key === 'Home' ? 0 : entries.length - 1)
        }
        break
      case 'Enter':
      case ' ':
        if (open) {
          event.preventDefault()
          chooseOption(activeIndex)
        }
        break
      case 'Escape':
        if (open) {
          event.preventDefault()
          setActiveIndex(null)
        }
        break
    }
  }

  return (
    <div className="career-dropdown">
      {required && (
        <select className="career-dropdown-validation" tabIndex={-1} aria-hidden="true"
          required disabled={disabled} name={name} value={value}
          onChange={event => onChange(event.target.value)}
          onInvalid={event => {
            event.preventDefault()
            trigger.current?.focus()
          }}>
          {entries.map(([key, label]) => <option key={key} value={key}>{label}</option>)}
        </select>
      )}
      <button ref={trigger} className="career-dropdown-trigger" type="button" role="combobox"
        id={id} disabled={disabled} aria-required={required} aria-expanded={open}
        aria-controls={listId} aria-activedescendant={open ? optionId(activeIndex) : undefined}
        {...accessibility}
        onClick={() => setActiveIndex(index => index === null ? Math.max(selectedIndex, 0) : null)}
        onKeyDown={handleKeyDown} onBlur={() => setActiveIndex(null)}>
        {options[value] ?? entries[0][1]}
      </button>
      <ul className="career-dropdown-options" id={listId} role="listbox" hidden={!open}
        aria-labelledby={id}>
        {entries.map(([key, label], index) => (
          <li key={key} id={optionId(index)} role="option" aria-selected={key === value}
            data-active={open && index === activeIndex ? '' : undefined}
            onMouseMove={() => setActiveIndex(index)}
            onMouseDown={event => event.preventDefault()} onClick={() => chooseOption(index)}>
            {label}
          </li>
        ))}
      </ul>
    </div>
  )
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
      <label htmlFor={`${prefix}${name}`}>{label}{required && <span className="required" aria-hidden="true">*</span>}</label>
      {options ? (
        <SourceDropdown {...fieldProps} value={value ?? ''} options={{ '': '선택해 주세요', ...options }}
          onChange={nextValue => onChange(name, nextValue)} />
      ) : type === 'textarea' ? (
        <textarea {...textProps} rows={4} />
      ) : (
        <input {...textProps} type={type}
          className={(type === 'date' || type === 'month') && !value ? 'career-date-empty' : undefined} />
      )}
      {error && <small id={`${prefix}${name}-error`} className="error">{error}</small>}
    </div>
  )
}

function SourceForm({ edit, onList, onSaved, notify }) {
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
  const [submitting, setSubmitting] = useState(false)
  const submissionInProgress = useRef(false)
  const [files, setFiles] = useState([])
  const [requestId, setRequestId] = useState(null)
  const [savedSource, setSavedSource] = useState(null)
  const [removedIds, setRemovedIds] = useState([])
  const details = drafts[sourceType] ?? {}
  const prefix = edit ? 'edit-' : ''
  const sourceTypeFields = sourceTypes[sourceType].fields
  const dateFields = sourceTypeFields.filter(isDateSectionField)

  function isDateSectionField({ name, type }) {
    return type === 'month' || type === 'date' || name === 'ongoing'
  }

  function renderDetailField(field) {
    return (
      <div key={field.name} className={field.type === 'textarea' || field.name === 'ongoing' ? 'career-editor-wide' : undefined}>
        <SourceField field={field} prefix={field.name === 'name' ? `${prefix}details-` : prefix}
          value={details[field.name]} error={fieldErrors[validationFieldName(field.name)]} onChange={changeField}
          disabled={field.name === 'endMonth' && Boolean(details.ongoing)} />
      </div>
    )
  }

  function changeSourceType(nextSourceType) {
    setSourceType(nextSourceType)
    setRequestId(null)
    setFieldErrors({})
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
  }

  async function submitSource(event) {
    event.preventDefault()
    if (submissionInProgress.current) return

    const oversizedFile = files.find(file => file.size === 0 || file.size > 20 * 1024 * 1024)
    if (oversizedFile || files.reduce((total, file) => total + file.size, 0) > 100 * 1024 * 1024) {
      const message = oversizedFile
        ? `${oversizedFile.name}: 비어 있거나 파일당 20MiB를 초과합니다.`
        : '첨부 파일 합계는 100MiB 이하여야 합니다.'
      setFieldErrors({ attachments: message })
      notify('error', message)
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

    submissionInProgress.current = true
    setSubmitting(true)
    setFieldErrors({})
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
        notify('error', payload?.error_message ?? '소스를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.')
        return
      }
      const registered = payload?.response
      if (response.status !== (edit ? 200 : 201) || payload?.error !== null || !registered?.sourceId) {
        throw new Error('Invalid registration response')
      }
      if (edit) {
        notify('success', '소스를 수정했어요.')
        onSaved()
        return
      }
      setSavedSource(files.length ? registered.sourceId : null)
      setFiles([])
      setRequestId(null)
      setSourceName('')
      setDrafts(previous => ({ ...previous, [sourceType]: {} }))
      notify('success', `${sourceTypeLabels[sourceType]} 등록을 완료했어요.`)
    } catch {
      notify('error', '소스를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      submissionInProgress.current = false
      setSubmitting(false)
    }
  }

  return (
    <main className="registration">
      <header>
        <button type="button" className="career-editor-back" disabled={submitting} onClick={onList}>← 소스 목록</button>
        <h1>{edit ? '소스 수정' : '소스 등록'}</h1>
      </header>
      <form onSubmit={submitSource}
        onInvalid={() => notify('error', '필수 입력과 입력 형식을 확인해 주세요.')}>
        <fieldset disabled={submitting}>
          <div className="field">
            <label htmlFor={`${prefix}source-type`}>소스 유형</label>
            <SourceDropdown id={`${prefix}source-type`} name="sourceType" value={sourceType}
              options={sourceTypeLabels}
              onChange={changeSourceType} />
          </div>
          <div className="career-editor-fields">
            <h2 className="career-editor-wide">기본 정보</h2>
            <div className="career-editor-wide">
              <SourceField field={{ name: 'name', label: '소스 이름', required: true }} prefix={prefix}
                value={sourceName} error={fieldErrors.name} onChange={(_, value) => {
                  setSourceName(value)
                  setRequestId(null)
                  setFieldErrors(previous => ({ ...previous, name: undefined }))
                }} />
            </div>
            {sourceTypeFields.filter(field => !isDateSectionField(field)).map(renderDetailField)}
          </div>
          {dateFields.length > 0 && (
            <section className="career-editor-fields" aria-labelledby={`${prefix}source-dates-title`}>
              <h2 id={`${prefix}source-dates-title`} className="career-editor-wide">날짜</h2>
              {dateFields.filter(field => field.name !== 'ongoing').map(renderDetailField)}
              {dateFields.filter(field => field.name === 'ongoing').map(renderDetailField)}
            </section>
          )}
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
      </form>
      {savedSource && (
        <section>
          <h2>등록한 첨부 파일</h2>
          <SourceAttachments key={savedSource} sourceId={savedSource} notify={notify} />
        </section>
      )}
    </main>
  )
}


function SourceEditor({ sourceId, onList, onSaved, notify }) {
  const [result, setResult] = useState(null)
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    fetch(`/api/sources/${sourceId}`, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (controller.signal.aborted) return
        if (response.status === 404) {
          notify('error', '소스를 찾을 수 없어요.')
          setResult({ missing: true })
          return
        }
        if (!response.ok) throw new Error('Source edit load failed')
        const payload = await response.json()
        if (!payload.response || payload.error !== null) throw new Error('Invalid edit response')
        if (!controller.signal.aborted) setResult({ edit: payload.response })
      })
      .catch(() => {
        if (controller.signal.aborted) return
        setResult({ failed: true })
        notify('error', '수정할 소스를 불러오지 못했어요.')
      })
    return () => controller.abort()
  }, [sourceId, retry, notify])

  if (result?.edit) return <SourceForm edit={result.edit} onList={onList} onSaved={onSaved} notify={notify} />

  return (
    <main className="registration">
      <h1>소스 수정</h1>
      {result?.failed && (
        <button type="button" onClick={() => { setResult(null); setRetry(value => value + 1) }}>다시 시도</button>
      )}
      {!result && <p role="status">소스를 불러오는 중이에요.</p>}
      <button type="button" onClick={onList}>소스 목록</button>
    </main>
  )
}

export default function App() {
  const [showList, setShowList] = useState(true)
  const [editingId, setEditingId] = useState(null)
  const [detailId, setDetailId] = useState(null)
  const [showTemplateEditor, setShowTemplateEditor] = useState(false)
  const detailTrigger = useRef(null)
  const [toasts, setToasts] = useState([])
  const nextToastId = useRef(0)

  const notify = useCallback((kind, text) => {
    const toast = { id: ++nextToastId.current, kind, text }
    setToasts(current => current.some(item => item.kind === kind && item.text === text)
      ? current
      : [...current.slice(-2), toast])
  }, [])

  const dismissToast = useCallback(id => {
    setToasts(current => current.filter(toast => toast.id !== id))
  }, [])

  useEffect(() => {
    if (detailId === null) detailTrigger.current?.focus()
  }, [detailId])

  function openDetail(sourceId, trigger) {
    detailTrigger.current = trigger
    setDetailId(sourceId)
  }

  return (
    <>
      <div className="toast-stack" aria-label="알림">
        {toasts.map(toast => <ToastBar key={toast.id} {...toast} onDismiss={dismissToast} />)}
      </div>
      <div className="career-library career-editor" hidden={showList || Boolean(editingId)}>
        <CareerLibraryHeader />
        <SourceForm onList={() => setShowList(true)} notify={notify} />
      </div>
      {editingId ? (
        <div className="career-library career-editor">
          <CareerLibraryHeader />
          <SourceEditor key={editingId} sourceId={editingId}
            onList={() => setEditingId(null)}
            onSaved={() => setEditingId(null)} notify={notify} />
        </div>
      ) : showList && (
        <div hidden={detailId !== null || showTemplateEditor}>
          <SourceList notify={notify} onDetail={openDetail} onEdit={setEditingId}
            onRegister={() => setShowList(false)} onCreateTemplate={() => setShowTemplateEditor(true)} />
        </div>
      )}
      {detailId !== null && (
        <SourceDetail key={detailId} sourceId={detailId} sourceTypes={sourceTypes} onList={() => setDetailId(null)} />
      )}
      {showTemplateEditor && <TemplateEditor onBack={() => setShowTemplateEditor(false)} notify={notify} />}
    </>
  )
}
