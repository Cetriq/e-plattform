'use client';

import { RequireRole } from '@/components/auth/RequireRole';
import { StaffShell } from '@/components/layout/StaffShell';

const nav = [
  { name: 'E-tjänster', href: '/admin/flows', icon: 'document' as const },
  { name: 'Kategorier', href: '/admin/categories', icon: 'folder' as const },
  { name: 'Statistik', href: '/admin/statistics', icon: 'chart' as const },
  { name: 'Användare', href: '/admin/users', icon: 'users' as const },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <RequireRole roles={['ADMIN', 'FLOW_EDITOR']}>
      <StaffShell title="Admin" theme="admin" nav={nav}
                  links={[{ name: 'Till handläggare', href: '/manager/dashboard' }]}>
        {children}
      </StaffShell>
    </RequireRole>
  );
}
