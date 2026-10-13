import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../api.ts'
import { useAuth } from '../auth/context.ts'

const RESEND_SECONDS = 60

export default function PhoneVerifyCard() {
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

  return (
    <div className="mb-8 rounded-3xl border-2 border-ink bg-pop-lime/40 p-6 shadow-[6px_6px_0_0_var(--color-ink)]">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="max-w-md">
          <h2 className="text-xl font-black">Xác minh số điện thoại, nhận 30 credit 🎁</h2>
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
              className="min-w-0 flex-1 rounded-full border-2 border-ink bg-white px-4 py-2.5 text-sm focus:outline-none"
            />
            <button
              type="button"
              onClick={sendCode}
              disabled={busy || !phone.trim() || cooldown > 0}
              className="shrink-0 rounded-full bg-ink px-4 py-2.5 text-sm font-bold text-white transition hover:bg-pop-pink disabled:opacity-40 disabled:hover:bg-ink"
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
                className="min-w-0 flex-1 rounded-full border-2 border-ink bg-white px-4 py-2.5 text-sm tracking-[0.3em] focus:outline-none"
              />
              <button
                type="submit"
                disabled={busy || code.length !== 6}
                className="shrink-0 rounded-full bg-pop-pink px-5 py-2.5 text-sm font-bold text-white transition hover:scale-105 disabled:opacity-40 disabled:hover:scale-100"
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
          {error && <p className="animate-pop-in text-sm font-medium text-red-700">{error}</p>}
        </form>
      </div>
    </div>
  )
}
