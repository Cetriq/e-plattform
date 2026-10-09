'use client';

import { RequireRole } from '@/components/auth/RequireRole';
import { WorkspaceShell } from '@/components/layout/WorkspaceShell';

const nav = [
  { name: 'Spårbarhetslogg', href: '/security/audit' },
  { name: 'Registrerade', href: '/security/people' },
  { name: 'Gallring', href: '/security/retention' },
];

export default function SecurityLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireRole roles={['SECURITY_OFFICER']}>
      <WorkspaceShell title="Informationssäkerhet & dataskydd" headerClass="bg-slate-800" nav={nav}>
        {children}
      </WorkspaceShell>
    </RequireRole>
  );
}
