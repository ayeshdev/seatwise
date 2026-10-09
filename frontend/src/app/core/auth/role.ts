export type Role = 'ADMIN' | 'MANAGER' | 'STAFF';

export const ROLES: readonly Role[] = ['ADMIN', 'MANAGER', 'STAFF'];

export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value);
}

/** How a role reads in the UI (never the raw enum). */
export function roleLabel(role: Role): string {
  switch (role) {
    case 'ADMIN':
      return 'Admin';
    case 'MANAGER':
      return 'Manager';
    case 'STAFF':
      return 'Staff';
  }
}

/** Where each role starts, and where it is sent when it opens a page it cannot use. */
export function landingPathFor(role: Role): string {
  return role === 'ADMIN' ? '/staff-accounts' : '/workshops';
}

export interface NavItem {
  label: string;
  path: string;
}

export function navItemsFor(role: Role): readonly NavItem[] {
  if (role === 'ADMIN') {
    return [
      { label: 'Staff accounts', path: '/staff-accounts' },
      { label: 'Account activity', path: '/account-activity' },
    ];
  }
  return [
    { label: 'Workshops', path: '/workshops' },
    { label: 'Activity', path: '/activity' },
  ];
}
