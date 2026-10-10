import Link from 'next/link';
import { API_BASE_URL } from '@/lib/config';

export function Footer() {
  return (
    <footer className="border-t bg-white mt-16">
      <div className="container mx-auto px-4 py-8 text-sm text-gray-600">
        <div className="grid gap-6 md:grid-cols-3">
          <div>
            <p className="font-semibold text-gray-900 mb-2">e-Plattform</p>
            <p className="text-gray-600">
              Digitala tjänster för kommunen — enkelt, tryggt och tillgängligt.
            </p>
          </div>
          <div>
            <p className="font-semibold text-gray-900 mb-2">Support</p>
            <ul className="space-y-1">
              <li>
                <Link href="/citizen/services" className="hover:text-gray-900">
                  Våra e-tjänster
                </Link>
              </li>
              <li>
                <Link href="/citizen/cases" className="hover:text-gray-900">
                  Mina ärenden
                </Link>
              </li>
            </ul>
          </div>
          <div>
            <p className="font-semibold text-gray-900 mb-2">Om webbplatsen</p>
            <ul className="space-y-1">
              <li>
                <Link href="/tillganglighet" className="hover:text-gray-900">
                  Tillgänglighetsredogörelse
                </Link>
              </li>
              <li>
                {/* Served by the backend, so a plain link rather than client navigation */}
                <a href={`${API_BASE_URL}/swagger-ui/index.html`} className="hover:text-gray-900">
                  API-dokumentation
                </a>
              </li>
            </ul>
          </div>
        </div>
        <div className="mt-8 pt-4 border-t text-xs text-gray-500">
          e-Plattform är en öppen källkodslösning för digitala kommunala tjänster.
        </div>
      </div>
    </footer>
  );
}
