'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { Header } from '@/components/layout';

interface FlowSummary {
  id: string;
  name: string;
  shortDescription?: string;
  categoryId?: string;
  categoryName?: string;
  typeId?: string;
  typeName?: string;
  version: number;
  status: string;
}

export default function ServicesPage() {
  const { data: flows, isLoading, error } = useQuery({
    queryKey: ['flows', 'published'],
    queryFn: async () => {
      const response = await api.get<FlowSummary[]>('/api/v1/flows');
      return response;
    },
  });

  // Group flows by category
  const categorizedFlows = flows?.reduce((acc, flow) => {
    const categoryName = flow.categoryName || 'Övrigt';
    if (!acc[categoryName]) {
      acc[categoryName] = [];
    }
    acc[categoryName].push(flow);
    return acc;
  }, {} as Record<string, FlowSummary[]>) || {};

  return (
    <main className="min-h-screen bg-gray-50">
      <Header />

      <div className="container mx-auto px-4 py-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Våra e-tjänster</h1>
        <p className="text-gray-600 mb-8">
          Välj en tjänst nedan för att starta en digital ansökan.
        </p>

        {isLoading && (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
            <span className="ml-3 text-gray-600">Laddar tjänster...</span>
          </div>
        )}

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-8">
            <p className="text-red-800">
              Kunde inte ladda tjänster. Försök igen senare.
            </p>
          </div>
        )}

        {!isLoading && !error && Object.keys(categorizedFlows).length === 0 && (
          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-8 text-center">
            <h3 className="text-lg font-medium text-yellow-800 mb-2">
              Inga tjänster tillgängliga
            </h3>
            <p className="text-yellow-700">
              Det finns inga publicerade e-tjänster för tillfället.
            </p>
          </div>
        )}

        <div className="space-y-8">
          {Object.entries(categorizedFlows).map(([category, categoryFlows]) => (
            <section key={category}>
              <h2 className="text-xl font-semibold text-gray-800 mb-4 flex items-center gap-2">
                <span className="w-1 h-6 bg-blue-600 rounded"></span>
                {category}
              </h2>
              <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
                {categoryFlows.map((flow) => (
                  <Link
                    key={flow.id}
                    href={`/citizen/services/${flow.id}`}
                    className="bg-white p-6 rounded-xl border hover:border-blue-300 hover:shadow-md transition-all group"
                  >
                    <div className="flex items-start justify-between mb-3">
                      <span className="text-xs font-medium text-blue-600 bg-blue-50 px-2 py-1 rounded">
                        {flow.typeName || 'E-tjänst'}
                      </span>
                    </div>
                    <h3 className="text-lg font-semibold text-gray-900 mb-2 group-hover:text-blue-600 transition-colors">
                      {flow.name}
                    </h3>
                    <p className="text-gray-600 text-sm line-clamp-2">
                      {flow.shortDescription || 'Starta en digital ansökan.'}
                    </p>
                    <div className="mt-4 flex items-center text-blue-600 text-sm font-medium">
                      <span>Starta ansökan</span>
                      <svg
                        className="w-4 h-4 ml-1 group-hover:translate-x-1 transition-transform"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M9 5l7 7-7 7"
                        />
                      </svg>
                    </div>
                  </Link>
                ))}
              </div>
            </section>
          ))}
        </div>
      </div>
    </main>
  );
}
