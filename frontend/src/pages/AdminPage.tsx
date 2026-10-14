import { useEffect, useState } from 'react'
import { api, type Funnel } from '../api.ts'
import { useAuth } from '../auth/context.ts'

const PERIODS = [7, 30, 90]

/** Conversion funnel for the team: where visitors drop off between landing and the first download. */
export default function AdminPage() {
  const { account } = useAuth()
  const [days, setDays] = useState(30)
  const [funnel, setFunnel] = useState<Funnel>()
  const [error, setError] = useState<string>()

  useEffect(() => {
    if (account?.role !== 'ADMIN') return
    api
      .funnel(days)
      .then((data) => {
        setFunnel(data)
        setError(undefined)
      })
      .catch((e: Error) => setError(e.message))
  }, [account, days])

  if (account?.role !== 'ADMIN') {
    return <p className="mx-auto max-w-3xl px-6 py-20 text-center font-bold">Trang này chỉ dành cho quản trị viên.</p>
  }

  const top = Math.max(1, ...(funnel?.steps.map((s) => s.count) ?? [1]))

  return (
    <section className="mx-auto max-w-3xl px-6 py-12">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="font-bold text-pop-pink uppercase">Quản trị</p>
          <h1 className="mt-1 text-4xl font-black">Phễu chuyển đổi</h1>
        </div>
        <div className="flex gap-1 rounded-full border-2 border-ink bg-white p-1">
          {PERIODS.map((d) => (
            <button
              key={d}
              type="button"
              onClick={() => setDays(d)}
              className={`min-h-9 rounded-full px-4 text-sm font-bold ${days === d ? 'bg-ink text-white' : 'hover:bg-ink/5'}`}
            >
              {d} ngày
            </button>
          ))}
        </div>
      </div>
      <p className="mt-3 text-sm text-ink/60">
        Hai bước đầu đếm theo trình duyệt; từ bước Đăng ký trở đi đếm các tài khoản đăng ký trong khoảng thời gian này.
        Bước Nạp tiền sẽ có khi có thanh toán online.
      </p>

      {error && <p className="mt-6 rounded-2xl border-2 border-red-300 bg-red-50 p-3 text-sm text-red-700">{error}</p>}

      <ol className="mt-8 space-y-3">
        {funnel?.steps.map((step, i) => {
          const previous = i > 0 ? funnel.steps[i - 1].count : 0
          // Over 100% happens while early steps were not tracked yet; such a rate means nothing.
          const rate =
            i > 0 && previous > 0 && step.count <= previous ? Math.round((step.count / previous) * 100) : undefined
          return (
            <li key={step.label} className="rounded-2xl border-2 border-ink bg-white p-4">
              <div className="flex items-start justify-between gap-3">
                <span className="font-black">
                  {i + 1}. {step.label}
                </span>
                <span className="shrink-0 text-right">
                  <span className="block text-xl font-black">{step.count.toLocaleString('vi-VN')}</span>
                  {rate !== undefined && <span className="block text-xs font-bold text-pop-pink">{rate}% bước trước</span>}
                </span>
              </div>
              <div className="mt-3 h-3 overflow-hidden rounded-full bg-ink/5">
                <div className="bg-gradient-pop h-full rounded-full" style={{ width: `${(step.count / top) * 100}%` }} />
              </div>
            </li>
          )
        })}
      </ol>
      {funnel && (
        <p className="mt-6 text-sm text-ink/60">
          Trong đó {funnel.trials.toLocaleString('vi-VN')} người thử ảnh không cần đăng ký. Ngoài phễu:{' '}
          {funnel.zaloClicks.toLocaleString('vi-VN')} người bấm nút Zalo.
        </p>
      )}
    </section>
  )
}
