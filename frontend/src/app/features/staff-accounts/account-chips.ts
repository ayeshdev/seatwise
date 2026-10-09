import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Role } from '@core/auth/role';

import { roleName } from './staff-accounts.model';

/** Role as a quiet chip: "Admin", "Programme manager", "Front desk". */
@Component({
  selector: 'sw-role-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex' },
  template: `
    <span
      class="inline-flex items-center rounded-button border border-line bg-surface-muted px-2 py-0.5 text-xs font-medium text-ink"
    >
      {{ label() }}
    </span>
  `,
})
export class RoleChip {
  readonly role = input.required<Role>();
  protected readonly label = computed(() => roleName(this.role()));
}

/** "Active" / "Deactivated": text plus colour, never colour alone. */
@Component({
  selector: 'sw-account-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex' },
  template: `
    <span
      class="inline-flex items-center gap-1.5 rounded-button border px-2 py-0.5 text-xs font-medium text-ink"
      [class]="active() ? 'border-ok/50 bg-ok/10' : 'border-quiet/50 bg-quiet/10'"
    >
      <span
        class="h-1.5 w-1.5 rounded-full"
        [class]="active() ? 'bg-ok' : 'bg-quiet'"
        aria-hidden="true"
      ></span>
      {{ active() ? 'Active' : 'Deactivated' }}
    </span>
  `,
})
export class AccountStatus {
  readonly active = input.required<boolean>();
}
