// CodePanel: tabbed code samples (keyboard-accessible tabs with arrow keys), a copy button,
// and an optional response pane beside it. Long lines scroll inside the panel, never the page.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { Check, Copy } from '../lib/icons';
import { useId, useRef, useState, type KeyboardEvent } from 'react';
import type { Sample } from '../content/code';
import { highlight } from '../lib/highlight';

function CopyButton({ text, label }: { text: string; label: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  };
  return (
    <button type="button" onClick={copy}
            className="inline-flex min-h-8 items-center gap-1.5 rounded-pill px-3 text-micro text-code-dim transition-colors hover:bg-white/10 hover:text-white">
      {copied ? <Check aria-hidden className="size-3.5" /> : <Copy aria-hidden className="size-3.5" />}
      <span>{copied ? 'Copied' : 'Copy'}</span>
      <span className="sr-only"> {label}</span>
      <span className="sr-only" aria-live="polite">{copied ? `${label} copied to clipboard` : ''}</span>
    </button>
  );
}

function Code({ sample, label }: { sample: Sample; label: string }) {
  return (
    <pre tabIndex={0} aria-label={label}
         className="overflow-x-auto px-5 pb-6 pt-1 font-mono text-[12.5px] leading-[1.7] text-code-text md:px-6 md:text-[13px]">
      <code>{highlight(sample.code, sample.lang)}</code>
    </pre>
  );
}

interface Props {
  samples: Sample[];
  response?: Sample;
  /** What the request does, for screen readers and the copy button. */
  title: string;
}

export function CodePanel({ samples, response, title }: Props) {
  const [active, setActive] = useState(0);
  const baseId = useId();
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    const dir = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
    const jump = e.key === 'Home' ? 0 : e.key === 'End' ? samples.length - 1 : null;
    if (!dir && jump === null) return;
    e.preventDefault();
    const next = jump ?? (active + dir + samples.length) % samples.length;
    setActive(next);
    tabRefs.current[next]?.focus();
  };

  const sample = samples[active];
  return (
    <div className={`grid min-w-0 grid-cols-[minmax(0,1fr)] gap-4 ${response ? 'lg:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]' : ''}`}>
      <div className="min-w-0 overflow-hidden rounded-tile bg-code">
        <div className="flex min-h-12 items-center justify-between gap-3 border-b border-white/10 px-3 py-2 md:px-4">
          {samples.length === 1 ? (
            <p className="px-2 text-micro font-medium text-code-dim md:px-2">{sample.label}</p>
          ) : (
          <div role="tablist" aria-label={`${title}: language`} className="flex gap-1" onKeyDown={onKey}>
            {samples.map((s, i) => (
              <button
                key={s.label}
                ref={(el) => { tabRefs.current[i] = el; }}
                id={`${baseId}-tab-${i}`}
                role="tab"
                type="button"
                aria-selected={i === active}
                aria-controls={`${baseId}-panel`}
                tabIndex={i === active ? 0 : -1}
                onClick={() => setActive(i)}
                className={`min-h-8 rounded-pill px-3 text-micro font-medium transition-colors ${
                  i === active ? 'bg-white/15 text-white' : 'text-code-dim hover:text-white'
                }`}
              >
                {s.label}
              </button>
            ))}
          </div>
          )}
          <CopyButton text={sample.code} label={`${sample.label} example`} />
        </div>
        <div id={`${baseId}-panel`} className="pt-4"
             {...(samples.length > 1 ? { role: 'tabpanel', 'aria-labelledby': `${baseId}-tab-${active}` } : {})}>
          <Code sample={sample} label={`${title} in ${sample.label}`} />
        </div>
      </div>

      {response && (
        <div className="min-w-0 overflow-hidden rounded-tile bg-code">
          <div className="flex min-h-12 items-center justify-between gap-3 border-b border-white/10 px-5 py-2 md:px-6">
            <p className="text-micro font-medium text-code-dim">Response · <span className="text-white">{response.label}</span></p>
            <CopyButton text={response.code} label="response" />
          </div>
          <div className="pt-4">
            <Code sample={response} label={`Response: ${response.label}`} />
          </div>
        </div>
      )}
    </div>
  );
}
