import { useEffect, useState } from 'react'
import CareerMaterialAttachments from './CareerMaterialAttachments.jsx'

const typeNames = { CAREER_HISTORY: '이력', EXPERIENCE: '경험', CERTIFICATION: '자격증' }
const historyNames = { WORK: '근무', STUDY: '학업', TRAINING: '교육', OTHER: '기타' }

function MaterialAttachmentsDisclosure({ materialId }) {
  const [open, setOpen] = useState(false)

  return (
    <details onToggle={event => setOpen(event.currentTarget.open)}>
      <summary>첨부 파일</summary>
      {open && <CareerMaterialAttachments materialId={materialId} />}
    </details>
  )
}

export default function CareerMaterialList({ onRegister }) {
  const [result, setResult] = useState(null)
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/career-materials', { signal: controller.signal })
      .then(async response => {
        if (!response.ok) throw new Error('Material list failed')
        const payload = await response.json()
        if (payload.error !== null || !Array.isArray(payload.response)) throw new Error('Invalid list response')
        return payload.response
      })
      .then(materials => setResult({ materials }))
      .catch(() => {
        if (!controller.signal.aborted) setResult({ failed: true })
      })
    return () => controller.abort()
  }, [retry])

  return (
    <main className="registration">
      <h1>자료 목록</h1>
      {!result && <p role="status">자료를 불러오는 중이에요.</p>}
      {result?.failed && (
        <>
          <p role="alert">자료를 불러오지 못했어요.</p>
          <button type="button" onClick={() => { setResult(null); setRetry(value => value + 1) }}>다시 시도</button>
        </>
      )}
      {result?.materials?.length === 0 && <p>아직 등록한 자료가 없어요.</p>}
      {result?.materials?.map(material => (
        <article key={material.materialId}>
          <h2>{material.displayName}</h2>
          <p>{[typeNames[material.materialType], historyNames[material.historyType], material.startMonth,
            material.issuedOn].filter(Boolean).join(' · ')}</p>
          <MaterialAttachmentsDisclosure materialId={material.materialId} />
        </article>
      ))}
      <button type="button" onClick={onRegister}>자료 등록하기</button>
    </main>
  )
}
