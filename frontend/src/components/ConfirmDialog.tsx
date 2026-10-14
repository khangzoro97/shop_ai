import { useEffect, useRef, type ReactNode } from 'react'

/** Modal yes/no question on the native <dialog>, which traps focus and closes on Esc. */
export default function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  cancelLabel = 'Hủy',
  onConfirm,
  onCancel,
}: {
  open: boolean
  title: string
  children: ReactNode
  confirmLabel: string
  cancelLabel?: string
  onConfirm: () => void
  onCancel: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const el = dialog.current
    if (!el) return
    if (open && !el.open) el.showModal()
    if (!open && el.open) el.close()
  }, [open])

  return (
    <dialog
      ref={dialog}
      onCancel={(e) => {
        e.preventDefault()
        onCancel()
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel()
      }}
      aria-labelledby="confirm-title"
      className="m-auto w-[calc(100%-3rem)] max-w-sm rounded-[2rem] border-2 border-ink bg-white p-0 text-ink shadow-[6px_6px_0_0_var(--color-ink)] backdrop:bg-ink/40 backdrop:backdrop-blur-sm open:animate-pop-in"
    >
      <div className="p-7">
        <h2 id="confirm-title" className="text-2xl font-black">
          {title}
        </h2>
        <div className="mt-2 text-ink/70">{children}</div>
        <div className="mt-7 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <button
            type="button"
            autoFocus
            onClick={onCancel}
            className="min-h-11 rounded-full border-2 border-ink px-6 font-bold transition hover:bg-ink/5"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="min-h-11 rounded-full border-2 border-ink bg-pop-pink px-6 font-bold text-white shadow-[3px_3px_0_0_var(--color-ink)] transition hover:translate-x-0.5 hover:translate-y-0.5 hover:shadow-[1px_1px_0_0_var(--color-ink)]"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  )
}
