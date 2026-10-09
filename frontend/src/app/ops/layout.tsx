'use client';

import { RequireRole } from '@/components/auth/RequireRole';
import { WorkspaceShell } from '@/components/layout/WorkspaceShell';

const nav = [
  { name: 'Driftstatus', href: '/ops/status' },
  { name: 'Systemlogg', href: '/ops/events' },
];

export default function OpsLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireRole roles={['OPERATIONS']}>
      <WorkspaceShell title="IT & drift" headerClass="bg-teal-800" nav={nav}>
        {children}
      </WorkspaceShell>
    </RequireRole>
  );
}
