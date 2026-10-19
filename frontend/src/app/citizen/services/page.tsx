'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { Header } from '@/components/layout';
import { ServiceCatalog } from '@/components/catalog/ServiceCatalog';
import type { OrganizationRef } from '@/lib/auth';

export default function ServicesPage() {
  const { data: organizations } = useQuery({
    queryKey: ['organizations'],
    queryFn: () => api.get<OrganizationRef[]>('/api/v1/public/organizations'),
  });

  return (
    <main className="min-h-screen bg-gray-50">
      <Header />

      <div className="container mx-auto px-4 py-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Våra e-tjänster</h1>
        <p className="text-gray-600 mb-4">
          Välj en tjänst nedan för att starta en digital ansökan.
        </p>

        {organizations && organizations.length > 1 && (
          <nav aria-label="Organisationer" className="mb-8">
            <p className="text-gray-600 mb-2">Visa bara tjänsterna från:</p>
            <ul className="flex flex-wrap gap-2">
              {organizations.map((org) => (
                <li key={org.id}>
                  <Link href={`/o/${org.slug}`} className="inline-block rounded-full border border-brand-300 bg-white px-3 py-1 text-brand-700 hover:bg-brand-50">
                    {org.name}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        )}

        <ServiceCatalog />
      </div>
    </main>
  );
}
