export interface Plan {
  name: string
  /** Price in đồng. */
  price: number
  credits: number
  note: string
  perks: string[]
  featured?: boolean
}

export const PLANS: Plan[] = [
  { name: 'Gói nhỏ', price: 99_000, credits: 50, note: '~25 ảnh sản phẩm', perks: ['Đủ mọi tính năng AI', 'Ảnh chuẩn 3 tỉ lệ sàn'] },
  {
    name: 'Shop',
    price: 299_000,
    credits: 180,
    note: '~90 ảnh hoặc 45 giây video 720p',
    perks: ['Mọi thứ của Gói nhỏ', 'Hỗ trợ trực tiếp từ team'],
    featured: true,
  },
  { name: 'Pro', price: 799_000, credits: 550, note: 'Cho shop đăng bài mỗi ngày', perks: ['Mọi thứ của gói Shop', 'Tư vấn concept riêng'] },
]

export const IMAGE_CREDITS = 2

export const FEATURED_PLAN = PLANS.find((p) => p.featured) ?? PLANS[0]

/** Đồng per credit in the most popular plan, used to show credit prices in money. */
export const CREDIT_VND = FEATURED_PLAN.price / FEATURED_PLAN.credits

/** Rounded to the nearest 100đ, e.g. "3.300đ". */
export function formatVnd(amount: number) {
  return `${(Math.round(amount / 100) * 100).toLocaleString('vi-VN')}đ`
}

export function formatPlanPrice(price: number) {
  return `${price / 1000}K`
}
