import { useEffect, useRef, useState } from 'react'
import CareerLibraryHeader from './CareerLibraryHeader.jsx'
import { SourceAttachmentList } from './SourceAttachments.jsx'
import { formatSourceCreatedAt, formatSourceDate, formatSourceMonth } from './sourceDates.js'
import { sourceTypeLabels } from './sourceTypeLabels.js'

function SourceDetailValue({ field, value }) {
  if (field.options) return field.options[value] ?? value
  if (field.type === 'checkbox') return value ? '예' : '아니요'
  if (field.type === 'month') return formatSourceMonth(value)
  if (field.type === 'date') return formatSourceDate(value)
  if (field.type === 'url') {
    const link = URL.parse(value)
    if (link && ['http:', 'https:'].includes(link.protocol)) {
      return <a href={link.href} target="_blank" rel="noopener noreferrer">{value}</a>
    }
  }
  return value
}

export default function SourceDetail({ sourceId, sourceTypes, onList }) {
  const [result, setResult] = useState(null)
  const [retry, setRetry] = useState(0)
  const backButton = useRef(null)
  const timeZone = localStorage.getItem('catchup.timeZone')

  useEffect(() => {
    backButton.current.focus()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    fetch(`/api/sources/${sourceId}`, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (controller.signal.aborted) return
        if (response.status === 404) {
          setResult({ missing: true })
          return
        }
        if (!response.ok) throw new Error('Source detail load failed')
        const payload = await response.json()
        if (payload.error !== null || !payload.response || !Array.isArray(payload.response.attachments)) {
          throw new Error('Invalid source detail response')
        }
        if (!controller.signal.aborted) setResult({ source: payload.response })
      })
      .catch(() => {
        if (!controller.signal.aborted) setResult({ failed: true })
      })
    return () => controller.abort()
  }, [sourceId, retry])

  const source = result?.source
  const fieldList = sourceTypes[source?.sourceType]?.fields ?? []

  return (
    <div className="career-library career-editor">
      <CareerLibraryHeader />
      <main className="registration">
        <header>
          <button ref={backButton} type="button" className="career-editor-back" onClick={onList}>← 소스 목록</button>
          <h1>{source?.name ?? '소스 상세'}</h1>
        </header>
        {!result && <p role="status">소스를 불러오는 중이에요.</p>}
        {result?.missing && <p role="status">소스를 찾을 수 없어요.</p>}
        {result?.failed && (
          <>
            <p role="alert">소스를 불러오지 못했어요.</p>
            <button type="button" onClick={() => { setResult(null); setRetry(value => value + 1) }}>다시 시도</button>
          </>
        )}
        {source && (
          <>
            <dl className="career-detail-fields">
              <div><dt>소스 유형</dt><dd>{sourceTypeLabels[source.sourceType]}</dd></div>
              <div><dt>등록 시각</dt><dd><time dateTime={source.createdAt}>
                {formatSourceCreatedAt(source.createdAt, timeZone)}
              </time></dd></div>
              <div><dt>수정 시각</dt><dd><time dateTime={source.updatedAt}>
                {formatSourceCreatedAt(source.updatedAt, timeZone)}
              </time></dd></div>
              {fieldList.map(field => {
                const value = source[field.name === 'name' ? 'certificateName' : field.name]
                if (value == null || value === '') return null
                return (
                  <div key={field.name}>
                    <dt>{field.label}</dt>
                    <dd><SourceDetailValue field={field} value={value} /></dd>
                  </div>
                )
              })}
            </dl>
            <section className="career-editor-files" aria-labelledby="source-detail-attachments">
              <h2 id="source-detail-attachments">첨부 파일</h2>
              <SourceAttachmentList sourceId={sourceId} files={source.attachments} />
            </section>
          </>
        )}
      </main>
    </div>
  )
}
