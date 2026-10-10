'use client';

import { RequireRole } from '@/components/auth/RequireRole';
import { StaffShell } from '@/components/layout/StaffShell';

const nav = [
  { name: 'Spårbarhetslogg', href: '/security/audit', icon: 'log' as const },
  { name: 'Registrerade', href: '/security/people', icon: 'person' as const },
  { name: 'Gallring', href: '/security/retention', icon: 'archive' as const },
];

export default function SecurityLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireRole roles={['SECURITY_OFFICER']}>
      <StaffShell title="Informationssäkerhet & dataskydd" theme="security" nav={nav}>
        {children}
      </StaffShell>
    </RequireRole>
  );
}
