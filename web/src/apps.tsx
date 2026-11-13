// Page roots, shared by the browser entries (hydrate) and the build-time pre-renderer.
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { Footer } from './components/Footer';
import { Nav } from './components/Nav';
import { MotionProvider } from './lib/motion';
import { DevelopersPage } from './pages/DevelopersPage';
import { Architecture } from './sections/Architecture';
import { Builder } from './sections/Builder';
import { Developers } from './sections/Developers';
import { Features } from './sections/Features';
import { Hero } from './sections/Hero';
import { Idempotency } from './sections/Idempotency';
import { Ledger } from './sections/Ledger';
import { Lifecycle } from './sections/Lifecycle';

export function HomeApp() {
  return (
    <MotionProvider>
      <a href="#main" className="skip-link">Skip to content</a>
      <Nav />
      <main id="main">
        <Hero />
        <Idempotency />
        <Lifecycle />
        <Ledger />
        <Features />
        <Developers />
        <Architecture />
        <Builder />
      </main>
      <Footer />
    </MotionProvider>
  );
}

export function DevelopersApp() {
  return (
    <MotionProvider>
      <a href="#main" className="skip-link">Skip to content</a>
      <Nav current="Developers" />
      <main id="main">
        <DevelopersPage />
      </main>
      <Footer />
    </MotionProvider>
  );
}
