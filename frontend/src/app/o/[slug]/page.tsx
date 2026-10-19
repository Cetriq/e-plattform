'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { Header } from '@/components/layout';
import { ServiceCatalog } from '@/components/catalog/ServiceCatalog';
import type { OrganizationRef } from '@/lib/auth';

/**
 * One organisation's e-services. The organisation decides the catalogue and
 * who receives the case; a citizen's own cases are found under "Mina ärenden"
 * whichever organisation handles them.
 */
export default function OrganizationServicesPage() {
  const { slug } = useParams<{ slug: string }>();
  const { data: organizations, isLoading } = useQuery({
    queryKey: ['organizations'],
    queryFn: () => api.get<OrganizationRef[]>('/api/v1/public/organizations'),
  });
  const organization = organizations?.find((org) => org.slug === slug);

  return (
    <main className="min-h-screen bg-gray-50">
      <Header />

      <div className="container mx-auto px-4 py-8">
        {isLoading ? (
          <p className="text-gray-600">Laddar…</p>
        ) : organization ? (
          <>
            <h1 className="text-3xl font-bold text-gray-900 mb-2">E-tjänster från {organization.name}</h1>
            <p className="text-gray-600 mb-8">
              Välj en tjänst för att starta en digital ansökan. Den skickas till {organization.name}.{' '}
              <Link href="/citizen/services" className="text-brand-700 underline">Visa alla organisationers tjänster</Link>
            </p>
            <ServiceCatalog organization={organization.slug} />
          </>
        ) : (
          <>
            <h1 className="text-3xl font-bold text-gray-900 mb-2">Organisationen finns inte</h1>
            <p className="text-gray-600">
              Det finns ingen organisation med adressen /o/{slug}.{' '}
              <Link href="/citizen/services" className="text-brand-700 underline">Visa alla e-tjänster</Link>
            </p>
          </>
        )}
      </div>
    </main>
  );
}
