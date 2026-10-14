import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'

export interface FilterOption<T> {
  value: T
  label: string
  /** Tailwind background class for the status dot. */
  dot: string
}

interface FilterMenuProps<T> {
  label: string
  options: FilterOption<T>[]
  value: T
  onChange: (value: T) => void
}

/** Matches `min-w-48` on the menu. */
const MENU_WIDTH = 192

/** Pill dropdown in the site's sticker style; behaves like a single-select listbox. */
export default function FilterMenu<T>({ label, options, value, onChange }: FilterMenuProps<T>) {
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const [alignLeft, setAlignLeft] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const listId = useId()

  const selectedIndex = Math.max(
    0,
    options.findIndex((option) => option.value === value),
  )
  const selected = options[selectedIndex]
  const isDefault = selectedIndex === 0

  useEffect(() => {
    if (!open) return
    listRef.current?.focus()
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  const openMenu = () => {
    const rect = buttonRef.current?.getBoundingClientRect()
    setAlignLeft(rect !== undefined && rect.right < MENU_WIDTH + 16)
    setActive(selectedIndex)
    setOpen(true)
  }

  const close = () => {
    setOpen(false)
    buttonRef.current?.focus()
  }

  const choose = (index: number) => {
    onChange(options[index].value)
    close()
  }

  const onButtonKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      openMenu()
    }
  }

  const onListKeyDown = (event: KeyboardEvent) => {
    const last = options.length - 1
    const moves: Record<string, number> = {
      ArrowDown: Math.min(active + 1, last),
      ArrowUp: Math.max(active - 1, 0),
      Home: 0,
      End: last,
    }
    if (event.key in moves) {
      event.preventDefault()
      setActive(moves[event.key])
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      choose(active)
    } else if (event.key === 'Escape' || event.key === 'Tab') {
      event.preventDefault()
      close()
    }
  }

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        aria-label={`${label}: ${selected.label}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        onClick={() => (open ? setOpen(false) : openMenu())}
        onKeyDown={onButtonKeyDown}
        className={`inline-flex min-h-9 items-center gap-2 rounded-full border-2 border-ink px-3.5 text-xs font-bold shadow-[3px_3px_0_0_var(--color-ink)] transition hover:translate-x-px hover:translate-y-px hover:shadow-[2px_2px_0_0_var(--color-ink)] ${
          isDefault ? 'bg-white' : 'bg-pop-lime'
        }`}
      >
        <span className={`h-2 w-2 rounded-full ${selected.dot}`} />
        {selected.label}
        <svg
          viewBox="0 0 12 12"
          className={`h-3 w-3 transition-transform ${open ? 'rotate-180' : ''}`}
          aria-hidden="true"
        >
          <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>

      {open && (
        <ul
          ref={listRef}
          id={listId}
          role="listbox"
          tabIndex={-1}
          aria-label={label}
          aria-activedescendant={`${listId}-${active}`}
          onKeyDown={onListKeyDown}
          className={`absolute ${alignLeft ? 'left-0' : 'right-0'} z-20 mt-2 min-w-48 animate-pop-in rounded-2xl border-2 border-ink bg-white p-1.5 shadow-[5px_5px_0_0_var(--color-ink)] focus:outline-none`}
        >
          {options.map((option, index) => {
            const isSelected = index === selectedIndex
            return (
              <li
                key={option.label}
                id={`${listId}-${index}`}
                role="option"
                aria-selected={isSelected}
                onClick={() => choose(index)}
                onPointerEnter={() => setActive(index)}
                className={`flex min-h-10 cursor-pointer items-center gap-2.5 rounded-xl px-3 text-sm font-bold transition ${
                  isSelected ? 'bg-ink text-white' : index === active ? 'bg-ink/5' : ''
                }`}
              >
                <span className={`h-2.5 w-2.5 rounded-full ${option.dot} ${isSelected ? 'ring-2 ring-white/40' : ''}`} />
                <span className="flex-1">{option.label}</span>
                {isSelected && <span aria-hidden="true">✓</span>}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
