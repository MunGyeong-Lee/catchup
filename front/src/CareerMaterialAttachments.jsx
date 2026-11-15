import { useEffect, useState } from 'react'

export default function CareerMaterialAttachments({ materialId }) {
  const [result, setResult] = useState(null)
  const [retry, setRetry] = useState(0)
  const endpoint = `/api/career-materials/${materialId}/attachments`

  useEffect(() => {
    const controller = new AbortController()
    fetch(endpoint, { signal: controller.signal, cache: 'no-store' })
      .then(async response => {
        if (!response.ok) throw new Error('Attachment list failed')
        const payload = await response.json()
        if (payload.error !== null || !Array.isArray(payload.response)) throw new Error('Invalid list response')
        return payload.response
      })
      .then(files => setResult({ files }))
      .catch(() => {
        if (!controller.signal.aborted) setResult({ failed: true })
      })
    return () => controller.abort()
  }, [endpoint, retry])

  if (!result) return <p role="status">첨부 파일을 불러오는 중이에요.</p>
  if (result.failed) {
    return (
      <div>
        <p role="alert">첨부 파일을 불러오지 못했어요.</p>
        <button type="button" onClick={() => { setResult(null); setRetry(value => value + 1) }}>다시 시도</button>
      </div>
    )
  }
  if (!result.files.length) return <p>첨부한 파일이 없어요.</p>

  return (
    <ul className="attachment-list">
      {result.files.map(file => (
        <li key={file.attachmentId}>
          <span>{file.name}</span>{' '}
          <a href={`${endpoint}/${file.attachmentId}/view`} target="_blank" rel="noreferrer">다시 열기</a>{' '}
          <a href={`${endpoint}/${file.attachmentId}/download`}>원본 내려받기</a>
        </li>
      ))}
    </ul>
  )
}
