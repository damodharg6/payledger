// A tiny syntax highlighter (strings, numbers, keys, keywords, comments) for the code panel.
// 40 lines instead of a 100 KB library keeps the page fast. Monochrome plus one accent tint.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import type { ReactNode } from 'react';

export type Lang = 'bash' | 'java' | 'js' | 'json' | 'http';

const KEYWORDS: Record<Lang, ReadonlySet<string>> = {
  bash: new Set(['curl', 'export', 'docker', 'compose']),
  java: new Set(['var', 'new', 'import', 'public', 'class', 'static', 'void', 'final', 'return', 'throws']),
  js: new Set(['const', 'await', 'let', 'return', 'new', 'async', 'function', 'import', 'from']),
  json: new Set(),
  http: new Set(['GET', 'POST', 'PUT', 'DELETE']),
};
const LITERALS = new Set(['null', 'true', 'false']);

// 1 string · 2 comment · 3 number · 4 word
// A "//" right after ":" (as in http://) is a URL, not a comment.
const TOKEN = /("(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*')|((?<![:\w])\/\/[^\n]*|(?<![\w$/:#])#[^\n]*)|(\b\d+(?:\.\d+)?\b)|(\b[A-Za-z_][\w]*\b)/g;

export function highlight(code: string, lang: Lang): ReactNode[] {
  const out: ReactNode[] = [];
  let last = 0;
  let key = 0;
  for (const m of code.matchAll(TOKEN)) {
    const [token, str, comment, num, word] = m;
    const at = m.index ?? 0;
    if (at > last) out.push(code.slice(last, at));
    let cls: string | null = null;
    if (str !== undefined) {
      const isKey = (lang === 'json' || lang === 'js') && /^\s*:/.test(code.slice(at + token.length));
      cls = isKey ? 'tok-key' : 'tok-str';
    } else if (comment !== undefined) cls = 'tok-com';
    else if (num !== undefined) cls = 'tok-num';
    else if (word !== undefined) cls = KEYWORDS[lang].has(word) ? 'tok-kw' : LITERALS.has(word) ? 'tok-num' : null;
    out.push(cls ? <span key={key++} className={cls}>{token}</span> : token);
    last = at + token.length;
  }
  if (last < code.length) out.push(code.slice(last));
  return out;
}
