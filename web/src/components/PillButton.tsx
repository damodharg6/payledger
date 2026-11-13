// PillButton: the primary pill (accent fill) and the secondary text link with a › chevron.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { ChevronRight } from '../lib/icons';
import type { ReactNode } from 'react';

interface Props {
  href: string;
  variant?: 'primary' | 'link';
  size?: 'md' | 'sm';
  external?: boolean;
  children: ReactNode;
}

export function PillButton({ href, variant = 'primary', size = 'md', external = false, children }: Props) {
  const rel = external ? { target: '_blank', rel: 'noopener noreferrer' } : {};
  if (variant === 'link') {
    return (
      <a href={href} {...rel}
         className="group inline-flex items-center gap-0.5 text-[17px] leading-none text-accent hover:underline underline-offset-4">
        {children}
        <ChevronRight aria-hidden className="size-[18px] translate-y-px transition-transform duration-300 ease-apple group-hover:translate-x-0.5" />
      </a>
    );
  }
  const sizing = size === 'sm' ? 'min-h-7 px-3.5 py-1 text-micro' : 'min-h-11 px-[22px] py-3 text-[17px]';
  return (
    <a href={href} {...rel}
       className={`inline-flex items-center justify-center rounded-pill bg-accent font-normal leading-none text-white transition-colors duration-200 hover:bg-accent-hover ${sizing}`}>
      {children}
    </a>
  );
}
