/**
 * Internal links that work wherever the site is hosted: at a domain root ("/") or under a
 * sub-path such as GitHub Pages' "/payledger/". Vite sets BASE_URL from the build's `base`.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
export function withBase(path: string): string {
  return import.meta.env.BASE_URL + path.replace(/^\//, '');
}
