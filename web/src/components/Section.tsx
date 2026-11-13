// Section: a full-bleed band with the site's vertical rhythm and one of three backgrounds.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import type { ReactNode } from 'react';

export type Tone = 'white' | 'mist' | 'night';

const TONE: Record<Tone, string> = {
  white: 'bg-white text-ink',
  mist: 'bg-mist text-ink',
  night: 'bg-black text-white',
};

interface Props {
  id?: string;
  tone?: Tone;
  labelledBy?: string;
  className?: string;
  children: ReactNode;
}

export function Section({ id, tone = 'white', labelledBy, className = '', children }: Props) {
  return (
    <section
      id={id}
      aria-labelledby={labelledBy}
      data-tone={tone}
      className={`${TONE[tone]} px-gutter py-section ${className}`}
    >
      {children}
    </section>
  );
}
