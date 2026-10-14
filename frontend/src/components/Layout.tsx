import { startTransition, useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context.ts'
import { BRAND, BUSINESS, CONTACT, PROMOS } from '../config.ts'
import { trackLinkClicks, trackVisit } from '../track.ts'
import ConfirmDialog from './ConfirmDialog.tsx'
import Logo from './Logo.tsx'
import Marquee from './Marquee.tsx'
import UserMenu, { Avatar, CreditBadge } from './UserMenu.tsx'

const YEAR = new Date().getFullYear()

/** The floating Zalo button covers form buttons here; these pages link to Zalo where it is needed. */
const FORM_PAGES = ['/studio', '/dang-nhap']

const FOOTER_LINK = 'flex min-h-11 items-center hover:text-pop-lime md:min-h-8'

const POLICY_LINKS = [
  { to: '/dieu-khoan', label: 'Điều khoản sử dụng' },
  { to: '/bao-mat', label: 'Chính sách bảo mật' },
  { to: '/hoan-tien', label: 'Chính sách hoàn tiền' },
]

const NAV_LINKS = [
  { href: '/#services', label: 'Dịch vụ' },
  { href: '/#portfolio', label: 'Tác phẩm' },
  { href: '/#pricing', label: 'Bảng giá' },
  { href: '/#faq', label: 'Hỏi đáp' },
]

export default function Layout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const [confirmLogout, setConfirmLogout] = useState(false)
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { account, logout } = useAuth()

  useEffect(() => {
    trackVisit()
    document.addEventListener('click', trackLinkClicks)
    return () => document.removeEventListener('click', trackLinkClicks)
  }, [])

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
          <Link to="/" className="group flex min-h-11 items-center" aria-label={`${BRAND} - Trang chủ`}>
            <Logo />
          </Link>

          <div className="hidden items-center gap-1 text-sm font-semibold lg:flex">
            {NAV_LINKS.map((link) => (
              <a
                key={link.href}
                href={link.href}
                className="rounded-full px-4 py-2 whitespace-nowrap transition hover:bg-ink/5"
              >
                {link.label}
              </a>
            ))}
          </div>

          <div className="flex items-center gap-2">
            {account ? (
              <UserMenu account={account} onLogout={() => setConfirmLogout(true)} />
            ) : (
              <Link
                to="/dang-nhap?tab=dang-nhap"
                className="hidden rounded-full border-2 border-ink px-4 py-2 text-sm font-bold transition hover:bg-ink hover:text-white md:block"
              >
                Đăng nhập
              </Link>
            )}
            <NavLink
              to="/studio"
              className={`shine relative flex min-h-11 items-center overflow-hidden rounded-full px-5 text-sm font-bold whitespace-nowrap text-white transition hover:scale-105 ${
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
              className="flex h-11 w-11 flex-col items-center justify-center gap-1.5 rounded-full border-2 border-ink lg:hidden"
            >
              <span className={`h-0.5 w-4 bg-ink transition ${menuOpen ? 'translate-y-1 rotate-45' : ''}`} />
              <span className={`h-0.5 w-4 bg-ink transition ${menuOpen ? '-translate-y-1 -rotate-45' : ''}`} />
            </button>
          </div>
        </nav>

        {menuOpen && (
          <div className="animate-pop-in border-t border-ink/5 px-6 pb-4 lg:hidden">
            {account && (
              <div className="mt-4 flex items-center gap-3 md:hidden rounded-3xl border-2 border-ink bg-white p-3 shadow-[4px_4px_0_0_var(--color-ink)]">
                <Avatar account={account} size="lg" />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-black">{account.shopName}</p>
                  <p className="truncate text-xs text-ink/60">{account.email}</p>
                </div>
                <CreditBadge credits={account.credits} />
              </div>
            )}
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
            {account ? (
              <>
                {account.role === 'ADMIN' && (
                  <Link
                    to="/quan-tri"
                    onClick={() => setMenuOpen(false)}
                    className="block border-b border-ink/5 py-3 font-semibold"
                  >
                    Quản trị
                  </Link>
                )}
                <button
                  type="button"
                  onClick={() => {
                    setMenuOpen(false)
                    setConfirmLogout(true)
                  }}
                  className="block min-h-11 w-full py-3 text-left font-bold text-red-600"
                >
                  Đăng xuất
                </button>
              </>
            ) : (
              <Link to="/dang-nhap?tab=dang-nhap" onClick={() => setMenuOpen(false)} className="block py-3 font-semibold">
                Đăng nhập
              </Link>
            )}
          </div>
        )}
      </header>

      <ConfirmDialog
        open={confirmLogout}
        title="Đăng xuất?"
        confirmLabel="Đăng xuất"
        cancelLabel="Ở lại"
        onCancel={() => setConfirmLogout(false)}
        onConfirm={() => {
          setConfirmLogout(false)
          // Same transition as the navigation, otherwise RequireAuth redirects to the login page first.
          navigate('/')
          startTransition(logout)
        }}
      >
        Bạn sẽ cần đăng nhập lại để vào AI Studio. Ảnh đã tạo và credit vẫn được giữ nguyên trong tài khoản
        {account ? <strong> {account.shopName}</strong> : null}.
      </ConfirmDialog>

      <main className="flex-1">
        <Outlet />
      </main>

      <footer className="relative overflow-hidden bg-ink pt-16 pb-8 text-white">
        <div className="pointer-events-none absolute -top-24 -right-24 h-72 w-72 rounded-full bg-pop-violet/40 blur-3xl" />
        <div className="relative mx-auto max-w-6xl px-6">
          <p className="max-w-3xl text-4xl leading-tight font-black md:text-6xl">
            Ảnh xịn, <span className="text-gradient">chốt đơn</span> mỏi tay.
          </p>
          <div className="mt-12 grid grid-cols-2 gap-x-6 gap-y-8 border-t border-white/10 pt-8 text-sm text-white/70 md:grid-cols-4">
            <div className="col-span-2 md:col-span-1">
              <div className="mb-3">
                <Logo dark />
              </div>
              <p>Ảnh & video AI cho shop thời trang, mỹ phẩm Việt.</p>
              {BUSINESS.name && <p className="mt-3">{BUSINESS.name}</p>}
              {BUSINESS.taxId && <p>MST: {BUSINESS.taxId}</p>}
              {BUSINESS.address && <p>{BUSINESS.address}</p>}
            </div>
            {(CONTACT.zaloUrl || CONTACT.email) && (
              <div className="col-span-2 md:col-span-1">
                <p className="mb-2 font-bold text-white">Liên hệ</p>
                {CONTACT.zaloUrl && (
                  <a href={CONTACT.zaloUrl} target="_blank" rel="noreferrer" className={FOOTER_LINK}>
                    Zalo: {CONTACT.zaloLabel}
                  </a>
                )}
                {CONTACT.email && (
                  <a href={`mailto:${CONTACT.email}`} className={FOOTER_LINK}>
                    {CONTACT.email}
                  </a>
                )}
              </div>
            )}
            <div>
              <p className="mb-2 font-bold text-white">Khám phá</p>
              {NAV_LINKS.map((link) => (
                <a key={link.href} href={link.href} className={FOOTER_LINK}>
                  {link.label}
                </a>
              ))}
            </div>
            <div>
              <p className="mb-2 font-bold text-white">Chính sách</p>
              {POLICY_LINKS.map((link) => (
                <Link key={link.to} to={link.to} className={FOOTER_LINK}>
                  {link.label}
                </Link>
              ))}
            </div>
          </div>
          <p className="mt-10 text-xs text-white/40">© {YEAR} {BRAND}. All rights reserved.</p>
        </div>
      </footer>

      {CONTACT.zaloUrl && !FORM_PAGES.includes(pathname) && (
        <a
          href={CONTACT.zaloUrl}
          target="_blank"
          rel="noreferrer"
          aria-label={`Chat Zalo với ${BRAND}`}
          className={`group fixed right-5 bottom-5 z-40 items-center gap-2 ${pathname === '/' ? 'hidden md:flex' : 'flex'}`}
        >
          <span className="hidden rounded-full bg-white px-4 py-2 text-sm font-bold shadow-lg transition group-hover:block">
            Chat Zalo ngay
          </span>
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-[#0068ff] text-sm font-black text-white shadow-xl transition group-hover:scale-110">
            Zalo
          </span>
        </a>
      )}
    </div>
  )
}
