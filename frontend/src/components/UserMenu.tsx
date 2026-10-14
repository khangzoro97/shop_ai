import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import type { Account } from '../api.ts'

export function Avatar({ account, size = 'md' }: { account: Account; size?: 'md' | 'lg' }) {
  const initial = account.shopName.trim().charAt(0).toUpperCase() || '✦'
  return (
    <span
      aria-hidden
      className={`bg-gradient-pop flex shrink-0 items-center justify-center rounded-full border-2 border-ink font-black text-white ${
        size === 'lg' ? 'h-12 w-12 text-lg' : 'h-8 w-8 text-sm'
      }`}
    >
      {initial}
    </span>
  )
}

export function CreditBadge({ credits }: { credits: number }) {
  return (
    <span className="rounded-full bg-pop-lime px-2 py-0.5 text-xs font-black whitespace-nowrap text-ink">
      ✦ {credits.toLocaleString('vi-VN')} credit
    </span>
  )
}

const ITEM = 'flex min-h-11 w-full items-center gap-3 rounded-2xl px-3 text-left text-sm font-bold transition hover:bg-ink/5'

/** Account chip in the header; the menu holds the account details and the logout button. */
export default function UserMenu({ account, onLogout }: { account: Account; onLogout: () => void }) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onPointer = (e: PointerEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div ref={root} className="relative hidden md:block">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        className={`flex min-h-11 items-center gap-2 rounded-full border-2 border-ink bg-white py-1 pr-3 pl-1 text-sm font-bold transition ${
          open ? 'shadow-none' : 'shadow-[3px_3px_0_0_var(--color-ink)] hover:translate-x-0.5 hover:translate-y-0.5 hover:shadow-[1px_1px_0_0_var(--color-ink)]'
        }`}
      >
        <Avatar account={account} />
        <span className="max-w-32 truncate">{account.shopName}</span>
        <CreditBadge credits={account.credits} />
        <svg viewBox="0 0 12 12" className={`h-3 w-3 transition ${open ? 'rotate-180' : ''}`} aria-hidden>
          <path d="M2 4l4 4 4-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </button>

      {open && (
        <div
          role="menu"
          className="absolute top-full right-0 z-40 mt-3 w-72 animate-pop-in rounded-3xl border-2 border-ink bg-white p-2 shadow-[6px_6px_0_0_var(--color-ink)]"
        >
          <div className="flex items-center gap-3 rounded-2xl bg-cream p-3">
            <Avatar account={account} size="lg" />
            <div className="min-w-0">
              <p className="truncate font-black">{account.shopName}</p>
              <p className="truncate text-xs text-ink/60">{account.email}</p>
              <p className="mt-1 text-xs font-bold">
                {account.phoneVerified ? (
                  <span className="text-emerald-700">✓ Đã xác minh SĐT</span>
                ) : (
                  <span className="text-pop-pink">Chưa xác minh SĐT</span>
                )}
              </p>
            </div>
          </div>
          <div className="mt-2 flex items-center justify-between px-3 py-2 text-sm">
            <span className="text-ink/60">Số dư</span>
            <CreditBadge credits={account.credits} />
          </div>
          <div className="my-1 h-px bg-ink/10" />
          <Link role="menuitem" to="/studio" onClick={() => setOpen(false)} className={ITEM}>
            ✦ AI Studio
          </Link>
          {account.role === 'ADMIN' && (
            <Link role="menuitem" to="/quan-tri" onClick={() => setOpen(false)} className={ITEM}>
              ▤ Quản trị
            </Link>
          )}
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false)
              onLogout()
            }}
            className={`${ITEM} text-red-600 hover:bg-red-50`}
          >
            ⎋ Đăng xuất
          </button>
        </div>
      )}
    </div>
  )
}
