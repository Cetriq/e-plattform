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
          Målet är att e-Plattformen ska uppfylla WCAG 2.1 nivå AA, som är kravet i
          lagen. Vi har gått igenom hela plattformen, både e-tjänsterna för invånare
          och verktygen för handläggare och administratörer, och åtgärdat de brister
          vi hittade. Vi känner i dag inte till några kvarvarande brister mot WCAG 2.1
          AA. Webbplatsen har dock ännu inte testats med skärmläsare eller granskats av
          en extern part. Därför redovisar vi den som delvis förenlig tills den
          granskningen är gjord.
        </p>

        <h2>Det här har vi gjort</h2>
        <ul>
          <li>Alla funktioner går att använda med enbart tangentbord, med synlig fokusmarkering.</li>
          <li>
            Formulär har kopplade etiketter. Fält som hör ihop är grupperade, och
            personuppgiftsfält kan fyllas i automatiskt. Felmeddelanden visas vid
            fältet och samlat, och fokus flyttas till första felet.
          </li>
          <li>
            Signaturfältet kan också fyllas i genom att du skriver ditt namn, om du inte
            kan rita med mus eller finger.
          </li>
          <li>Dialogrutor håller kvar fokus, stängs med Esc och lämnar tillbaka fokus.</li>
          <li>
            Text och knappar har minst 4,5:1 i kontrast, även statusfärger som
            administratörer väljer själva.
          </li>
          <li>Sidorna fungerar i 320 pixlars bredd utan sidledes rullning, med undantag för datatabeller.</li>
          <li>
            Varje sida har en egen titel, och en länk för att hoppa direkt till
            huvudinnehållet.
          </li>
          <li>
            Innan inloggningen löper ut får du en varning och kan välja att fortsätta
            vara inloggad.
          </li>
          <li>
            PDF-utskrifter av ärenden är taggade, har svenska som språk och innehåller
            inbäddade typsnitt.
          </li>
        </ul>

        <h2>Innehåll som inte omfattas</h2>
        <ul>
          <li>
            Filer som du eller andra laddar upp som bilagor till ett ärende. Hur
            tillgängliga de är beror på filen.
          </li>
          <li>Det kartstöd som är under utveckling. Plats anges i dag med adress eller koordinater.</li>
        </ul>

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
          digital offentlig service, eftersom den ännu inte är granskad med
          skärmläsare eller av extern part (se ovan).
        </p>

        <h2>Hur vi har testat webbplatsen</h2>
        <p>
          Vi har gjort en egen granskning mot WCAG 2.1 AA. Samtliga sidtyper har
          testats för alla roller med det automatiska verktyget axe-core, även
          formulärets alla steg, felmeddelanden och dialogrutor. Vi har också
          kontrollerat tangentbordsnavigering, fokushantering, sidtitlar och
          visning i 320 pixlars bredd. Testerna gav inga kvarvarande avvikelser.
          De automatiska testerna körs vid varje ändring av koden, så att nya
          brister upptäcks innan de når webbplatsen.
          Test med skärmläsare (NVDA, VoiceOver) och en extern granskning återstår.
        </p>
        <p>Redogörelsen uppdaterades senast den 10 oktober 2026.</p>
      </main>
    </>
  );
}
