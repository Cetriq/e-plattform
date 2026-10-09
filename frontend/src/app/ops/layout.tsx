'use client';

import { RequireRole } from '@/components/auth/RequireRole';
import { StaffShell } from '@/components/layout/StaffShell';

const nav = [
  { name: 'Driftstatus', href: '/ops/status', icon: 'pulse' as const },
  { name: 'Systemlogg', href: '/ops/events', icon: 'terminal' as const },
];

export default function OpsLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireRole roles={['OPERATIONS']}>
      <StaffShell title="IT & drift" theme="ops" nav={nav}>
        {children}
      </StaffShell>
    </RequireRole>
  );
}
