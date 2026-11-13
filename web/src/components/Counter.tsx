// Counter: counts up to a real number when scrolled into view.
// The static HTML already contains the final value (good for SEO and no-JS readers); the count
// only replays if the number starts off-screen. Width is fixed by tabular figures, so nothing shifts.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { animate, useInView, useReducedMotion } from 'motion/react';
import { useEffect, useRef, useState } from 'react';
import { EASE } from '../lib/motion';

interface Props {
  value: number;
  format: (n: number) => string;
  duration?: number;
  delay?: number;
  className?: string;
}

export function Counter({ value, format, duration = 1.4, delay = 0, className = '' }: Props) {
  const ref = useRef<HTMLSpanElement>(null);
  const inView = useInView(ref, { once: true, margin: '0px 0px -10% 0px' });
  const reduce = useReducedMotion();
  const [armed, setArmed] = useState(false);

  // Only animate numbers the reader has not seen yet.
  useEffect(() => {
    const el = ref.current;
    if (!el || reduce) return;
    if (el.getBoundingClientRect().top > window.innerHeight) {
      el.textContent = format(0);
      setArmed(true);
    }
  }, [format, reduce]);

  useEffect(() => {
    const el = ref.current;
    if (!el || !armed || !inView) return;
    const controls = animate(0, value, {
      duration,
      delay,
      ease: EASE,
      onUpdate: (v) => { el.textContent = format(v); },
    });
    return () => controls.stop();
  }, [armed, inView, value, format, duration, delay]);

  return <span ref={ref} className={`tabular-nums ${className}`}>{format(value)}</span>;
}
