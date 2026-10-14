import { useEffect, useRef, useState } from 'react'
import { api } from '../api.ts'
import { useAuth } from '../auth/context.ts'

interface GoogleIdentity {
  accounts: {
    id: {
      initialize: (options: { client_id: string; callback: (response: { credential: string }) => void }) => void
      renderButton: (element: HTMLElement, options: Record<string, string | number>) => void
    }
  }
}

declare global {
  interface Window {
    google?: GoogleIdentity
  }
}

const SCRIPT_URL = 'https://accounts.google.com/gsi/client'

let clientIdRequest: Promise<string> | undefined
let scriptRequest: Promise<void> | undefined

function loadScript(): Promise<void> {
  scriptRequest ??= new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SCRIPT_URL
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      scriptRequest = undefined
      reject(new Error('Không tải được Google'))
    }
    document.head.appendChild(script)
  })
  return scriptRequest
}

/** "Tiếp tục với Google"; renders nothing until GOOGLE_CLIENT_ID is set on the server. */
export default function GoogleButton({ onError }: { onError: (message: string) => void }) {
  const { signIn } = useAuth()
  const container = useRef<HTMLDivElement>(null)
  const [enabled, setEnabled] = useState(false)

  useEffect(() => {
    let cancelled = false
    clientIdRequest ??= api
      .authOptions()
      .then((o) => o.googleClientId)
      .catch(() => '')
    clientIdRequest
      .then(async (clientId) => {
        if (!clientId || cancelled) return
        await loadScript()
        const google = window.google
        if (cancelled || !google || !container.current) return
        google.accounts.id.initialize({
          client_id: clientId,
          callback: ({ credential }) => {
            api
              .googleLogin(credential)
              .then(signIn)
              .catch((e: Error) => onError(e.message))
          },
        })
        google.accounts.id.renderButton(container.current, {
          theme: 'outline',
          size: 'large',
          shape: 'pill',
          text: 'continue_with',
          locale: 'vi',
          width: container.current.clientWidth || 320,
        })
        setEnabled(true)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [signIn, onError])

  return (
    <div className={enabled ? 'mt-6' : undefined}>
      <div ref={container} className={`flex w-full justify-center ${enabled ? 'min-h-11' : ''}`} />
      {enabled && (
        <div className="mt-6 flex items-center gap-3 text-xs font-bold text-ink/40 uppercase">
          <span className="h-px flex-1 bg-ink/10" />
          hoặc dùng email
          <span className="h-px flex-1 bg-ink/10" />
        </div>
      )}
    </div>
  )
}
