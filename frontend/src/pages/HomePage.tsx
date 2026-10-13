import { useEffect, useState, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { api, type Category, type PortfolioItem } from '../api.ts'
import BeforeAfter from '../components/BeforeAfter.tsx'
import CountUp from '../components/CountUp.tsx'
import Marquee from '../components/Marquee.tsx'
import Reveal from '../components/Reveal.tsx'
import { CONTACT, FREE_TRIAL_IMAGES } from '../config.ts'

const PLATFORMS = ['Shopee', 'TikTok Shop', 'Lazada', 'Facebook', 'Instagram', 'Zalo OA', 'Tiki']

const STATS = [
  { value: 100, suffix: '%', label: 'Giữ nguyên nhãn sản phẩm' },
  { value: 3, suffix: '', label: 'Tỉ lệ chuẩn sàn' },
  { value: 5, suffix: '+', label: 'Phong cách bối cảnh' },
  { value: 30, suffix: '', label: 'Credit tặng shop mới' },
]

const SERVICES = [
  {
    tag: 'Hot',
    title: 'Ảnh sản phẩm giữ nguyên nhãn',
    text: 'Chụp bằng điện thoại, AI tạo bối cảnh studio, lifestyle, pastel... Nhãn, chữ, màu bao bì giữ đúng 100%.',
    accent: 'from-pop-pink to-pop-peach',
    wide: true,
  },
  {
    tag: 'Thời trang',
    title: 'Thử đồ ảo',
    text: 'Mặc váy áo của shop lên người mẫu AI. Không thuê mẫu, không thuê studio.',
    accent: 'from-pop-violet to-pop-pink',
  },
  {
    tag: 'TikTok',
    title: 'Video ngắn 5 giây',
    text: 'Biến ảnh sản phẩm thành clip chuyển động, đăng TikTok, Reels là viral.',
    accent: 'from-pop-lime to-emerald-300',
  },
  {
    tag: 'Làm hộ',
    title: 'Gói content tháng',
    text: 'Bận chốt đơn? Gửi ảnh cho team Fortfio, nhận trọn bộ ảnh và video cho cả tháng.',
    accent: 'from-pop-peach to-pop-violet',
    wide: true,
  },
]

const STEPS = [
  { title: 'Chụp bằng điện thoại', text: 'Đặt sản phẩm trên nền trơn, đủ sáng. Không cần đèn, không cần studio.' },
  { title: 'Chọn vibe & tỉ lệ sàn', text: 'Studio, bàn đá, pastel, thiên nhiên, sang chảnh. Chọn 1:1, 3:4 hoặc 9:16.' },
  { title: 'Nhận ảnh, lên sàn', text: 'Vài phút là có ảnh. Tải về, đăng Shopee, TikTok Shop, chốt đơn thôi.' },
]

const PLANS = [
  { name: 'Dùng thử', price: '99K', credits: 50, note: '~25 ảnh sản phẩm', perks: ['Đủ 4 tính năng AI', 'Ảnh chuẩn 3 tỉ lệ sàn'] },
  {
    name: 'Shop',
    price: '299K',
    credits: 180,
    note: '~90 ảnh hoặc 9 video',
    perks: ['Mọi thứ của gói Dùng thử', 'Hỗ trợ trực tiếp từ team'],
    featured: true,
  },
  { name: 'Pro', price: '799K', credits: 550, note: 'Cho shop đăng bài mỗi ngày', perks: ['Mọi thứ của gói Shop', 'Tư vấn concept riêng'] },
]

const FAQS = [
  {
    q: 'Ảnh có giữ đúng nhãn và màu sản phẩm không?',
    a: 'Có. Với tính năng "Ảnh sản phẩm giữ nguyên nhãn", AI chỉ tạo bối cảnh phía sau, sản phẩm thật của bạn được giữ nguyên từng pixel.',
  },
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
    a: `Có. Gửi ảnh sản phẩm${CONTACT.primary ? ` qua ${CONTACT.primary.channel}` : ''}, team Fortfio làm và gửi lại trọn bộ ảnh, video cho bạn.`,
  },
]

