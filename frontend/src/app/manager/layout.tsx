'use client';

import { RequireRole } from '@/components/auth/RequireRole';

export default function ManagerLayout({ children }: { children: React.ReactNode }) {
  return <RequireRole roles={['MANAGER', 'ADMIN']}>{children}</RequireRole>;
}
