'use client';

import { useEffect } from 'react';
import { usePathname } from 'next/navigation';

const SITE = 'e-Plattform';

function mainHeading(): HTMLElement | null {
  return document.querySelector('main h1') ?? document.querySelector('h1');
}

/**
 * "Hoppa till huvudinnehåll": moves focus to the page's main heading, which
 * comes after the header and navigation on every page (WCAG 2.4.1).
 */
export function SkipLink() {
  return (
    <a
      href="#main-content"
      onClick={(e) => {
        const heading = mainHeading();
        if (!heading) return;
        e.preventDefault();
        heading.setAttribute('tabindex', '-1');
        heading.focus();
      }}
      className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-[100] focus:bg-white focus:text-brand-700 focus:px-4 focus:py-2 focus:rounded-md focus:shadow-lg focus:outline focus:outline-2 focus:outline-brand-600"
    >
      Hoppa till huvudinnehåll
    </a>
  );
}

/**
 * Names each page after its main heading, e.g. "Mina ärenden – e-Plattform"
 * (WCAG 2.4.2). Pages are client-rendered and their headings often arrive
 * with the data, so the title follows the heading as it changes.
 */
export function DocumentTitle() {
  const pathname = usePathname();

  useEffect(() => {
    const update = () => {
      const text = mainHeading()?.textContent?.replace(/\s+/g, ' ').trim();
      const title = text && text !== SITE ? `${text} – ${SITE}` : SITE;
      if (document.title !== title) document.title = title;
    };
    update();
    const observer = new MutationObserver(update);
    observer.observe(document.body, { childList: true, subtree: true, characterData: true });
    return () => observer.disconnect();
  }, [pathname]);

  return null;
}
