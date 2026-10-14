import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { CONTACT } from '../config.ts'

export type PolicyKey = 'terms' | 'privacy' | 'refund'

interface Policy {
  title: string
  sections: { heading: string; body: string[] }[]
}

const contactLine = CONTACT.primary
  ? `Liên hệ Chụp Xịn qua ${[CONTACT.zaloUrl && `Zalo ${CONTACT.zaloLabel}`, CONTACT.email].filter(Boolean).join(' hoặc ')}.`
  : 'Liên hệ Chụp Xịn qua kênh hỗ trợ trên trang web.'

/** Draft wording that mirrors how the product behaves today; needs legal review before launch. */
const POLICIES: Record<PolicyKey, Policy> = {
  terms: {
    title: 'Điều khoản sử dụng',
    sections: [
      {
        heading: 'Dịch vụ',
        body: [
          'Chụp Xịn dùng AI để tạo ảnh và video quảng cáo từ ảnh sản phẩm bạn tải lên. Mỗi lượt tạo trừ một số credit hiển thị trên nút Tạo trước khi bạn bấm.',
          'Kết quả AI có thể khác nhau giữa các lần tạo. Chữ nhỏ trên sản phẩm có thể bị mờ trong video hoặc ảnh có người mẫu; hãy kiểm tra kỹ trước khi đăng bán.',
        ],
      },
      {
        heading: 'Nội dung bạn tải lên',
        body: [
          'Bạn chỉ tải lên ảnh sản phẩm mà bạn có quyền sử dụng, không vi phạm thương hiệu, bản quyền hay quyền hình ảnh của người khác.',
          'Không dùng Chụp Xịn để tạo nội dung lừa đảo, giả mạo sản phẩm của thương hiệu khác, khiêu dâm hoặc trái pháp luật Việt Nam.',
        ],
      },
      {
        heading: 'Quyền với ảnh và video tạo ra',
        body: [
          'Bạn được dùng ảnh và video tạo ra cho mục đích bán hàng và quảng cáo của shop mình. Chụp Xịn chỉ dùng kết quả của bạn làm ví dụ công khai khi có sự đồng ý của bạn.',
        ],
      },
      {
        heading: 'Tài khoản',
        body: [
          'Mỗi shop dùng một tài khoản. Credit tặng khi xác minh số điện thoại chỉ áp dụng một lần cho mỗi số. Chụp Xịn có thể khóa tài khoản lạm dụng ưu đãi hoặc vi phạm điều khoản.',
        ],
      },
    ],
  },
  privacy: {
    title: 'Chính sách bảo mật',
    sections: [
      {
        heading: 'Thông tin Chụp Xịn lưu',
        body: [
          'Email, tên shop, mật khẩu đã mã hóa và số điện thoại nếu bạn xác minh.',
          'Ảnh sản phẩm bạn tải lên, mô tả bạn nhập và ảnh, video AI tạo ra, để bạn xem lại và tải về.',
        ],
      },
      {
        heading: 'Đối tác xử lý AI',
        body: [
          'Để tạo ảnh và video, ảnh sản phẩm và mô tả của bạn được gửi tới nhà cung cấp mô hình AI (fal.ai, máy chủ đặt ở nước ngoài). Chụp Xịn không bán dữ liệu của bạn cho bên thứ ba.',
        ],
      },
      {
        heading: 'Quyền của bạn',
        body: [
          `Bạn có thể yêu cầu xem, sửa hoặc xóa tài khoản cùng toàn bộ ảnh và video đã lưu. ${contactLine}`,
        ],
      },
    ],
  },
  refund: {
    title: 'Chính sách hoàn tiền',
    sections: [
      {
        heading: 'Hoàn credit tự động',
        body: [
          'Nếu AI gặp lỗi hoặc không trả kết quả trong 15 phút, lượt tạo được đánh dấu thất bại và credit được hoàn lại tài khoản ngay, bạn không cần làm gì.',
        ],
      },
      {
        heading: 'Kết quả chưa ưng ý',
        body: [
          'Kết quả AI đã tạo thành công không được hoàn credit tự động. Nếu ảnh sai rõ ràng (đổi nhãn, sai sản phẩm), hãy chụp màn hình kết quả gửi cho team để được xem xét hoàn credit.',
        ],
      },
      {
        heading: 'Hoàn tiền gói credit',
        body: [
          `Gói credit chưa sử dụng có thể yêu cầu hoàn tiền trong 7 ngày kể từ khi thanh toán. ${contactLine}`,
        ],
      },
    ],
  },
}

export default function PolicyPage({ policy }: { policy: PolicyKey }) {
  const { title, sections } = POLICIES[policy]

  useEffect(() => {
    const previous = document.title
    document.title = `${title} · Chụp Xịn`
    window.scrollTo(0, 0)
    return () => {
      document.title = previous
    }
  }, [title])

  return (
    <section className="mx-auto max-w-3xl px-6 py-12 md:py-20">
      <Link to="/" className="text-sm font-bold text-ink/50 hover:text-ink">
        ← Trang chủ
      </Link>
      <h1 className="mt-4 text-4xl font-black md:text-5xl">{title}</h1>
      <div className="mt-10 space-y-8">
        {sections.map((section) => (
          <div key={section.heading}>
            <h2 className="text-xl font-black">{section.heading}</h2>
            {section.body.map((paragraph) => (
              <p key={paragraph} className="mt-3 leading-relaxed text-ink/70">
                {paragraph}
              </p>
            ))}
          </div>
        ))}
      </div>
    </section>
  )
}
