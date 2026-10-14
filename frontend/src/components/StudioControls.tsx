import { useState, type ReactNode } from 'react'

export function ChoiceButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-2xl border-2 px-3 py-2 text-xs font-bold transition ${
        active ? 'border-ink bg-pop-lime/30' : 'border-ink/10 hover:border-ink'
      }`}
    >
      {children}
    </button>
  )
}

export function StepLabel({ n, children }: { n: number; children: ReactNode }) {
  return (
    <p className="mb-3 flex items-center gap-2 text-sm font-black uppercase">
      <span className="flex h-6 w-6 items-center justify-center rounded-full bg-pop-pink text-xs text-white">{n}</span>
      {children}
    </p>
  )
}

/** Square drop zone for one uploaded image, with a remove button once filled. */
export function UploadSlot({ label, required, image, onFile, onClear }: {
  label: string
  required: boolean
  image?: string
  onFile: (file?: File) => void
  onClear: () => void
}) {
  const [dragging, setDragging] = useState(false)
  return (
    <div className="relative">
      <label
        onDragOver={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragging(false)
          onFile(e.dataTransfer.files[0])
        }}
        className={`group flex aspect-square cursor-pointer flex-col items-center justify-center overflow-hidden rounded-2xl border-2 border-dashed text-sm font-semibold transition ${
          dragging
            ? 'scale-105 border-pop-pink bg-pop-pink/10'
            : 'border-ink/30 bg-cream hover:border-pop-pink hover:bg-pop-pink/5'
        }`}
      >
        {image ? (
          <img src={image} alt={label} className="h-full w-full animate-pop-in object-cover" />
        ) : (
          <>
            <span className="mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-pop-lime text-2xl font-black transition group-hover:rotate-90">
              +
            </span>
            <span>{label}</span>
            <span className="mt-1 text-xs font-normal text-ink/50">{required ? 'Bấm hoặc kéo thả' : 'Không bắt buộc'}</span>
          </>
        )}
        <input type="file" accept="image/*" className="hidden" onChange={(e) => onFile(e.target.files?.[0])} />
      </label>
      {image && (
        <button
          type="button"
          onClick={onClear}
          aria-label={`Bỏ ${label.toLowerCase()}`}
          className="absolute top-2 right-2 flex h-7 w-7 items-center justify-center rounded-full border-2 border-ink bg-white text-sm font-black transition hover:bg-pop-pink hover:text-white"
        >
          ×
        </button>
      )}
    </div>
  )
}
