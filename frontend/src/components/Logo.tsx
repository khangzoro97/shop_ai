export function LogoMark({ className = 'h-9 w-9' }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className} aria-hidden="true">
      <rect width="64" height="64" rx="16" fill="#16121f" />
      <g fill="none" stroke="#fff8f3" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22V12h10" />
        <path d="M42 12h10v10" />
        <path d="M52 42v10H42" />
        <path d="M22 52H12V42" />
      </g>
      <path
        d="M30 17c1.4 8.6 4.4 11.6 13 13-8.6 1.4-11.6 4.4-13 13-1.4-8.6-4.4-11.6-13-13 8.6-1.4 11.6-4.4 13-13z"
        fill="#c6f432"
      />
      <circle cx="43" cy="42" r="4" fill="#ff3d8b" />
    </svg>
  )
}

export default function Logo({ dark = false }: { dark?: boolean }) {
  return (
    <span className="flex items-center gap-2 text-2xl font-black tracking-tight">
      <LogoMark className="h-9 w-9 shrink-0 transition group-hover:rotate-12" />
      <span className={dark ? 'text-white' : 'text-ink'}>
        Chụp <span className="text-gradient">Xịn</span>
      </span>
    </span>
  )
}
