import { useEffect, useState } from 'react'
import './ToastBar.css'

export default function ToastBar({ id, kind, text, onDismiss }) {
  const [leaving, setLeaving] = useState(false)
  const isError = kind === 'error'

  useEffect(() => {
    const timer = window.setTimeout(() => setLeaving(true), 5000)
    return () => window.clearTimeout(timer)
  }, [])

  return (
    <div className="toast-bar" data-kind={kind} data-leaving={leaving || undefined}
      onAnimationEnd={event => {
        if (event.animationName === 'toast-exit') onDismiss(id)
      }}>
      <svg viewBox="0 0 24 24" className="toast-bar-icon" aria-hidden="true">
        <circle cx="12" cy="12" r="9" />
        {isError ? <path d="M12 7.5v5m0 3h.01" /> : <path d="m8 12 2.5 2.5L16 9" />}
      </svg>
      <p role={isError ? 'alert' : 'status'}>{text}</p>
      <button type="button" onClick={() => onDismiss(id)} aria-label="알림 닫기">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 7 10 10M17 7 7 17" /></svg>
      </button>
    </div>
  )
}
