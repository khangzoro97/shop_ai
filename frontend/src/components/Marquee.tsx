import type { ReactNode } from 'react'

interface MarqueeProps {
  items: ReactNode[]
  className?: string
  itemClassName?: string
}

/** Infinite horizontal ticker: the list is rendered twice and shifted by half its width. */
export default function Marquee({ items, className = '', itemClassName = '' }: MarqueeProps) {
  return (
    <div className={`overflow-hidden ${className}`}>
      <div className="flex w-max animate-marquee hover:[animation-play-state:paused]">
        {[...items, ...items].map((item, i) => (
          <div key={i} aria-hidden={i >= items.length} className={`shrink-0 ${itemClassName}`}>
            {item}
          </div>
        ))}
      </div>
    </div>
  )
}
