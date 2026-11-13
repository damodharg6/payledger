// Developers: one request, three languages, the real response shape beside it.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { CodePanel } from '../components/CodePanel';
import { Headline } from '../components/Headline';
import { PillButton } from '../components/PillButton';
import { Reveal } from '../components/Reveal';
import { Section } from '../components/Section';
import { CREATE_PAYMENT, PAYMENT_RESPONSE } from '../content/code';
import { DOCS_URL } from '../content/site';

export function Developers() {
  return (
    <Section id="developers" tone="white" labelledBy="developers-title">
      <Headline id="developers-title" eyebrow="Developers"
                sub="Every call carries an X-API-Key. Payments also carry an Idempotency-Key, so a retry is always safe.">
        <span className="whitespace-nowrap">Start with</span> <span className="whitespace-nowrap">one request.</span>
      </Headline>
      <Reveal className="mx-auto mt-14 max-w-grid md:mt-20">
        <CodePanel title="Create a payment" samples={CREATE_PAYMENT} response={PAYMENT_RESPONSE} />
      </Reveal>
      <p className="mt-10 text-center">
        <PillButton href={DOCS_URL} variant="link">Read the API docs</PillButton>
      </p>
    </Section>
  );
}
