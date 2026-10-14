import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { api, type Category, type PortfolioItem } from '../api.ts'
import BeforeAfter from '../components/BeforeAfter.tsx'
import Marquee from '../components/Marquee.tsx'
import Reveal from '../components/Reveal.tsx'
import TrialBox from '../components/TrialBox.tsx'
import { useAuth } from '../auth/context.ts'
import { CONTACT, FREE_TRIAL_IMAGES } from '../config.ts'
import { formatPlanPrice, formatVnd, IMAGE_CREDITS, PLANS } from '../pricing.ts'

const HERO_CTA_CLASS =
  'shine bg-gradient-pop relative overflow-hidden rounded-full px-8 py-4 font-bold text-white shadow-lg shadow-pop-pink/30 transition hover:-translate-y-0.5 hover:shadow-xl'

const PLATFORMS = ['Shopee', 'TikTok Shop', 'Lazada', 'Facebook', 'Instagram', 'Zalo OA', 'Tiki']

const SERVICES = [
  {
    tag: 'Hot',
    title: 'Ảnh sản phẩm giữ nguyên nhãn',
    text: 'Chụp bằng điện thoại, kể AI nghe bối cảnh bạn muốn: bàn đá, spa, bãi biển... Nhãn, chữ và màu bao bì giữ đúng như ảnh gốc.',
    accent: 'from-pop-pink to-pop-peach',
    span: 'md:col-span-2',
  },
  {
    tag: 'Thời trang',
    title: 'Thử đồ ảo',
    text: 'Mặc váy áo của shop lên người mẫu. Không thuê mẫu, không thuê studio.',
    accent: 'from-pop-violet to-pop-pink',
  },
  {
    tag: 'Mỹ phẩm',
    title: 'Người mẫu cầm sản phẩm',
    text: 'Tải ảnh người mẫu và ảnh sản phẩm, AI ghép thành ảnh cầm sản phẩm tự nhiên để đăng review.',
    accent: 'from-pop-peach to-pop-lime',
  },
  {
    tag: 'TikTok',
    title: 'Video 4–30 giây có nhạc',
    text: 'AI viết kịch bản theo mô tả của bạn, chia cảnh theo từng giây, thêm nhạc nền không lo bản quyền. Đúng tỉ lệ TikTok, Reels.',
    accent: 'from-pop-lime to-emerald-300',
    span: 'md:col-span-2',
  },
]

const STEPS = [
  { title: 'Chụp bằng điện thoại', text: 'Đặt sản phẩm trên nền trơn, đủ sáng. Không cần đèn, không cần studio.' },
  {
    title: 'Kể AI nghe bạn muốn gì',
    text: 'Gõ vài chữ như "bàn đá, có hoa, nắng sớm", AI viết chi tiết giúp. Chọn nơi đăng: Shopee, TikTok, Facebook.',
  },
  { title: 'Nhận ảnh, lên sàn', text: 'Vài phút là có ảnh. Tải về, đăng Shopee, TikTok Shop, chốt đơn thôi.' },
]

const FAQS = [
  {
    q: 'Ảnh có giữ đúng nhãn và màu sản phẩm không?',
    a: 'Có, với "Ảnh sản phẩm giữ nguyên nhãn": AI chỉ tạo bối cảnh xung quanh, sản phẩm lấy từ ảnh thật của bạn. Video và ảnh người mẫu thì AI vẽ lại sản phẩm, nên chữ rất nhỏ trên nhãn có thể bị mờ; hãy xem kỹ trước khi đăng.',
  },
  ...(CONTACT.primary
    ? [
        {
          q: 'Mua credit thế nào?',
          a: `Nhắn ${CONTACT.primary.channel} cho Chụp Xịn tên gói bạn chọn. Team gửi thông tin chuyển khoản và cộng credit vào tài khoản của bạn sau khi nhận tiền.`,
        },
      ]
    : []),
  {
    q: 'Tôi cần chuẩn bị ảnh như thế nào?',
    a: 'Chụp bằng điện thoại ở nơi đủ sáng, sản phẩm rõ nét, nền càng trơn càng tốt. Vậy là đủ.',
  },
  {
    q: 'Nếu AI làm lỗi thì sao?',
    a: 'Nếu AI xử lý thất bại hoặc quá thời gian, credit được hoàn lại tự động. Bạn không mất gì cả.',
  },
  {
    q: 'Tôi không rành công nghệ, có làm hộ không?',
    a: `Có. Gửi ảnh sản phẩm${CONTACT.primary ? ` qua ${CONTACT.primary.channel}` : ''}, team Chụp Xịn làm và gửi lại trọn bộ ảnh, video cho bạn.`,
  },
]

