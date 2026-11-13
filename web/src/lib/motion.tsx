/**
 * Motion setup shared by every page: one easing, one duration, the lazy-loaded animation
 * features (keeps the bundle small) and reduced-motion support.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { LazyMotion, MotionConfig, domAnimation } from 'motion/react';
import type { ReactNode } from 'react';

/** Apple-like ease-out: fast start, long gentle settle. */
export const EASE = [0.22, 1, 0.36, 1] as const;
export const DURATION = 0.7;
/** Fade-and-rise distance in px. */
export const RISE = 24;

export function MotionProvider({ children }: { children: ReactNode }) {
  return (
    <MotionConfig reducedMotion="user">
      <LazyMotion features={domAnimation} strict>
        {children}
      </LazyMotion>
    </MotionConfig>
  );
}
