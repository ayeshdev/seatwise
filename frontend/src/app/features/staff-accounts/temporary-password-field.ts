import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { ToastService } from '@core/layout/toast.service';
import { Button } from '@shared/ui/button';
import { FieldControl, FormField } from '@shared/ui/form-field';

import { generateTemporaryPassword } from './staff-accounts.model';

/** Temporary password input with Generate and Copy. The password is shown so it can be shared in person. */
@Component({
  selector: 'sw-temporary-password-field',
  imports: [ReactiveFormsModule, FormField, FieldControl, Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <sw-form-field [label]="label()" [hint]="hint()" [error]="error()">
      <div class="flex flex-col gap-2 sm:flex-row">
        <input
          swFieldControl
          type="text"
          class="block w-full border-line bg-surface font-mono text-ink"
          autocomplete="off"
          spellcheck="false"
          autocapitalize="off"
          [formControl]="control()"
        />
        <div class="flex gap-2">
          <sw-button (click)="generate()">Generate</sw-button>
          <sw-button [disabled]="!control().value" (click)="copy()">Copy</sw-button>
        </div>
      </div>
    </sw-form-field>
  `,
})
export class TemporaryPasswordField {
  readonly control = input.required<FormControl<string>>();
  readonly error = input<string | null>(null);
  readonly label = input('Temporary password');
  readonly hint = input(
    "They'll be asked to choose their own password the first time they sign in.",
  );
  /** An email the generated password must not contain (the server refuses that). */
  readonly avoid = input('');

  private readonly toasts = inject(ToastService);

  protected generate(): void {
    const avoid = this.avoid().trim().toLowerCase();
    let password = generateTemporaryPassword();
    for (
      let attempt = 0;
      attempt < 5 && avoid !== '' && password.toLowerCase().includes(avoid);
      attempt++
    ) {
      password = generateTemporaryPassword();
    }
    const control = this.control();
    control.setValue(password);
    control.markAsDirty();
    control.markAsTouched();
  }

  protected async copy(): Promise<void> {
    const value = this.control().value;
    try {
      await navigator.clipboard.writeText(value);
      this.toasts.success('Password copied.');
    } catch {
      this.toasts.error("Couldn't copy automatically. Select the password and copy it by hand.");
    }
  }
}
