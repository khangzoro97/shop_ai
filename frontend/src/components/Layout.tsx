import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { CONTACT, PROMOS } from '../config.ts'
import Marquee from './Marquee.tsx'

const YEAR = new Date().getFullYear()

const NAV_LINKS = [
  { href: '/#services', label: 'Dịch vụ' },
  { href: '/#portfolio', label: 'Tác phẩm' },
  { href: '/#pricing', label: 'Bảng giá' },
  { href: '/#faq', label: 'Hỏi đáp' },
]

export default function Layout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const { pathname } = useLocation()

  return (
    <div className="flex min-h-screen flex-col">
      <div className="bg-gradient-pop py-2 text-sm font-semibold text-white">
        <Marquee
          items={PROMOS.map((promo) => (
            <span key={promo} className="flex items-center gap-6 px-6">
              <span className="text-pop-lime">✦</span>
              {promo}
            </span>
          ))}
        />
      </div>

      <header className="sticky top-0 z-30 border-b-2 border-ink/5 bg-cream/80 backdrop-blur-lg">
        <nav className="mx-auto flex max-w-6xl items-center justify-between px-6 py-3.5">
          <Link to="/" className="group flex items-center gap-2 text-2xl font-black tracking-tight">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-ink text-pop-lime transition group-hover:rotate-12">
              F
            </span>
            Fortfio<span className="text-pop-pink">.</span>
          </Link>

          <div className="hidden items-center gap-1 text-sm font-semibold md:flex">
            {NAV_LINKS.map((link) => (
              <a key={link.href} href={link.href} className="rounded-full px-4 py-2 transition hover:bg-ink/5">
                {link.label}
              </a>
            ))}
          </div>

          <div className="flex items-center gap-2">
            <NavLink
              to="/studio"
              className={`shine relative overflow-hidden rounded-full px-5 py-2.5 text-sm font-bold text-white transition hover:scale-105 ${
                pathname === '/studio' ? 'bg-ink' : 'bg-gradient-pop'
              }`}
            >
              AI Studio ✦
            </NavLink>
            <button
              type="button"
              onClick={() => setMenuOpen((open) => !open)}
              aria-label="Mở menu"
              aria-expanded={menuOpen}
              className="flex h-10 w-10 flex-col items-center justify-center gap-1.5 rounded-full border-2 border-ink md:hidden"
            >
              <span className={`h-0.5 w-4 bg-ink transition ${menuOpen ? 'translate-y-1 rotate-45' : ''}`} />
              <span className={`h-0.5 w-4 bg-ink transition ${menuOpen ? '-translate-y-1 -rotate-45' : ''}`} />
            </button>
          </div>
        </nav>

        {menuOpen && (
          <div className="animate-pop-in border-t border-ink/5 px-6 pb-4 md:hidden">
            {NAV_LINKS.map((link) => (
              <a
                key={link.href}
                href={link.href}
                onClick={() => setMenuOpen(false)}
                className="block border-b border-ink/5 py-3 font-semibold"
              >
                {link.label}
              </a>
            ))}
          </div>
        )}
      </header>

      <main className="flex-1">
        <Outlet />
      </main>

      <footer className="relative overflow-hidden bg-ink pt-16 pb-8 text-white">
        <div className="pointer-events-none absolute -top-24 -right-24 h-72 w-72 rounded-full bg-pop-violet/40 blur-3xl" />
        <div className="relative mx-auto max-w-6xl px-6">
          <p className="max-w-3xl text-4xl leading-tight font-black md:text-6xl">
            Ảnh xịn, <span className="text-gradient">chốt đơn</span> mỏi tay.
          </p>
          <div className="mt-12 grid gap-8 border-t border-white/10 pt-8 text-sm text-white/70 sm:grid-cols-3">
            <div>
              <p className="mb-2 font-bold text-white">Fortfio</p>
              <p>Ảnh & video AI cho shop thời trang, mỹ phẩm Việt.</p>
            </div>
            <div>
              <p className="mb-2 font-bold text-white">Liên hệ</p>
              <p>Zalo: {CONTACT.zaloLabel}</p>
              <p>{CONTACT.email}</p>
            </div>
            <div>
              <p className="mb-2 font-bold text-white">Khám phá</p>
              {NAV_LINKS.map((link) => (
                <a key={link.href} href={link.href} className="block hover:text-pop-lime">
                  {link.label}
                </a>
              ))}
            </div>
          </div>
          <p className="mt-10 text-xs text-white/40">© {YEAR} Fortfio. All rights reserved.</p>
        </div>
      </footer>

      <a
        href={CONTACT.zaloUrl}
        target="_blank"
        rel="noreferrer"
        aria-label="Chat Zalo với Fortfio"
        className="group fixed right-5 bottom-5 z-40 flex items-center gap-2"
      >
        <span className="hidden rounded-full bg-white px-4 py-2 text-sm font-bold shadow-lg transition group-hover:block">
          Chat Zalo ngay
        </span>
        <span className="relative flex h-14 w-14 items-center justify-center rounded-full bg-[#0068ff] text-sm font-black text-white shadow-xl transition group-hover:scale-110">
          <span className="absolute inset-0 animate-ping rounded-full bg-[#0068ff]/50" />
          <span className="relative">Zalo</span>
        </span>
      </a>
    </div>
  )
}
