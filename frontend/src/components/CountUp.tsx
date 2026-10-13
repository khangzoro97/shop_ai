import { useEffect, useRef, useState } from 'react'

interface CountUpProps {
  to: number
  suffix?: string
  durationMs?: number
}

export default function CountUp({ to, suffix = '', durationMs = 1400 }: CountUpProps) {
  const ref = useRef<HTMLSpanElement>(null)
  const [value, setValue] = useState(0)

  useEffect(() => {
    const node = ref.current
    if (!node) return
    let frame = 0
    const observer = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return
      observer.disconnect()
      const start = performance.now()
      const tick = (now: number) => {
        const progress = Math.min(1, (now - start) / durationMs)
        setValue(Math.round(to * (1 - Math.pow(1 - progress, 3))))
        if (progress < 1) frame = requestAnimationFrame(tick)
      }
      frame = requestAnimationFrame(tick)
    })
    observer.observe(node)
    return () => {
      observer.disconnect()
      cancelAnimationFrame(frame)
    }
  }, [to, durationMs])

  return (
    <span ref={ref}>
      {value}
      {suffix}
    </span>
  )
}
