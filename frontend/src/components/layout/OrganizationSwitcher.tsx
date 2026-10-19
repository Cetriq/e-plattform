'use client';

import { useId, useState } from 'react';
import { useAuth } from '@/context/AuthContext';

interface OrganizationSwitcherProps {
  /** "dark" on the coloured staff header, "light" in white menus. */
  tone?: 'dark' | 'light';
}

/**
 * The organisation staff are working for. Members of several organisations
 * can switch; the page then reloads so everything shows the new
 * organisation's data with the roles the user has there.
 */
export function OrganizationSwitcher({ tone = 'light' }: OrganizationSwitcherProps) {
  const { user, switchOrganization } = useAuth();
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);
  const selectId = useId();

  const active = user?.activeOrganization;
  if (!active) return null;

  const memberships = user?.memberships ?? [];
  const muted = tone === 'dark' ? 'text-white/70' : 'text-gray-500';

  if (memberships.length < 2) {
    return (
      <span className={`text-sm ${muted}`}>
        <span className="sr-only">Organisation: </span>
        {active.name}
      </span>
    );
  }

  async function onChange(event: React.ChangeEvent<HTMLSelectElement>) {
    setBusy(true);
    setFailed(false);
    if (await switchOrganization(event.target.value)) {
      window.location.reload();
    } else {
      setFailed(true);
      setBusy(false);
    }
  }

  return (
    <div className="flex items-center gap-2 text-sm">
      <label htmlFor={selectId} className={muted}>Organisation</label>
      <select
        id={selectId}
        value={active.id}
        onChange={onChange}
        disabled={busy}
        className={tone === 'dark'
          ? 'rounded border border-white/30 bg-brand-900 text-white px-2 py-1'
          : 'rounded border border-gray-300 bg-white text-gray-900 px-2 py-1'}
      >
        {memberships.map((org) => (
          <option key={org.id} value={org.id}>{org.name}</option>
        ))}
      </select>
      {failed && (
        <span role="alert" className={tone === 'dark' ? 'text-white' : 'text-red-700'}>
          Det gick inte att byta organisation.
        </span>
      )}
    </div>
  );
}
