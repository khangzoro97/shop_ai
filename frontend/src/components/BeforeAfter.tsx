import { useState } from 'react'

interface BeforeAfterProps {
  before: string
  after: string
  alt: string
  className?: string
}

/** Drag the handle to compare the original photo with the AI result. */
export default function BeforeAfter({ before, after, alt, className = '' }: BeforeAfterProps) {
  const [position, setPosition] = useState(50)

  return (
    <div className={`relative select-none overflow-hidden ${className}`}>
      <img src={after} alt={`${alt} - sau`} className="absolute inset-0 h-full w-full object-cover" draggable={false} />
      <img
        src={before}
        alt={`${alt} - trước`}
        className="absolute inset-0 h-full w-full object-cover"
        style={{ clipPath: `inset(0 ${100 - position}% 0 0)` }}
        draggable={false}
      />

      <span className="absolute top-3 left-3 rounded-full bg-ink/80 px-3 py-1 text-xs font-bold text-white">Trước</span>
      <span className="absolute top-3 right-3 rounded-full bg-pop-lime px-3 py-1 text-xs font-bold text-ink">Sau</span>

      <div className="pointer-events-none absolute inset-y-0 w-0.5 bg-white" style={{ left: `${position}%` }}>
        <div className="absolute top-1/2 left-1/2 flex h-11 w-11 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full border-2 border-ink bg-white text-sm font-black shadow-lg">
          ⟷
        </div>
      </div>

      <input
        type="range"
        min={0}
        max={100}
        value={position}
        onChange={(e) => setPosition(Number(e.target.value))}
        aria-label="Kéo để so sánh ảnh trước và sau"
        className="absolute inset-0 h-full w-full cursor-ew-resize opacity-0"
      />
    </div>
  )
}