const FILTERS: { id: Category | undefined; label: string }[] = [
  { id: undefined, label: 'Tất cả' },
  { id: 'COSMETICS', label: 'Mỹ phẩm' },
  { id: 'FASHION', label: 'Thời trang' },
]

export default function HomePage() {
  const { account, loading } = useAuth()
  // Logged-in shops already have credits in Studio; the trial is for visitors only.
  const showTryNow = !loading && !account
  const [category, setCategory] = useState<Category | undefined>()
  const [items, setItems] = useState<PortfolioItem[]>([])
  const [heroItem, setHeroItem] = useState<PortfolioItem>()
  const [revealedId, setRevealedId] = useState<number>()
  const [showBar, setShowBar] = useState(false)
  const heroCtaRef = useRef<HTMLDivElement>(null)
  const finalCtaRef = useRef<HTMLElement>(null)

  useEffect(() => {
    let frame = 0
    const update = () => {
      frame = 0
      const hero = heroCtaRef.current?.getBoundingClientRect()
      const final = finalCtaRef.current?.getBoundingClientRect()
      if (hero && final) setShowBar(hero.bottom < 0 && final.top > window.innerHeight)
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update)
    }
    update()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      cancelAnimationFrame(frame)
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
    }
  }, [])

  useEffect(() => {
    api.portfolio(category).then(setItems).catch(() => setItems([]))
  }, [category])

  useEffect(() => {
    api
      .portfolio()
      .then((all) =>
        setHeroItem(
          all.find((item) => item.beforeUrl && !item.sample) ??
            all.find((item) => item.beforeUrl && item.outputType === 'IMAGE') ??
            all[0],
        ),
      )
      .catch(() => {})
  }, [])

  const onlySamples = items.length > 0 && items.every((item) => item.sample)
  const showTrial = FREE_TRIAL_IMAGES > 0 && CONTACT.primary !== undefined

  return (
    <>
      {/* Hero */}
      <section className="relative overflow-hidden">
        <div className="pointer-events-none absolute -top-32 -left-20 h-96 w-96 rounded-full bg-pop-pink/30 blur-3xl md:animate-blob" />
        <div className="pointer-events-none absolute top-20 right-0 h-96 w-96 rounded-full bg-pop-violet/25 blur-3xl [animation-delay:-6s] md:animate-blob" />
        <div className="pointer-events-none absolute bottom-0 left-1/3 h-72 w-72 rounded-full bg-pop-lime/40 blur-3xl [animation-delay:-12s] md:animate-blob" />

        <div className="relative mx-auto grid max-w-6xl items-center gap-14 px-6 pt-16 pb-20 md:grid-cols-[1.1fr_1fr] md:pt-24">
          <div>
            <span className="sticker animate-pop-in bg-pop-lime">✦ AI Studio cho shop online</span>
            <h1 className="mt-6 animate-pop-in text-5xl leading-[1.05] font-black tracking-tight [animation-delay:100ms] md:text-7xl">
              Ảnh sản phẩm <span className="text-gradient">xịn như studio</span>,{' '}
              <span className="relative inline-block">
                chốt đơn
                <svg viewBox="0 0 200 12" className="absolute -bottom-2 left-0 w-full" preserveAspectRatio="none">
                  <path d="M2 8 Q 50 2, 100 7 T 198 5" stroke="var(--color-pop-pink)" strokeWidth="4" fill="none" strokeLinecap="round" />
                </svg>
              </span>{' '}
              nhanh hơn.
            </h1>
            <p className="mt-7 max-w-xl animate-pop-in text-lg text-ink/70 [animation-delay:200ms]">
              Dành cho shop thời trang, mỹ phẩm trên Shopee, TikTok Shop. Chụp bằng điện thoại, chọn vibe, vài phút có
              ảnh đẹp. Không studio, không thuê mẫu.
            </p>
            <div ref={heroCtaRef} className="mt-9 flex animate-pop-in flex-wrap gap-3 [animation-delay:300ms]">
              {showTryNow ? (
                <a href="#thu-ngay" className={HERO_CTA_CLASS}>
                  Thử ngay, không cần đăng ký ↓
                </a>
              ) : (
                <Link to="/studio" className={HERO_CTA_CLASS}>
                  {account ? 'Mở AI Studio →' : 'Tạo ảnh miễn phí →'}
                </Link>
              )}
              {CONTACT.primary && (
                <a
                  href={CONTACT.primary.url}
                  target="_blank"
                  rel="noreferrer"
                  className="rounded-full border-2 border-ink bg-white px-8 py-4 font-bold shadow-[4px_4px_0_0_var(--color-ink)] transition hover:translate-x-0.5 hover:translate-y-0.5 hover:shadow-[2px_2px_0_0_var(--color-ink)]"
                >
                  Gửi ảnh, team làm hộ
                </a>
              )}
            </div>
            <div className="mt-8 flex animate-pop-in flex-wrap gap-x-6 gap-y-2 text-sm font-medium text-ink/60 [animation-delay:400ms]">
              <span>✓ Tặng 30 credit</span>
              <span>✓ Không cần thẻ</span>
              <span>✓ Lỗi AI hoàn credit</span>
            </div>
          </div>

          <div className="relative animate-pop-in [animation-delay:250ms]">
            <div className="absolute -inset-3 rotate-3 rounded-[2rem] bg-gradient-pop" />
            {heroItem?.beforeUrl ? (
              <BeforeAfter
                before={heroItem.beforeUrl}
                after={heroItem.afterUrl}
                alt={heroItem.title}
                className="relative aspect-[4/5] rounded-[1.75rem] border-4 border-ink bg-white"
              />
            ) : heroItem ? (
              <img
                src={heroItem.afterUrl}
                alt={heroItem.title}
                className="relative aspect-[4/5] w-full rounded-[1.75rem] border-4 border-ink bg-white object-cover"
              />
            ) : (
              <div className="skeleton relative aspect-[4/5] rounded-[1.75rem] border-4 border-ink" />
            )}

            <span
              className="sticker absolute -top-4 -left-6 bg-white md:animate-float"
              style={{ '--tilt': '-8deg' } as CSSProperties}
            >
              Nhãn giữ nguyên như ảnh gốc
            </span>
            {heroItem?.sample && (
              <span className="absolute right-3 bottom-3 rounded-full bg-ink/70 px-3 py-1 text-xs font-semibold text-white backdrop-blur">
                {heroItem.client}
              </span>
            )}
            <span
              className="sticker absolute top-1/3 -right-6 bg-pop-lime [animation-delay:-2s] md:animate-float-slow"
              style={{ '--tilt': '6deg' } as CSSProperties}
            >
              1:1 · 3:4 · 9:16
            </span>
            {heroItem && (
              <span
                className="sticker absolute -bottom-5 left-8 bg-pop-pink text-white [animation-delay:-4s] md:animate-float"
                style={{ '--tilt': '-4deg' } as CSSProperties}
              >
                {heroItem.beforeUrl ? 'Kéo để xem trước / sau ⟷' : heroItem.title}
              </span>
            )}
          </div>
        </div>
      </section>

      {/* Platforms ticker */}
      <div className="hidden overflow-hidden py-2 md:block">
        <section className="-rotate-1 border-y-2 border-ink bg-ink py-4 text-white">
          <Marquee
            items={PLATFORMS.map((p) => (
              <span key={p} className="flex items-center gap-8 px-8 text-2xl font-black tracking-tight uppercase">
                {p}
                <span className="text-pop-lime">✦</span>
              </span>
            ))}
          />
        </section>
      </div>

      {showTryNow && (
        <section id="thu-ngay" className="mx-auto max-w-6xl scroll-mt-28 px-6 pt-14 md:pt-20">
          <Reveal>
            <div className="rounded-[2rem] border-2 border-ink bg-white p-6 shadow-[6px_6px_0_0_var(--color-ink)] md:p-10">
              <p className="font-bold text-pop-pink uppercase">Thử ngay</p>
              <h2 className="mt-2 text-3xl font-black md:text-4xl">Tải 1 ảnh sản phẩm, xem AI làm gì cho shop bạn</h2>
              <p className="mt-2 mb-8 text-ink/60">
                Chụp bằng điện thoại cũng được. Thấy ưng thì đăng ký để tải ảnh gốc.
              </p>
              <TrialBox />
            </div>
          </Reveal>
        </section>
      )}

      {/* Services bento */}
      <section id="services" className="mx-auto max-w-6xl scroll-mt-28 px-6 py-14 md:py-20">
        <Reveal>
          <p className="font-bold text-pop-pink uppercase">Dịch vụ</p>
          <h2 className="mt-2 text-4xl font-black md:text-5xl">
            Một app, <span className="font-serif italic">đủ</span> content cho cả shop
          </h2>
        </Reveal>
        <div className="mt-8 grid gap-4 md:mt-10 md:grid-cols-3 md:gap-5">
          {SERVICES.map((s, i) => (
            <Reveal key={s.title} delay={i * 80} className={s.span ?? ''}>
              <div className="group relative h-full overflow-hidden rounded-3xl border-2 border-ink bg-white p-6 transition hover:-translate-y-1 hover:shadow-[8px_8px_0_0_var(--color-ink)] md:p-8">
                <div
                  className={`absolute -top-16 -right-16 h-48 w-48 rounded-full bg-linear-to-br ${s.accent} opacity-60 blur-2xl transition duration-500 group-hover:scale-150 group-hover:opacity-90`}
                />
                <span className="sticker relative bg-cream">{s.tag}</span>
                <h3 className="relative mt-4 text-xl font-black md:mt-5 md:text-2xl">{s.title}</h3>
                <p className="relative mt-2 max-w-md text-ink/70 md:mt-3">{s.text}</p>
              </div>
            </Reveal>
          ))}
        </div>

        <Reveal className="mt-10 rounded-3xl border-2 border-ink bg-white p-6 md:mt-14 md:p-10">
          <h3 className="text-2xl font-black md:text-center md:text-3xl">
            3 bước, <span className="text-gradient">xong trong vài phút</span>
          </h3>
          <ol className="mt-6 grid gap-5 md:mt-10 md:grid-cols-3 md:gap-8">
            {STEPS.map((step, i) => (
              <li key={step.title} className="flex gap-4 md:flex-col md:items-center md:text-center">
                <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border-2 border-ink bg-pop-lime text-lg font-black shadow-[3px_3px_0_0_var(--color-ink)] md:h-16 md:w-16 md:text-2xl">
                  {i + 1}
                </span>
                <div>
                  <p className="font-black md:text-xl">{step.title}</p>
                  <p className="mt-1 text-sm text-ink/70 md:mt-2 md:text-base">{step.text}</p>
                </div>
              </li>
            ))}
          </ol>
        </Reveal>
      </section>

      {/* Two ways to start */}
      <section id="bat-dau" className="mx-auto max-w-6xl scroll-mt-28 px-6 pb-14 md:pb-20">
        <Reveal>
          <p className="font-bold text-pop-pink uppercase">Bắt đầu</p>
          <h2 className="mt-2 text-4xl font-black md:text-5xl">Chọn cách hợp với shop bạn</h2>
        </Reveal>
        <div className={`mt-8 grid gap-5 md:mt-10 ${CONTACT.primary ? 'md:grid-cols-[1.4fr_1fr]' : ''}`}>
          <Reveal className="bg-gradient-pop relative overflow-hidden rounded-[2rem] border-2 border-ink p-7 text-white shadow-[8px_8px_0_0_var(--color-ink)] md:p-12">
            <div className="pointer-events-none absolute -bottom-20 -left-10 h-64 w-64 rounded-full bg-pop-lime/40 blur-3xl" />
            <svg
              viewBox="0 0 200 200"
              className="absolute top-6 right-6 hidden h-32 w-32 animate-spin-slow lg:block"
              aria-hidden
            >
              <defs>
                <path id="promo-circle" d="M100,100 m-75,0 a75,75 0 1,1 150,0 a75,75 0 1,1 -150,0" />
              </defs>
              <circle cx="100" cy="100" r="52" fill="var(--color-pop-lime)" />
              <text fontSize="17" fontWeight="800" fill="white" letterSpacing="3">
                <textPath href="#promo-circle">TẶNG CREDIT ✦ SHOP MỚI ✦ KHÔNG CẦN THẺ ✦</textPath>
              </text>
              <text x="100" y="110" textAnchor="middle" fontSize="28" fontWeight="900" fill="var(--color-ink)">
                +30
              </text>
            </svg>
            <p className="sticker relative bg-white text-ink">Tự làm · Ưu đãi shop mới</p>
            <h3 className="relative mt-5 max-w-sm text-3xl leading-tight font-black md:text-4xl">
              Tự làm trên AI Studio, tặng <span className="text-pop-lime">30 credit</span>
            </h3>
            <p className="relative mt-3 max-w-md text-white/85">
              Đủ làm khoảng 15 ảnh sản phẩm. Đăng ký, xác minh số điện thoại là dùng được, không cần thẻ.
            </p>
            <Link
              to="/studio"
              className="relative mt-7 flex min-h-12 items-center justify-center rounded-full bg-white px-8 py-3 font-bold text-ink transition hover:scale-105 md:inline-flex"
            >
              Nhận 30 credit ngay →
            </Link>
          </Reveal>

          {CONTACT.primary && (
            <Reveal
              delay={120}
              className="relative overflow-hidden rounded-[2rem] border-2 border-ink bg-white p-7 shadow-[8px_8px_0_0_var(--color-ink)] md:p-12"
            >
              <div className="pointer-events-none absolute -top-16 -right-16 h-48 w-48 rounded-full bg-linear-to-br from-pop-peach to-pop-violet opacity-50 blur-2xl" />
              <p className="sticker relative bg-pop-lime">Làm hộ</p>
              <h3 className="relative mt-5 text-3xl leading-tight font-black md:text-4xl">Gửi ảnh, team làm hộ</h3>
              <p className="relative mt-3 text-ink/70">
                Không rành công nghệ hoặc bận chốt đơn? Gửi ảnh sản phẩm qua {CONTACT.primary.channel}, team làm và gửi
                lại ảnh, video. Có gói content cho cả tháng.
              </p>
              {showTrial && (
                <p className="relative mt-3 font-bold text-pop-pink">
                  Làm thử miễn phí {FREE_TRIAL_IMAGES} ảnh, ưng mới mua.
                </p>
              )}
              <a
                href={CONTACT.primary.url}
                target="_blank"
                rel="noreferrer"
                className="relative mt-7 flex min-h-12 items-center justify-center rounded-full border-2 border-ink bg-white px-8 py-3 font-bold shadow-[4px_4px_0_0_var(--color-ink)] transition hover:translate-x-0.5 hover:translate-y-0.5 hover:shadow-[2px_2px_0_0_var(--color-ink)] md:inline-flex"
              >
                Gửi ảnh qua {CONTACT.primary.channel} →
              </a>
            </Reveal>
          )}
        </div>
      </section>

      {/* Portfolio */}
      <section id="portfolio" className="mx-auto max-w-6xl scroll-mt-28 px-6 pb-14 md:pb-20">
        <Reveal className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="font-bold text-pop-pink uppercase">Tác phẩm</p>
            <h2 className="mt-2 text-4xl font-black md:text-5xl">
              {onlySamples ? 'Ví dụ do Chụp Xịn tạo' : 'Shop thật, ảnh thật'}
            </h2>
            {onlySamples && (
              <p className="mt-2 max-w-xl text-sm text-ink/60">
                Ảnh gốc là sản phẩm mẫu với nhãn tự đặt FORTFIO LAB, không phải sản phẩm của khách hàng. Ảnh sau và video được tạo
                bằng đúng AI Studio bạn sẽ dùng. Tác phẩm của khách hàng sẽ được cập nhật sớm.
              </p>
            )}
          </div>
          <div className="flex gap-2 rounded-full border-2 border-ink bg-white p-1">
            {FILTERS.map((f) => (
              <button
                key={f.label}
                onClick={() => setCategory(f.id)}
                className={`min-h-11 rounded-full px-5 text-sm font-bold transition ${
                  category === f.id ? 'bg-ink text-white' : 'hover:bg-ink/5'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
        </Reveal>
        {items.length > 1 && <p className="mt-6 text-sm font-semibold text-ink/50 sm:hidden">Vuốt ngang xem thêm →</p>}
        <div className="-mx-6 mt-3 flex snap-x snap-mandatory gap-4 overflow-x-auto px-6 pt-1 pb-4 sm:mx-0 sm:mt-10 sm:grid sm:grid-cols-2 sm:gap-6 sm:overflow-visible sm:p-0 lg:grid-cols-3">
          {items.map((item, i) => (
            <article
              key={item.id}
              className="group w-[78%] shrink-0 animate-pop-in snap-center overflow-hidden rounded-3xl border-2 border-ink bg-white transition hover:-translate-y-1 hover:shadow-[8px_8px_0_0_var(--color-ink)] sm:w-auto"
              style={{ animationDelay: `${i * 70}ms` }}
            >
              <div className="relative aspect-[4/5] overflow-hidden">
                {item.outputType === 'VIDEO' ? (
                  <video
                    src={item.afterUrl}
                    aria-label={item.title}
                    autoPlay
                    muted
                    loop
                    playsInline
                    controls
                    preload="metadata"
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <img
                    src={item.afterUrl}
                    alt={item.title}
                    loading="lazy"
                    className="h-full w-full object-cover transition duration-700 group-hover:scale-110"
                  />
                )}
                {item.beforeUrl && (
                  <button
                    type="button"
                    aria-label={`Xem ảnh gốc: ${item.title}`}
                    aria-pressed={revealedId === item.id}
                    onClick={() => setRevealedId((id) => (id === item.id ? undefined : item.id))}
                    className="absolute inset-0"
                  >
                    <img
                      src={item.beforeUrl}
                      alt={`${item.title} - trước`}
                      loading="lazy"
                      className={`h-full w-full object-cover transition duration-500 ${
                        revealedId === item.id ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
                      }`}
                    />
                  </button>
                )}
                <span className="sticker absolute top-3 left-3 bg-white">
                  {item.category === 'COSMETICS' ? 'Mỹ phẩm' : 'Thời trang'}
                </span>
                {item.sample && (
                  <span className="absolute top-3 right-3 rounded-full bg-ink/70 px-3 py-1 text-xs font-semibold text-white backdrop-blur">
                    Ví dụ
                  </span>
                )}
                {item.beforeUrl && (
                  <span className="pointer-events-none absolute right-3 bottom-3 rounded-full bg-ink/80 px-3 py-1 text-xs font-semibold text-white backdrop-blur transition group-hover:bg-pop-pink">
                    {revealedId === item.id ? 'Ảnh gốc · chạm để xem ảnh AI' : 'Chạm hoặc rê chuột xem ảnh gốc'}
                  </span>
                )}
                {item.outputType === 'VIDEO' && (
                  <span className="pointer-events-none absolute bottom-14 left-3 rounded-full bg-ink/80 px-3 py-1 text-xs font-semibold text-white backdrop-blur">
                    Bật tiếng để nghe nhạc
                  </span>
                )}
              </div>
              <div className="p-5">
                <h3 className="font-black">{item.title}</h3>
                <p className="text-sm text-ink/60">{item.client}</p>
              </div>
            </article>
          ))}
        </div>
      </section>

      {/* Pricing */}
      <section id="pricing" className="relative scroll-mt-28 overflow-hidden bg-ink py-14 text-white md:py-20">
        <div className="pointer-events-none absolute top-0 left-1/4 h-80 w-80 rounded-full bg-pop-violet/30 blur-3xl md:animate-blob" />
        <div className="relative mx-auto max-w-6xl px-6">
          <Reveal className="text-center">
            <p className="font-bold text-pop-lime uppercase">Bảng giá</p>
            <h2 className="mt-2 text-4xl font-black md:text-5xl">Rẻ hơn một buổi chụp</h2>
            <p className="mt-3 text-white/60">1 ảnh = 2 credit · Thử đồ = 3 credit · Video 720p = 4 credit mỗi giây</p>
          </Reveal>
          <div className="mt-10 grid items-center gap-5 md:mt-12 md:grid-cols-3 md:gap-6">
            {PLANS.map((p, i) => (
              <Reveal key={p.name} delay={i * 120}>
                <div
                  className={`relative rounded-3xl border-2 p-6 transition hover:-translate-y-2 md:p-8 ${
                    p.featured
                      ? 'border-pop-lime bg-white text-ink shadow-[8px_8px_0_0_var(--color-pop-lime)] md:scale-105'
                      : 'border-white/15 bg-white/5'
                  }`}
                >
                  {p.featured && (
                    <span
                      className="sticker absolute -top-4 right-6 bg-pop-pink text-white md:animate-float"
                      style={{ '--tilt': '6deg' } as CSSProperties}
                    >
                      Shop chọn nhiều nhất
                    </span>
                  )}
                  <h3 className="text-lg font-bold">{p.name}</h3>
                  <p className="mt-4 text-5xl font-black">
                    {formatPlanPrice(p.price)}
                    <span className={`text-base font-semibold ${p.featured ? 'text-ink/50' : 'text-white/50'}`}>
                      {' '}
                      / {p.credits} credit
                    </span>
                  </p>
                  <p className={`mt-2 text-sm font-bold ${p.featured ? 'text-pop-pink' : 'text-pop-lime'}`}>
                    Khoảng {formatVnd((p.price / p.credits) * IMAGE_CREDITS)} mỗi ảnh
                  </p>
                  <p className={`mt-1 text-sm ${p.featured ? 'text-ink/60' : 'text-white/60'}`}>{p.note}</p>
                  <ul className="mt-6 space-y-2 text-sm">
                    {p.perks.map((perk) => (
                      <li key={perk} className="flex gap-2">
                        <span className={p.featured ? 'text-pop-pink' : 'text-pop-lime'}>✓</span>
                        {perk}
                      </li>
                    ))}
                  </ul>
                  {CONTACT.primary ? (
                    <a
                      href={CONTACT.primary.url}
                      target="_blank"
                      rel="noreferrer"
                      className={`relative mt-8 block overflow-hidden rounded-full py-3.5 text-center font-bold transition hover:scale-[1.02] ${
                        p.featured ? 'shine bg-gradient-pop text-white' : 'bg-white text-ink'
                      }`}
                    >
                      Mua qua {CONTACT.primary.channel}
                    </a>
                  ) : (
                    <Link
                      to="/studio"
                      className={`relative mt-8 block overflow-hidden rounded-full py-3.5 text-center font-bold transition hover:scale-[1.02] ${
                        p.featured ? 'shine bg-gradient-pop text-white' : 'bg-white text-ink'
                      }`}
                    >
                      Bắt đầu ngay
                    </Link>
                  )}
                </div>
              </Reveal>
            ))}
          </div>
          {CONTACT.primary && (
            <p className="mt-10 text-center text-sm text-white/60">
              Nhắn tên gói qua {CONTACT.primary.channel}, team gửi thông tin chuyển khoản và cộng credit sau khi nhận
              tiền. Muốn thử trước?{' '}
              <Link to="/studio" className="font-bold text-white underline decoration-pop-lime underline-offset-4">
                Nhận 30 credit miễn phí
              </Link>
              .
            </p>
          )}
        </div>
      </section>

      {/* FAQ */}
      <section id="faq" className="mx-auto max-w-3xl scroll-mt-28 px-6 py-14 md:py-20">
        <Reveal>
          <h2 className="text-center text-4xl font-black md:text-5xl">Hỏi nhanh, đáp gọn</h2>
        </Reveal>
        <div className="mt-10 space-y-3">
          {FAQS.map((faq, i) => (
            <Reveal key={faq.q} delay={i * 80}>
              <details className="group rounded-2xl border-2 border-ink bg-white px-5 py-3 transition open:shadow-[5px_5px_0_0_var(--color-ink)]">
                <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-4 font-bold">
                  {faq.q}
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-pop-lime text-lg transition group-open:rotate-45">
                    +
                  </span>
                </summary>
                <p className="mt-2 pb-2 text-ink/70">{faq.a}</p>
              </details>
            </Reveal>
          ))}
        </div>
      </section>

      {/* Final CTA */}
      <section ref={finalCtaRef} className="px-6 pb-16 md:pb-24">
        <Reveal>
          <div className="mx-auto max-w-4xl text-center">
            <h2 className="text-4xl leading-tight font-black md:text-6xl">
              Sẵn sàng có ảnh <span className="text-gradient">xịn sò</span> chưa?
            </h2>
            <Link
              to="/studio"
              className="shine bg-gradient-pop relative mt-8 inline-block overflow-hidden rounded-full px-10 py-5 text-lg font-bold text-white shadow-lg shadow-pop-pink/30 transition hover:scale-105"
            >
              Mở AI Studio, nhận 30 credit →
            </Link>
          </div>
        </Reveal>
      </section>

      <div
        inert={!showBar}
        className={`fixed inset-x-0 bottom-0 z-40 flex gap-3 border-t-2 border-ink bg-cream/95 px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] backdrop-blur transition-transform duration-300 md:hidden ${
          showBar ? 'translate-y-0' : 'translate-y-full'
        }`}
      >
        <Link
          to="/studio"
          className="bg-gradient-pop flex min-h-12 flex-1 items-center justify-center rounded-full font-bold text-white shadow-lg shadow-pop-pink/30"
        >
          Tạo ảnh miễn phí →
        </Link>
        {CONTACT.primary && (
          <a
            href={CONTACT.primary.url}
            target="_blank"
            rel="noreferrer"
            className="flex min-h-12 items-center rounded-full border-2 border-ink bg-white px-5 font-bold"
          >
            {CONTACT.primary.channel}
          </a>
        )}
      </div>
    </>
  )
}
