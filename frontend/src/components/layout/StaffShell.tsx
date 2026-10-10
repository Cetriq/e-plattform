'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';

export type StaffIcon =
  | 'document' | 'folder' | 'chart' | 'users'
  | 'log' | 'person' | 'archive' | 'pulse' | 'terminal';

const ICON_PATHS: Record<StaffIcon, string[]> = {
  document: ['M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z'],
  folder: ['M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z'],
  chart: ['M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z'],
  users: ['M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z'],
  log: ['M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4'],
  person: ['M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z'],
  archive: ['M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4'],
  pulse: ['M3 12h4l3-8 4 16 3-8h4'],
  terminal: ['M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z'],
};

function NavIcon({ icon }: { icon: StaffIcon }) {
  return (
    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
      {ICON_PATHS[icon].map((d) => (
        <path key={d} strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={d} />
      ))}
    </svg>
  );
}

// The theme class sets the brand colour for everything inside (globals.css)
const THEME_CLASS = {
  admin: 'theme-admin',
  security: 'theme-security',
  ops: 'theme-ops',
} as const;

export type StaffTheme = keyof typeof THEME_CLASS;

interface StaffShellProps {
  title: string;
  theme: StaffTheme;
  nav: { name: string; href: string; icon: StaffIcon }[];
  /** Extra links in the header, e.g. to other workspaces. */
  links?: { name: string; href: string }[];
  children: React.ReactNode;
}

/**
 * Frame for the staff areas (admin, information security, IT): coloured
 * header with the area name and a sidebar.
 */
export function StaffShell({ title, theme, nav, links = [], children }: StaffShellProps) {
  const pathname = usePathname();
  const { user, logout } = useAuth();

  return (
    <div className={`${THEME_CLASS[theme]} min-h-screen bg-gray-50`}>
      <header className="bg-brand-900 text-white">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16 gap-4">
            <Link href={nav[0].href} className="flex items-center gap-2 min-w-0">
              <div className="w-8 h-8 bg-white/20 rounded flex items-center justify-center shrink-0">
                <span className="text-white font-bold text-sm">E</span>
              </div>
              <span className="font-semibold truncate">{title}</span>
            </Link>
            <div className="flex items-center gap-4 text-sm">
              <span className="hidden sm:inline text-white/70">{user?.displayName}</span>
              {links.map((l) => (
                <Link key={l.href} href={l.href} className="hidden md:inline text-white/70 hover:text-white">
                  {l.name}
                </Link>
              ))}
              <Link href="/" className="hidden md:inline text-white/70 hover:text-white">
                Till medborgarsidan
              </Link>
              <button type="button" onClick={() => logout()} className="text-white/70 hover:text-white">
                Logga ut
              </button>
            </div>
          </div>
        </div>
      </header>

      <div className="flex flex-col md:flex-row">
        <aside className="md:w-64 bg-white border-b md:border-b-0 md:border-r md:min-h-[calc(100vh-4rem)] shadow-sm">
          <nav className="p-2 md:p-4 flex md:flex-col gap-1 overflow-x-auto" aria-label={title}>
            {nav.map((item) => {
              const active = pathname === item.href || pathname.startsWith(item.href + '/');
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? 'page' : undefined}
                  className={`flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors ${
                    active ? 'bg-brand-100 text-brand-700' : 'text-gray-700 hover:bg-gray-100'
                  }`}
                >
                  <NavIcon icon={item.icon} />
                  {item.name}
                </Link>
              );
            })}
          </nav>
        </aside>

        <main id="workspace-content" className="flex-1 min-w-0 p-4 md:p-8">{children}</main>
      </div>
    </div>
  );
}
