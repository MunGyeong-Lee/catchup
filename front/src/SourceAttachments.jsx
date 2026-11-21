import { useEffect, useState } from 'react'
import './SourceAttachments.css'

export default function SourceAttachments({ sourceId, notify }) {
  const [result, setResult] = useState(null)
  const [retry, setRetry] = useState(0)
  const endpoint = `/api/sources/${sourceId}/attachments`

  useEffect(() => {
    const controller = new AbortController()
    fetch(endpoint, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error('Attachment list failed')
        const payload = await response.json()
        if (payload.error !== null || !Array.isArray(payload.response)) throw new Error('Invalid list response')
        return payload.response
      })
      .then(files => {
        if (!controller.signal.aborted) setResult({ files })
      })
      .catch(() => {
        if (controller.signal.aborted) return
        setResult({ failed: true })
        notify('error', '첨부 파일을 불러오지 못했어요.')
      })
    return () => controller.abort()
  }, [endpoint, retry, notify])

  if (!result) return <p role="status">첨부 파일을 불러오는 중이에요.</p>
  if (result.failed) {
    return (
      <button type="button" onClick={() => { setResult(null); setRetry(value => value + 1) }}>첨부 파일 다시 불러오기</button>
    )
  }
  if (!result.files.length) return <p>첨부한 파일이 없어요.</p>

  return (
    <ul className="career-attachments">
      {result.files.map(file => (
        <li key={file.attachmentId}>
          <span className="career-attachment-name">{file.name}</span>
          <div className="career-attachment-links">
            <a href={`${endpoint}/${file.attachmentId}/view`} target="_blank" rel="noreferrer"
              aria-label={`${file.name} 다시 열기 (새 탭)`} title="다시 열기 (새 탭)">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M13 5h6v6m0-6-9 9m0-9H6a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-4" />
              </svg>
            </a>
            <a href={`${endpoint}/${file.attachmentId}/download`}
              aria-label={`${file.name} 원본 내려받기`} title="원본 내려받기">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 4v11m-4-4 4 4 4-4M5 16v3a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-3" />
              </svg>
            </a>
          </div>
        </li>
      ))}
    </ul>
  )
}
