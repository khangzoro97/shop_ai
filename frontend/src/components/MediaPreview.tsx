import { useCallback, useEffect, useRef, useState, type PointerEvent, type ReactNode } from 'react'
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
            data-track="download"
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

      <div className="relative flex min-h-0 w-full max-w-5xl flex-1 items-center justify-center overflow-hidden rounded-2xl">
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
          <ZoomableImage key={url} src={url} alt={title} />
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

const MAX_ZOOM = 5
const BUTTON_STEP = 1.5
const WHEEL_STEP = 1.15

interface View {
  zoom: number
  x: number
  y: number
}

const FIT: View = { zoom: 1, x: 0, y: 0 }

function ZoomableImage({ src, alt }: { src: string; alt: string }) {
  const imgRef = useRef<HTMLImageElement>(null)
  const drag = useRef<{ startX: number; startY: number; x: number; y: number }>(undefined)
  const [view, setView] = useState<View>(FIT)
  const [dragging, setDragging] = useState(false)

  const limit = useCallback((v: View): View => {
    const img = imgRef.current
    if (!img || v.zoom <= 1) return FIT
    const maxX = (img.offsetWidth * (v.zoom - 1)) / 2
    const maxY = (img.offsetHeight * (v.zoom - 1)) / 2
    return { zoom: v.zoom, x: clamp(v.x, -maxX, maxX), y: clamp(v.y, -maxY, maxY) }
  }, [])

  const zoomBy = useCallback(
    (factor: number) =>
      setView((v) => {
        const zoom = clamp(v.zoom * factor, 1, MAX_ZOOM)
        const ratio = zoom / v.zoom
        return limit({ zoom, x: v.x * ratio, y: v.y * ratio })
      }),
    [limit],
  )

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === '+' || e.key === '=') zoomBy(BUTTON_STEP)
      if (e.key === '-') zoomBy(1 / BUTTON_STEP)
      if (e.key === '0') setView(FIT)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [zoomBy])

  function onPointerDown(e: PointerEvent<HTMLImageElement>) {
    if (view.zoom <= 1) return
    e.currentTarget.setPointerCapture(e.pointerId)
    drag.current = { startX: e.clientX, startY: e.clientY, x: view.x, y: view.y }
    setDragging(true)
  }

  function onPointerMove(e: PointerEvent<HTMLImageElement>) {
    const d = drag.current
    if (!d) return
    setView((v) => limit({ zoom: v.zoom, x: d.x + e.clientX - d.startX, y: d.y + e.clientY - d.startY }))
  }

  function onPointerUp() {
    drag.current = undefined
    setDragging(false)
  }

  const zoomed = view.zoom > 1

  return (
    <>
      <div className="flex h-full w-full animate-pop-in items-center justify-center">
        <img
          ref={imgRef}
          src={src}
          alt={alt}
          draggable={false}
          onClick={(e) => e.stopPropagation()}
          onDoubleClick={() => setView((v) => (v.zoom > 1 ? FIT : limit({ zoom: 2.5, x: 0, y: 0 })))}
          onWheel={(e) => zoomBy(e.deltaY < 0 ? WHEEL_STEP : 1 / WHEEL_STEP)}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerCancel={onPointerUp}
          style={{ transform: `translate(${view.x}px, ${view.y}px) scale(${view.zoom})` }}
          className={`max-h-full max-w-full rounded-2xl object-contain select-none ${
            dragging ? 'cursor-grabbing' : zoomed ? 'cursor-grab touch-none' : 'cursor-zoom-in'
          } ${dragging ? '' : 'transition-transform duration-150'}`}
        />
      </div>
      <div
        onClick={(e) => e.stopPropagation()}
        className="absolute bottom-3 left-1/2 flex -translate-x-1/2 items-center gap-1 rounded-full bg-ink/80 p-1 text-white shadow-lg backdrop-blur"
      >
        <ZoomButton label="Thu nhỏ" disabled={!zoomed} onClick={() => zoomBy(1 / BUTTON_STEP)}>
          −
        </ZoomButton>
        <button
          type="button"
          onClick={() => setView(FIT)}
          title="Về kích thước vừa khung (phím 0)"
          className="min-w-16 rounded-full px-2 py-1.5 text-xs font-bold tabular-nums transition hover:bg-white/15"
        >
          {Math.round(view.zoom * 100)}%
        </button>
        <ZoomButton label="Phóng to" disabled={view.zoom >= MAX_ZOOM} onClick={() => zoomBy(BUTTON_STEP)}>
          +
        </ZoomButton>
      </div>
    </>
  )
}

function ZoomButton({ label, disabled, onClick, children }: {
  label: string
  disabled: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
      className="h-8 w-8 rounded-full text-lg leading-none font-black transition hover:bg-white/15 disabled:opacity-30 disabled:hover:bg-transparent"
    >
      {children}
    </button>
  )
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
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
