import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import { Providers } from './providers';
import { Footer } from '@/components/layout';
import { DocumentTitle, SkipLink } from '@/components/layout/PageAccessibility';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'e-Plattform',
  description: 'Modern e-tjänstplattform för offentlig förvaltning',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="sv">
      <body className={inter.className}>
        <SkipLink />
        <Providers>
          <DocumentTitle />
          <div id="main-content" className="flex min-h-screen flex-col">
            <div className="flex-1">{children}</div>
            <Footer />
          </div>
        </Providers>
      </body>
    </html>
  );
}
