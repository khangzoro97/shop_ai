import { useAuth } from '../auth/context.ts'
import { CONTACT } from '../config.ts'

/**
 * Shown above the create button when the balance does not cover the request, so the customer
 * sees how to get credits instead of a bare "not enough credits" error after submitting.
 */
export default function CreditShortfall({ needed, onVerifyPhone }: { needed: number; onVerifyPhone: () => void }) {
  const { account } = useAuth()
  if (!account || account.credits >= needed) return null
  const canVerify = !account.phoneVerified

  return (
    <div role="status" className="animate-pop-in rounded-2xl border-2 border-ink bg-pop-lime/30 p-4">
      <p className="font-black">
        Bạn còn {account.credits} credit, lượt này cần {needed} credit.
      </p>
      <p className="mt-1 text-sm text-ink/70">
        {canVerify
          ? 'Xác minh số điện thoại để nhận 30 credit miễn phí, đủ cho khoảng 15 ảnh.'
          : 'Nhắn Zalo cho Chụp Xịn để mua thêm credit, team cộng ngay cho bạn.'}
      </p>
      <div className="mt-3 flex flex-wrap gap-2">
        {canVerify && (
          <button
            type="button"
            onClick={onVerifyPhone}
            className="min-h-11 rounded-full bg-ink px-5 text-sm font-bold text-white transition hover:bg-pop-pink"
          >
            Xác minh SĐT, nhận 30 credit
          </button>
        )}
        {CONTACT.primary && (
          <a
            href={CONTACT.primary.url}
            target="_blank"
            rel="noreferrer"
            className="flex min-h-11 items-center rounded-full border-2 border-ink bg-white px-5 text-sm font-bold transition hover:bg-ink hover:text-white"
          >
            {canVerify ? `Nhắn ${CONTACT.primary.channel} hỗ trợ` : `Nhắn ${CONTACT.primary.channel} mua credit`}
          </a>
        )}
      </div>
    </div>
  )
}
