import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../api.ts'
import { useAuth } from '../auth/context.ts'
import { CONTACT } from '../config.ts'

const RESEND_SECONDS = 60

/**
 * Collapsed to one row until opened, so on phones the upload form stays on the first screen.
 * Codes are not sent by SMS yet: in production the team relays them over Zalo.
 */
export default function PhoneVerifyCard({
  id,
  open,
  onOpenChange,
}: {
  id: string
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { setAccount } = useAuth()
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [sentTo, setSentTo] = useState<string>()
  const [devCode, setDevCode] = useState<string | null>(null)
  const [cooldown, setCooldown] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((s) => s - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  async function sendCode() {
    setBusy(true)
    setError(undefined)
    try {
      const sent = await api.sendPhoneCode(phone)
      setSentTo(sent.phone)
      setDevCode(sent.devCode)
      setCooldown(RESEND_SECONDS)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function verify(e: FormEvent) {
    e.preventDefault()
    if (!sentTo) return
    setBusy(true)
    setError(undefined)
    try {
      setAccount(await api.verifyPhone(sentTo, code))
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (!open) {
    return (
      <div
        id={id}
        className="mb-6 flex items-center justify-between gap-3 rounded-2xl border-2 border-ink bg-pop-lime/40 px-4 py-3"
      >
        <p className="text-sm font-black">🎁 Xác minh SĐT, nhận 30 credit</p>
        <button
          type="button"
          onClick={() => onOpenChange(true)}
          className="min-h-11 shrink-0 rounded-full bg-ink px-4 text-sm font-bold text-white transition hover:bg-pop-pink"
        >
          Nhận ngay
        </button>
      </div>
    )
  }

  return (
    <div
      id={id}
      className="mb-6 rounded-3xl border-2 border-ink bg-pop-lime/40 p-5 shadow-[6px_6px_0_0_var(--color-ink)] md:mb-8 md:p-6"
    >
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="max-w-md">
          <div className="flex items-start justify-between gap-3">
            <h2 className="text-lg font-black md:text-xl">Xác minh số điện thoại, nhận 30 credit 🎁</h2>
            <button
              type="button"
              onClick={() => onOpenChange(false)}
              aria-label="Thu gọn"
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-xl text-ink/50 transition hover:bg-ink/5 hover:text-ink md:hidden"
            >
              ×
            </button>
          </div>
          <p className="mt-1 text-sm text-ink/70">
            Mỗi số điện thoại nhận quà một lần. Chúng tôi cũng dùng số này để gửi kết quả và hỗ trợ bạn.
          </p>
        </div>

        <form onSubmit={verify} className="flex w-full max-w-md flex-col gap-3">
          <div className="flex gap-2">
            <input
              type="tel"
              inputMode="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="Số điện thoại, ví dụ 0912345678"
              autoComplete="tel"
              className="min-h-11 min-w-0 flex-1 rounded-full border-2 border-ink bg-white px-4 text-sm focus:outline-none"
            />
            <button
              type="button"
              onClick={sendCode}
              disabled={busy || !phone.trim() || cooldown > 0}
              className="min-h-11 shrink-0 rounded-full bg-ink px-4 text-sm font-bold text-white transition hover:bg-pop-pink disabled:opacity-40 disabled:hover:bg-ink"
            >
              {cooldown > 0 ? `Gửi lại (${cooldown}s)` : sentTo ? 'Gửi lại mã' : 'Gửi mã'}
            </button>
          </div>

          {sentTo && (
            <div className="flex animate-pop-in gap-2">
              <input
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder="Mã 6 số"
                className="min-h-11 min-w-0 flex-1 rounded-full border-2 border-ink bg-white px-4 text-sm tracking-[0.3em] focus:outline-none"
              />
              <button
                type="submit"
                disabled={busy || code.length !== 6}
                className="min-h-11 shrink-0 rounded-full bg-pop-pink px-5 text-sm font-bold text-white transition hover:scale-105 disabled:opacity-40 disabled:hover:scale-100"
              >
                Xác minh
              </button>
            </div>
          )}

          {sentTo && devCode && (
            <p className="text-xs font-medium text-ink/60">
              Chế độ thử nghiệm (chưa nối SMS): mã của bạn là <b className="text-ink">{devCode}</b>
            </p>
          )}
          {sentTo && !devCode && CONTACT.primary && (
            <p className="text-sm text-ink/70">
              Chưa nhận được mã?{' '}
              <a
                href={CONTACT.primary.url}
                target="_blank"
                rel="noreferrer"
                className="font-bold text-ink underline decoration-pop-pink decoration-2 underline-offset-2"
              >
                Nhắn {CONTACT.primary.channel} cho Chụp Xịn kèm số {sentTo}
              </a>
              , team gửi mã cho bạn.
            </p>
          )}
          {error && <p className="animate-pop-in text-sm font-medium text-red-700">{error}</p>}
        </form>
      </div>
    </div>
  )
}
