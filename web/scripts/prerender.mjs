/**
 * Pre-renders every page to static HTML after the build, so the first paint needs no
 * JavaScript (fast LCP, no layout shift, crawlable text). React then hydrates it.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { readFile, rm, writeFile } from 'node:fs/promises';

const root = new URL('../', import.meta.url);
const { render } = await import(new URL('dist-ssr/entry-server.js', root).href);

const PAGES = [
  ['home', 'dist/index.html'],
  ['developers', 'dist/developers/index.html'],
];

for (const [page, file] of PAGES) {
  const path = new URL(file, root);
  const html = await readFile(path, 'utf8');
  if (!html.includes('<!--app-html-->')) throw new Error(`${file} has no <!--app-html--> placeholder`);
  const body = render(page);
  await writeFile(path, html.replace('<!--app-html-->', body));
  console.log(`prerendered ${file} (${(body.length / 1024).toFixed(1)} KB of HTML)`);
}
await rm(new URL('dist-ssr/', root), { recursive: true, force: true });