const FILTERS: { id: Category | undefined; label: string }[] = [
  { id: undefined, label: 'Tất cả' },
  { id: 'COSMETICS', label: 'Mỹ phẩm' },
  { id: 'FASHION', label: 'Thời trang' },
]

export default function HomePage() {
  const [category, setCategory] = useState<Category | undefined>()
  const [items, setItems] = useState<PortfolioItem[]>([])
  const [heroItem, setHeroItem] = useState<PortfolioItem>()

  useEffect(() => {
    api.portfolio(category).then(setItems).catch(() => setItems([]))
  }, [category])

  useEffect(() => {
    api
      .portfolio()
      .then((all) => setHeroItem(all.find((item) => item.beforeUrl && !item.sample) ?? all[0]))
      .catch(() => {})
  }, [])

  const onlySamples = items.length > 0 && items.every((item) => item.sample)
  const showTrial = FREE_TRIAL_IMAGES > 0 && CONTACT.primary !== undefined

  return (
    <>
      {/* Hero */}
      <section className="relative overflow-hidden">
        <div className="pointer-events-none absolute -top-32 -left-20 h-96 w-96 animate-blob rounded-full bg-pop-pink/30 blur-3xl" />
        <div className="pointer-events-none absolute top-20 right-0 h-96 w-96 animate-blob rounded-full bg-pop-violet/25 blur-3xl [animation-delay:-6s]" />
        <div className="pointer-events-none absolute bottom-0 left-1/3 h-72 w-72 animate-blob rounded-full bg-pop-lime/40 blur-3xl [animation-delay:-12s]" />

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
            <div className="mt-9 flex animate-pop-in flex-wrap gap-3 [animation-delay:300ms]">
              <Link
                to="/studio"
                className="shine bg-gradient-pop relative overflow-hidden rounded-full px-8 py-4 font-bold text-white shadow-lg shadow-pop-pink/30 transition hover:-translate-y-0.5 hover:shadow-xl"
              >
                Tạo ảnh miễn phí →
              </Link>
              {CONTACT.primary && (
                <a
                  href={CONTACT.primary.url}
                  target="_blank"
                  rel="noreferrer"
                  className="rounded-full border-2 border-ink bg-white px-8 py-4 font-bold shadow-[4px_4px_0_0_var(--color-ink)] transition hover:translate-x-0.5 hover:translate-y-0.5 hover:shadow-[2px_2px_0_0_var(--color-ink)]"
                >
                  Nhắn {CONTACT.primary.channel} tư vấn
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
              className="sticker absolute -top-4 -left-6 animate-float bg-white"
              style={{ '--tilt': '-8deg' } as CSSProperties}
            >
              Giữ nguyên nhãn 100%
            </span>
            <span
              className="sticker absolute top-1/3 -right-6 animate-float-slow bg-pop-lime [animation-delay:-2s]"
              style={{ '--tilt': '6deg' } as CSSProperties}
            >
              1:1 · 3:4 · 9:16
            </span>
            {heroItem && (
              <span
                className="sticker absolute -bottom-5 left-8 animate-float bg-pop-pink text-white [animation-delay:-4s]"
                style={{ '--tilt': '-4deg' } as CSSProperties}
              >
                {heroItem.beforeUrl ? 'Kéo để xem trước / sau ⟷' : heroItem.sample ? 'Ảnh minh họa' : heroItem.title}
              </span>
            )}
          </div>
        </div>
      </section>

      {/* Platforms ticker */}
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

      {/* Stats */}
      <section className="mx-auto grid max-w-6xl grid-cols-2 gap-4 px-6 py-20 md:grid-cols-4">
        {STATS.map((s, i) => (
          <Reveal key={s.label} delay={i * 100}>
            <div className="rounded-3xl border-2 border-ink bg-white p-6 text-center transition hover:-translate-y-1 hover:shadow-[6px_6px_0_0_var(--color-ink)]">
              <p className="text-gradient text-5xl font-black">
                <CountUp to={s.value} suffix={s.suffix} />
              </p>
              <p className="mt-2 text-sm font-semibold text-ink/60">{s.label}</p>
            </div>
          </Reveal>
        ))}
      </section>

      {/* Services bento */}
      <section id="services" className="mx-auto max-w-6xl scroll-mt-28 px-6 pb-20">
        <Reveal>
          <p className="font-bold text-pop-pink uppercase">Dịch vụ</p>
          <h2 className="mt-2 text-4xl font-black md:text-5xl">
            Một app, <span className="font-serif italic">đủ</span> content cho cả shop
          </h2>
        </Reveal>
        <div className="mt-10 grid gap-5 md:grid-cols-3">
          {SERVICES.map((s, i) => (
            <Reveal key={s.title} delay={i * 80} className={s.wide ? 'md:col-span-2' : ''}>
              <div className="group relative h-full overflow-hidden rounded-3xl border-2 border-ink bg-white p-8 transition hover:-translate-y-1 hover:shadow-[8px_8px_0_0_var(--color-ink)]">
                <div
                  className={`absolute -top-16 -right-16 h-48 w-48 rounded-full bg-linear-to-br ${s.accent} opacity-60 blur-2xl transition duration-500 group-hover:scale-150 group-hover:opacity-90`}
                />
                <span className="sticker relative bg-cream">{s.tag}</span>
                <h3 className="relative mt-5 text-2xl font-black">{s.title}</h3>
                <p className="relative mt-3 max-w-md text-ink/70">{s.text}</p>
                <Link
                  to="/studio"
                  className="relative mt-6 inline-flex items-center gap-1 font-bold text-pop-pink transition group-hover:gap-3"
                >
                  Thử ngay <span aria-hidden>→</span>
                </Link>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section className="bg-white py-20">
        <div className="mx-auto max-w-6xl px-6">
          <Reveal>
            <h2 className="text-center text-4xl font-black md:text-5xl">
              3 bước, <span className="text-gradient">xong trong vài phút</span>
            </h2>
          </Reveal>
          <div className="relative mt-14 grid gap-10 md:grid-cols-3">
            <div className="absolute top-10 right-[16%] left-[16%] hidden border-t-2 border-dashed border-ink/20 md:block" />
            {STEPS.map((step, i) => (
              <Reveal key={step.title} delay={i * 150} className="relative text-center">
                <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full border-2 border-ink bg-pop-lime text-3xl font-black shadow-[4px_4px_0_0_var(--color-ink)] transition hover:animate-wiggle">
                  {i + 1}
                </div>
                <h3 className="mt-6 text-xl font-black">{step.title}</h3>
                <p className="mx-auto mt-2 max-w-xs text-ink/70">{step.text}</p>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* Promo banner */}
      <section className="mx-auto max-w-6xl px-6 py-20">
        <Reveal>
          <div className="bg-gradient-pop relative overflow-hidden rounded-[2rem] border-2 border-ink p-10 text-white shadow-[10px_10px_0_0_var(--color-ink)] md:p-14">
            <div className="pointer-events-none absolute -bottom-20 -left-10 h-64 w-64 rounded-full bg-pop-lime/40 blur-3xl" />
            <svg
              viewBox="0 0 200 200"
              className="absolute top-8 right-10 hidden h-44 w-44 animate-spin-slow md:block"
              aria-hidden
            >
              <defs>
                <path id="promo-circle" d="M100,100 m-75,0 a75,75 0 1,1 150,0 a75,75 0 1,1 -150,0" />
              </defs>
              <circle cx="100" cy="100" r="52" fill="var(--color-pop-lime)" />
              <text fontSize="17" fontWeight="800" fill="white" letterSpacing="3">
                <textPath href="#promo-circle">
                  {showTrial ? 'DÙNG THỬ MIỄN PHÍ ✦ KHÔNG CẦN THẺ ✦' : 'TẶNG CREDIT ✦ SHOP MỚI ✦ KHÔNG CẦN THẺ ✦'}
                </textPath>
              </text>
              <text x="100" y="110" textAnchor="middle" fontSize="28" fontWeight="900" fill="var(--color-ink)">
                {showTrial ? 'FREE' : '+30'}
              </text>
            </svg>
            <p className="sticker relative bg-white text-ink">Ưu đãi shop mới</p>
            {showTrial && CONTACT.primary ? (
              <>
                <h2 className="relative mt-5 max-w-2xl text-4xl leading-tight font-black md:text-5xl">
                  Gửi {FREE_TRIAL_IMAGES} ảnh sản phẩm, team làm thử <span className="text-pop-lime">miễn phí</span>.
                </h2>
                <p className="relative mt-4 max-w-xl text-white/85">
                  Ưng thì mới mua gói, không ưng cũng chẳng mất gì. Nhắn {CONTACT.primary.channel}, gửi ảnh, đợi nhận
                  ảnh xịn.
                </p>
              </>
            ) : (
              <>
                <h2 className="relative mt-5 max-w-2xl text-4xl leading-tight font-black md:text-5xl">
                  Tặng <span className="text-pop-lime">30 credit</span> để thử ngay hôm nay.
                </h2>
                <p className="relative mt-4 max-w-xl text-white/85">
                  Đủ để làm khoảng 15 ảnh sản phẩm. Chỉ cần đăng ký và xác minh số điện thoại, không cần thẻ.
                </p>
              </>
            )}
            <div className="relative mt-8 flex flex-wrap gap-3">
              {showTrial && CONTACT.primary && (
                <a
                  href={CONTACT.primary.url}
                  target="_blank"
                  rel="noreferrer"
                  className="rounded-full bg-white px-8 py-4 font-bold text-ink transition hover:scale-105"
                >
                  Gửi ảnh qua {CONTACT.primary.channel}
                </a>
              )}
              <Link
                to="/studio"
                className={`rounded-full px-8 py-4 font-bold transition ${
                  showTrial ? 'border-2 border-white hover:bg-white/10' : 'bg-white text-ink hover:scale-105'
                }`}
              >
                Tự làm trên AI Studio
              </Link>
            </div>
          </div>
        </Reveal>
      </section>

      {/* Portfolio */}
      <section id="portfolio" className="mx-auto max-w-6xl scroll-mt-28 px-6 pb-20">
        <Reveal className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="font-bold text-pop-pink uppercase">Tác phẩm</p>
            <h2 className="mt-2 text-4xl font-black md:text-5xl">
              {onlySamples ? 'Ảnh mẫu theo phong cách' : 'Shop thật, ảnh thật'}
            </h2>
            {onlySamples && (
              <p className="mt-2 max-w-xl text-sm text-ink/60">
                Ảnh minh họa phong cách, không phải sản phẩm của khách hàng. Tác phẩm thật sẽ được cập nhật sớm.
              </p>
            )}
          </div>
          <div className="flex gap-2 rounded-full border-2 border-ink bg-white p-1">
            {FILTERS.map((f) => (
              <button
                key={f.label}
                onClick={() => setCategory(f.id)}
                className={`rounded-full px-5 py-2 text-sm font-bold transition ${
                  category === f.id ? 'bg-ink text-white' : 'hover:bg-ink/5'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
        </Reveal>
        <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((item, i) => (
            <article
              key={item.id}
              className="group animate-pop-in overflow-hidden rounded-3xl border-2 border-ink bg-white transition hover:-translate-y-1 hover:shadow-[8px_8px_0_0_var(--color-ink)]"
              style={{ animationDelay: `${i * 70}ms` }}
            >
              <div className="relative aspect-[4/5] overflow-hidden">
                <img
                  src={item.afterUrl}
                  alt={item.title}
                  loading="lazy"
                  className="h-full w-full object-cover transition duration-700 group-hover:scale-110"
                />
                {item.beforeUrl && (
                  <img
                    src={item.beforeUrl}
                    alt={`${item.title} - trước`}
                    loading="lazy"
                    className="absolute inset-0 h-full w-full object-cover opacity-0 transition duration-500 group-hover:opacity-100"
                  />
                )}
                <span className="sticker absolute top-3 left-3 bg-white">
                  {item.category === 'COSMETICS' ? 'Mỹ phẩm' : 'Thời trang'}
                </span>
                {item.sample && (
                  <span className="absolute top-3 right-3 rounded-full bg-ink/70 px-3 py-1 text-xs font-semibold text-white backdrop-blur">
                    Ảnh minh họa
                  </span>
                )}
                {item.beforeUrl && (
                  <span className="absolute right-3 bottom-3 rounded-full bg-ink/80 px-3 py-1 text-xs font-semibold text-white backdrop-blur transition group-hover:bg-pop-pink">
                    Rê chuột xem ảnh gốc
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
      <section id="pricing" className="relative scroll-mt-28 overflow-hidden bg-ink py-20 text-white">
        <div className="pointer-events-none absolute top-0 left-1/4 h-80 w-80 animate-blob rounded-full bg-pop-violet/30 blur-3xl" />
        <div className="relative mx-auto max-w-6xl px-6">
          <Reveal className="text-center">
            <p className="font-bold text-pop-lime uppercase">Bảng giá</p>
            <h2 className="mt-2 text-4xl font-black md:text-5xl">Rẻ hơn một buổi chụp</h2>
            <p className="mt-3 text-white/60">1 ảnh = 2 credit · Thử đồ = 3 credit · Video 5 giây = 20 credit</p>
          </Reveal>
          <div className="mt-12 grid items-center gap-6 md:grid-cols-3">
            {PLANS.map((p, i) => (
              <Reveal key={p.name} delay={i * 120}>
                <div
                  className={`relative rounded-3xl border-2 p-8 transition hover:-translate-y-2 ${
                    p.featured
                      ? 'border-pop-lime bg-white text-ink shadow-[8px_8px_0_0_var(--color-pop-lime)] md:scale-105'
                      : 'border-white/15 bg-white/5'
                  }`}
                >
                  {p.featured && (
                    <span
                      className="sticker absolute -top-4 right-6 animate-float bg-pop-pink text-white"
                      style={{ '--tilt': '6deg' } as CSSProperties}
                    >
                      Shop chọn nhiều nhất
                    </span>
                  )}
                  <h3 className="text-lg font-bold">{p.name}</h3>
                  <p className="mt-4 text-5xl font-black">
                    {p.price}
                    <span className={`text-base font-semibold ${p.featured ? 'text-ink/50' : 'text-white/50'}`}>
                      {' '}
                      / {p.credits} credit
                    </span>
                  </p>
                  <p className={`mt-2 text-sm ${p.featured ? 'text-ink/60' : 'text-white/60'}`}>{p.note}</p>
                  <ul className="mt-6 space-y-2 text-sm">
                    {p.perks.map((perk) => (
                      <li key={perk} className="flex gap-2">
                        <span className={p.featured ? 'text-pop-pink' : 'text-pop-lime'}>✓</span>
                        {perk}
                      </li>
                    ))}
                  </ul>
                  <Link
                    to="/studio"
                    className={`relative mt-8 block overflow-hidden rounded-full py-3.5 text-center font-bold transition hover:scale-[1.02] ${
                      p.featured ? 'shine bg-gradient-pop text-white' : 'bg-white text-ink'
                    }`}
                  >
                    Bắt đầu ngay
                  </Link>
                </div>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* FAQ */}
      <section id="faq" className="mx-auto max-w-3xl scroll-mt-28 px-6 py-20">
        <Reveal>
          <h2 className="text-center text-4xl font-black md:text-5xl">Hỏi nhanh, đáp gọn</h2>
        </Reveal>
        <div className="mt-10 space-y-3">
          {FAQS.map((faq, i) => (
            <Reveal key={faq.q} delay={i * 80}>
              <details className="group rounded-2xl border-2 border-ink bg-white p-5 transition open:shadow-[5px_5px_0_0_var(--color-ink)]">
                <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-bold">
                  {faq.q}
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-pop-lime text-lg transition group-open:rotate-45">
                    +
                  </span>
                </summary>
                <p className="mt-3 text-ink/70">{faq.a}</p>
              </details>
            </Reveal>
          ))}
        </div>
      </section>

      {/* Final CTA */}
      <section className="px-6 pb-24">
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
    </>
  )
}
