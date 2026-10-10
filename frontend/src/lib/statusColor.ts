import type { CSSProperties } from 'react';

/**
 * Badge colours for a status colour chosen by an administrator. The text is
 * the colour mixed with black so it stays readable (WCAG 1.4.3, 4.5:1) on
 * the light tint, whatever colour was picked.
 */
export function statusBadgeStyle(color?: string | null): CSSProperties {
  const c = color || '#1d4ed8';
  return {
    backgroundColor: `color-mix(in srgb, ${c} 12%, white)`,
    color: `color-mix(in srgb, ${c} 55%, black)`,
  };
}
