const env = import.meta.env

export const BRAND = 'Chụp Xịn'

function digitsOnly(value: string | undefined) {
  return value?.replace(/\D/g, '') || undefined
}

function formatPhone(digits: string) {
  return digits.replace(/^(\d{4})(\d{3})(\d+)$/, '$1 $2 $3')
}

const zaloPhone = digitsOnly(env.VITE_ZALO_PHONE)
const email = env.VITE_CONTACT_EMAIL?.trim() || undefined

/** Contact links are hidden entirely until real values are configured in frontend/.env. */
export const CONTACT = {
  zaloUrl: zaloPhone ? `https://zalo.me/${zaloPhone}` : undefined,
  zaloLabel: zaloPhone ? formatPhone(zaloPhone) : undefined,
  email,
  /** Best available channel for "send us your photos" calls to action. */
  primary: zaloPhone
    ? { url: `https://zalo.me/${zaloPhone}`, channel: 'Zalo' }
    : email
      ? { url: `mailto:${email}`, channel: 'email' }
      : undefined,
}

/** Legal business details shown in the footer; each line is hidden until configured. */
export const BUSINESS = {
  name: env.VITE_BUSINESS_NAME?.trim() || undefined,
  taxId: env.VITE_BUSINESS_TAX_ID?.trim() || undefined,
  address: env.VITE_BUSINESS_ADDRESS?.trim() || undefined,
}

/** Number of product photos the team edits for free as a trial; 0 disables the offer. */
export const FREE_TRIAL_IMAGES = Math.max(0, Number(env.VITE_FREE_TRIAL_IMAGES ?? 5) || 0)

export const PROMOS = [
  'Tặng 30 credit cho shop mới',
  'Ảnh sản phẩm giữ nguyên nhãn thật',
  'Chuẩn tỉ lệ Shopee · TikTok · Facebook',
  'Lỗi AI? Tự động hoàn credit',
  ...(CONTACT.primary ? [`Gửi ảnh qua ${CONTACT.primary.channel}, team làm hộ`] : []),
]
