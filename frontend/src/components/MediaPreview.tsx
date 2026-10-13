import { useEffect } from 'react'
import { createPortal } from 'react-dom'

interface MediaPreviewProps {
  urls: string[]
  index: number
  video: boolean
  title: string
  onIndexChange: (index: number) => void
  onClose: () => void
}

export default function MediaPreview({ urls, index, video, title, onIndexChange, onClose }: MediaPreviewProps) {
  const url = urls[index]
  const many = urls.length > 1

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
      if (many && e.key === 'ArrowLeft') onIndexChange((index - 1 + urls.length) % urls.length)
      if (many && e.key === 'ArrowRight') onIndexChange((index + 1) % urls.length)
    }
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onKey)
    return () => {
      document.body.style.overflow = overflow
      window.removeEventListener('keydown', onKey)
    }
  }, [index, many, urls.length, onClose, onIndexChange])

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={onClose}
      className="fixed inset-0 z-50 flex animate-pop-in flex-col items-center justify-center gap-4 bg-ink/90 p-4 backdrop-blur-sm"
    >
      <div className="flex w-full max-w-5xl items-center justify-between text-white" onClick={(e) => e.stopPropagation()}>
        <p className="truncate font-black">
          {title}
          {many && <span className="ml-2 font-medium text-white/60">Phương án {index + 1}/{urls.length}</span>}
        </p>
        <div className="flex shrink-0 gap-2">
          <a
            href={url}
            target="_blank"
            rel="noreferrer"
            className="rounded-full bg-white px-4 py-2 text-sm font-bold text-ink transition hover:bg-pop-lime"
          >
            Tải về ↓
          </a>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng"
            className="h-9 w-9 rounded-full border-2 border-white/40 font-bold transition hover:border-white"
          >
            ✕
          </button>
        </div>
      </div>

      <div className="relative flex min-h-0 w-full max-w-5xl flex-1 items-center justify-center">
        {video ? (
          <video
            key={url}
            src={url}
            controls
            autoPlay
            loop
            onClick={(e) => e.stopPropagation()}
            className="max-h-full max-w-full rounded-2xl"
          />
        ) : (
          <img
            key={url}
            src={url}
            alt={title}
            onClick={(e) => e.stopPropagation()}
            className="max-h-full max-w-full animate-pop-in rounded-2xl object-contain"
          />
        )}
        {many && (
          <>
            <NavButton label="Phương án trước" onClick={() => onIndexChange((index - 1 + urls.length) % urls.length)}>
              ‹
            </NavButton>
            <NavButton label="Phương án sau" right onClick={() => onIndexChange((index + 1) % urls.length)}>
              ›
            </NavButton>
          </>
        )}
      </div>

      {many && (
        <div className="flex gap-2" onClick={(e) => e.stopPropagation()}>
          {urls.map((u, i) => (
            <button
              type="button"
              key={u}
              onClick={() => onIndexChange(i)}
              aria-label={`Phương án ${i + 1}`}
              className={`h-14 w-14 overflow-hidden rounded-xl border-2 transition ${
                i === index ? 'border-pop-pink' : 'border-white/20 hover:border-white'
              }`}
            >
              <img src={u} alt="" className="h-full w-full object-cover" />
            </button>
          ))}
        </div>
      )}
    </div>,
    document.body,
  )
}

function NavButton({ label, right, onClick, children }: {
  label: string
  right?: boolean
  onClick: () => void
  children: string
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={(e) => {
        e.stopPropagation()
        onClick()
      }}
      className={`absolute top-1/2 ${right ? 'right-2' : 'left-2'} h-12 w-12 -translate-y-1/2 rounded-full bg-white/90 text-3xl leading-none font-black text-ink shadow-lg transition hover:bg-pop-lime`}
    >
      {children}
    </button>
  )
}
