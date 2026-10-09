import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { Role } from '@core/auth/role';

import { ROLE_OPTIONS } from './staff-accounts.model';

let nextPickerId = 0;

/** Radio group where every role explains itself in one line. */
@Component({
  selector: 'sw-role-picker',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <fieldset class="m-0 border-0 p-0" [attr.aria-describedby]="error() ? errorId : null">
      <legend class="mb-2 block p-0 text-sm font-medium text-ink">Role</legend>
      <div class="flex flex-col gap-2">
        @for (option of options; track option.value) {
          <label
            class="flex items-start gap-3 rounded-card border border-line bg-surface p-3 has-[:checked]:border-accent has-[:checked]:bg-accent-soft has-[:enabled]:cursor-pointer has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-60"
          >
            <input
              type="radio"
              class="mt-1 text-accent"
              [name]="groupName"
              [value]="option.value"
              [formControl]="control()"
            />
            <span>
              <span class="block font-medium text-ink">{{ option.label }}</span>
              <span class="block text-sm text-ink-muted">{{ option.description }}</span>
            </span>
          </label>
        }
      </div>
      @if (error()) {
        <p class="mt-1 text-sm text-full" [id]="errorId">{{ error() }}</p>
      }
    </fieldset>
  `,
})
export class RolePicker {
  readonly control = input.required<FormControl<Role>>();
  readonly error = input<string | null>(null);

  protected readonly options = ROLE_OPTIONS;
  private readonly uid = `sw-role-picker-${nextPickerId++}`;
  protected readonly groupName = this.uid;
  protected readonly errorId = `${this.uid}-error`;
}
