'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';

interface WorkspaceShellProps {
  title: string;
  /** Tailwind background class for the header, e.g. "bg-slate-800". */
  headerClass: string;
  nav: { name: string; href: string }[];
  children: React.ReactNode;
}

/**
 * Frame for the staff workspaces (information security, IT/drift): header
 * with the role name and a tab bar.
 */
export function WorkspaceShell({ title, headerClass, nav, children }: WorkspaceShellProps) {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen bg-gray-100">
      <header className={`${headerClass} text-white`}>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <Link href={nav[0].href} className="flex items-center gap-2">
              <div className="w-8 h-8 bg-white/20 rounded flex items-center justify-center">
                <span className="text-white font-bold text-sm">E</span>
              </div>
              <span className="font-semibold">{title}</span>
            </Link>
            <div className="flex items-center gap-4 text-sm">
              <span className="text-white/80">{user?.displayName}</span>
              <button type="button" onClick={() => logout()} className="text-white/80 hover:text-white">
                Logga ut
              </button>
            </div>
          </div>
          <nav className="flex gap-6 -mb-px overflow-x-auto" aria-label={title}>
            {nav.map((item) => {
              const active = pathname === item.href || pathname.startsWith(item.href + '/');
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? 'page' : undefined}
                  className={`py-3 text-sm font-medium border-b-2 whitespace-nowrap ${
                    active ? 'border-white text-white' : 'border-transparent text-white/70 hover:text-white'
                  }`}
                >
                  {item.name}
                </Link>
              );
            })}
          </nav>
        </div>
      </header>
      <main id="workspace-content" className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {children}
      </main>
    </div>
  );
}
