// Reveal: scroll-triggered fade-and-rise (opacity 0→1, y 24→0, 0.7 s, Apple ease), once.
// Under prefers-reduced-motion the content appears instantly.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { m, useReducedMotion } from 'motion/react';
import type { ReactNode } from 'react';
import { DURATION, EASE, RISE } from '../lib/motion';

interface Props {
  children: ReactNode;
  delay?: number;
  className?: string;
}

export function Reveal({ children, delay = 0, className }: Props) {
  const reduce = useReducedMotion();
  return (
    <m.div
      className={className}
      initial={{ opacity: 0, y: RISE }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '0px 0px -10% 0px' }}
      transition={reduce ? { duration: 0 } : { duration: DURATION, ease: EASE, delay }}
    >
      {children}
    </m.div>
  );
}
