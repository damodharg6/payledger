/**
 * Site-wide facts: links, the author line, and the real benchmark numbers shown on the site.
 * Every number here comes from the repository's own test run; nothing is invented.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */

export const AUTHOR = 'Giddaluru Damodhar (MrDamodar), Agentic AI Engineer';

/** TODO: replace with the real repository URL once the code is pushed to GitHub. */
export const GITHUB_URL = 'https://github.com/';
/** TODO: replace with your LinkedIn profile URL. */
export const LINKEDIN_URL = 'https://www.linkedin.com/';

export const DOCS_URL = '/developers/';

/**
 * From `./mvnw verify` on 13 Nov 2026:
 * DoubleChargeProofIntegrationTest and ChaosLedgerProofIntegrationTest, JaCoCo report,
 * plus the dashboard's Vitest suite.
 */
export const BENCH = {
  concurrentRetries: 50,
  chargesFromRetries: 1,
  chaosPayments: 1000,
  chaosFailedDebits: 116,
  chaosReversed: 116,
  chaosLateReversals: 0,
  chaosTrialBalancePaise: 52_877_750, // debits = credits
  paiseLost: 0,
  backendTests: 188,
  dashboardTests: 14,
  lineCoveragePct: 96.1,
  source: 'Measured by the repository’s own test suite (./mvnw verify), 13 Nov 2026.',
} as const;

export const NAV_LINKS = [
  { label: 'Idempotency', href: '/#idempotency' },
  { label: 'Lifecycle', href: '/#lifecycle' },
  { label: 'Ledger', href: '/#ledger' },
  { label: 'Features', href: '/#features' },
  { label: 'Architecture', href: '/#architecture' },
  { label: 'Developers', href: '/developers/' },
] as const;
