import type { Metadata } from 'next';
import Link from 'next/link';
import { Header } from '@/components/layout';

export const metadata: Metadata = {
  title: 'Tillgänglighetsredogörelse – e-Plattform',
  description:
    'Tillgänglighetsredogörelse för e-Plattform i enlighet med lagen om tillgänglighet till digital offentlig service (DOS-lagen).',
};

export default function AccessibilityPage() {
  return (
    <>
      <Header />
      <main className="container mx-auto px-4 py-10 max-w-3xl text-gray-800 [&_h1]:text-3xl [&_h1]:font-bold [&_h1]:text-gray-900 [&_h1]:mb-6 [&_h2]:text-xl [&_h2]:font-semibold [&_h2]:text-gray-900 [&_h2]:mt-8 [&_h2]:mb-3 [&_p]:mb-4 [&_p]:leading-relaxed [&_ul]:list-disc [&_ul]:pl-6 [&_ul]:mb-4 [&_ul]:space-y-2 [&_a]:text-brand-600 [&_a:hover]:text-brand-800 [&_a]:underline">
        <h1>Tillgänglighetsredogörelse</h1>

        <p>
          Vi står bakom denna webbplats och vill att så många som möjligt ska kunna
          använda e-Plattformen. Den här sidan beskriver hur vi uppfyller lagen om
          tillgänglighet till digital offentlig service (SFS 2018:1937), kända
          brister och hur du kan kontakta oss om du upptäcker problem.
        </p>

        <h2>Hur tillgänglig är webbplatsen?</h2>
        <p>
          Vi arbetar mot kraven i WCAG 2.1 nivå AA. Delar av plattformen är
          fortfarande under utveckling och vissa funktioner är ännu inte fullt
          tillgängliga. Kända brister listas nedan.
        </p>

        <h2>Kända brister</h2>
        <ul>
          <li>
            Ritad namnteckning (signaturfält) är i dag svår att använda utan mus
            eller pekskärm. Vi arbetar på ett tangentbordsvänligt alternativ.
          </li>
          <li>
            Kartkomponenten i vissa e-tjänster saknar fullständigt
            tangentbordsstöd.
          </li>
          <li>
            Skärmläsarstöd för stegindikatorn i flerstegsformulär kan upplevas
            repetitivt.
          </li>
        </ul>
        <p>
          Vår ambition är att alla brister ska vara åtgärdade senast inom 12
          månader från publiceringsdatum för denna redogörelse.
        </p>

        <h2>Rapportera brister</h2>
        <p>
          Om du hittar problem som inte är beskrivna på denna sida, eller anser att
          vi inte uppfyller lagens krav, meddela oss så att vi får veta att
          problemet finns. Skicka e-post till{' '}
          <a href="mailto:tillganglighet@eplatform.se">
            tillganglighet@eplatform.se
          </a>
          . Vi återkopplar senast inom 14 dagar.
        </p>

        <h2>Tillsyn</h2>
        <p>
          Myndigheten för digital förvaltning (DIGG) har ansvaret för tillsyn över
          lagen om tillgänglighet till digital offentlig service. Om du inte är
          nöjd med hur vi hanterar dina synpunkter kan du{' '}
          <a
            href="https://www.digg.se/tdosanmalan"
            target="_blank"
            rel="noopener noreferrer"
          >
            anmäla bristen till DIGG
          </a>
          .
        </p>

        <h2>Teknisk information om webbplatsens tillgänglighet</h2>
        <p>
          Denna webbplats är delvis förenlig med lagen om tillgänglighet till
          digital offentlig service, på grund av de brister som beskrivs ovan.
        </p>

        <h2>Hur vi har testat webbplatsen</h2>
        <p>
          Vi gör löpande interna granskningar med automatiska verktyg (t.ex.
          axe-core) och manuell testning med skärmläsare. En fullständig extern
          granskning enligt WCAG 2.2 AA är planerad.
        </p>

        <p className="text-sm text-gray-500 mt-8">
          Redogörelsen uppdaterades senast den dag då plattformen driftsattes för
          utvärdering. För frågor eller mer information, se{' '}
          <Link href="/">startsidan</Link>.
        </p>
      </main>
    </>
  );
}
