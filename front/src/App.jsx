import { useState } from 'react'
import CareerMaterialAttachments from './CareerMaterialAttachments.jsx'
import CareerMaterialList from './CareerMaterialList.jsx'

const materialTypes = {
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

function MaterialField({ field, value, error, onChange, disabled }) {
  const { name, label, required, options, type = 'text' } = field
  const fieldProps = {
    id: name,
    name,
    required,
    disabled,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': error ? `${name}-error` : undefined,
  }
  const textProps = { ...fieldProps, value: value ?? '', onChange: event => onChange(name, event.target.value) }

  if (type === 'checkbox') {
    return (
      <label className="checkbox" htmlFor={name}>
        <input {...fieldProps} type="checkbox" checked={value ?? false}
          onChange={event => onChange(name, event.target.checked)} />
        {label}
      </label>
    )
  }

  return (
    <div className="field">
      <label htmlFor={name}>{label}{required && <span className="required"> 필수</span>}</label>
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
      {error && <small id={`${name}-error`} className="error">{error}</small>}
    </div>
  )
}

export default function App() {
  const [materialType, setMaterialType] = useState('CAREER_HISTORY')
  const [drafts, setDrafts] = useState({})
  const [fieldErrors, setFieldErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [files, setFiles] = useState([])
  const [requestId, setRequestId] = useState(null)
  const [savedMaterial, setSavedMaterial] = useState(null)
  const [showList, setShowList] = useState(false)
  const details = drafts[materialType] ?? {}

  function changeMaterialType(event) {
    setMaterialType(event.target.value)
    setRequestId(null)
    setFieldErrors({})
    setNotice(null)
  }

  function changeField(name, value) {
    setRequestId(null)
    setDrafts(previous => {
      const updatedDetails = { ...previous[materialType], [name]: value }
      if (name === 'ongoing' && value) delete updatedDetails.endMonth
      return { ...previous, [materialType]: updatedDetails }
    })
    setFieldErrors(previous => ({ ...previous, [name]: undefined }))
    setNotice(null)
  }

  async function submitMaterial(event) {
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
    const json = JSON.stringify({ materialType, details })
    let body = json
    let headers = { 'Content-Type': 'application/json' }
    if (files.length) {
      const uploadId = requestId ?? crypto.randomUUID()
      setRequestId(uploadId)
      body = new FormData()
      body.append('material', new Blob([json], { type: 'application/json' }))
      body.append('requestId', uploadId)
      for (const file of files) body.append('files', file)
      headers = { 'X-Catchup-Upload': '1' }
    }

    setSubmitting(true)
    setFieldErrors({})
    setNotice(null)
    try {
      const response = await fetch('/api/career-materials', {
        method: 'POST',
        headers,
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
      if (response.status !== 201 || payload?.error !== null || !registered?.materialId) {
        throw new Error('Invalid registration response')
      }
      setSavedMaterial(files.length ? registered.materialId : null)
      setFiles([])
      setRequestId(null)
      setDrafts(previous => ({ ...previous, [materialType]: {} }))
      setNotice({ kind: 'success', text: `${materialTypes[materialType].label} 등록을 완료했어요.` })
    } catch {
      setNotice({ kind: 'error', text: '자료를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.' })
    } finally {
      setSubmitting(false)
    }
  }

  if (showList) return <CareerMaterialList onRegister={() => setShowList(false)} />

  return (
    <main className="registration">
      <header>
        <p className="brand">캐쳡</p>
        <h1>취업 서류 자료 등록</h1>
        <p>이력, 경험, 자격증을 한 건씩 기록해 두세요.</p>
        <button type="button" disabled={submitting} onClick={() => setShowList(true)}>자료 목록</button>
      </header>
      <form onSubmit={submitMaterial}>
        <fieldset disabled={submitting}>
          <div className="field">
            <label htmlFor="materialType">자료 유형</label>
            <select id="materialType" value={materialType} onChange={changeMaterialType}>
              {Object.entries(materialTypes).map(([value, type]) => (
                <option key={value} value={value}>{type.label}</option>
              ))}
            </select>
          </div>
          {materialTypes[materialType].fields.map(field => (
            <MaterialField key={field.name} field={field} value={details[field.name]}
              error={fieldErrors[field.name]} onChange={changeField}
              disabled={field.name === 'endMonth' && Boolean(details.ongoing)} />
          ))}
          <div className="field">
            <label htmlFor="attachments">첨부 파일</label>
            <input id="attachments" type="file" multiple
              accept="image/jpeg,image/png,image/webp,application/pdf"
              onChange={event => {
                const selected = Array.from(event.target.files)
                setFiles(previous => [...previous, ...selected])
                setRequestId(null)
                setFieldErrors(previous => ({ ...previous, attachments: undefined }))
                event.target.value = ''
              }} />
            <small>JPEG, PNG, WebP, PDF · 파일당 20MiB, 첨부 합계 100MiB</small>
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
        </fieldset>
        <button type="submit" disabled={submitting}>{submitting ? '저장 중…' : '자료 등록'}</button>
        {notice && (
          <p className={notice.kind} role={notice.kind === 'error' ? 'alert' : 'status'}>{notice.text}</p>
        )}
      </form>
      {savedMaterial && (
        <section>
          <h2>등록한 첨부 파일</h2>
          <CareerMaterialAttachments key={savedMaterial} materialId={savedMaterial} />
        </section>
      )}
    </main>
  )
}
