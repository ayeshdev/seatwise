import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { Role } from '@core/auth/role';
import { messageFor } from '@core/http/messages';
import { ToastService } from '@core/layout/toast.service';
import { Button } from '@shared/ui/button';
import { FieldControl, FormField } from '@shared/ui/form-field';

import { RolePicker } from './role-picker';
import {
  asProblem,
  notBlank,
  passwordErrorMessage,
  temporaryPasswordValidator,
} from './staff-accounts.model';
import { StaffAccountsService } from './staff-accounts.service';
import { TemporaryPasswordField } from './temporary-password-field';

type FieldName = 'fullName' | 'email' | 'role' | 'temporaryPassword';

const GENERIC_ERROR = 'Something went wrong. Please try again, or reload the page.';

@Component({
  selector: 'sw-staff-account-create-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Button,
    FormField,
    FieldControl,
    RolePicker,
    TemporaryPasswordField,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="mb-2 text-sm">
      <a routerLink="/staff-accounts" class="text-ink-muted hover:underline"
        >&larr; Staff accounts</a
      >
    </p>
    <h1 class="mb-6 text-3xl">Add staff member</h1>

    <form
      class="flex max-w-xl flex-col gap-6 rounded-card border border-line bg-surface p-6"
      [formGroup]="form"
      (ngSubmit)="submit()"
      novalidate
    >
      @if (formError(); as message) {
        <div role="alert" class="rounded-card border border-full/50 bg-full/10 p-4 text-sm">
          {{ message }}
        </div>
      }

      <sw-form-field label="Full name" [error]="errorFor('fullName')">
        <input
          swFieldControl
          type="text"
          class="block w-full border-line bg-surface text-ink"
          autocomplete="off"
          formControlName="fullName"
        />
      </sw-form-field>

      <sw-form-field label="Email" [error]="errorFor('email')">
        <input
          swFieldControl
          type="email"
          class="block w-full border-line bg-surface text-ink"
          autocomplete="off"
          formControlName="email"
        />
      </sw-form-field>

      <sw-role-picker [control]="form.controls.role" [error]="errorFor('role')" />

      <sw-temporary-password-field
        [control]="form.controls.temporaryPassword"
        [error]="errorFor('temporaryPassword')"
        [avoid]="form.controls.email.value"
      />

      <div class="flex justify-end gap-2">
        <sw-button (click)="cancel()">Cancel</sw-button>
        <sw-button type="submit" variant="primary" [loading]="saving()">Create account</sw-button>
      </div>
    </form>
  `,
})
export class StaffAccountCreatePage {
  private readonly service = inject(StaffAccountsService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  private readonly emailControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.email, Validators.maxLength(254)],
  });

  protected readonly form = new FormGroup({
    fullName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, notBlank, Validators.maxLength(120)],
    }),
    email: this.emailControl,
    // Start from the least access; the admin picks up from there.
    role: new FormControl<Role>('STAFF', { nonNullable: true, validators: [Validators.required] }),
    temporaryPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, temporaryPasswordValidator(() => this.emailControl.value)],
    }),
  });

  constructor() {
    // The "must not contain the email" rule depends on the email field.
    this.form.controls.email.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.form.controls.temporaryPassword.updateValueAndValidity());
  }

  protected errorFor(name: FieldName): string | null {
    const control: AbstractControl = this.form.controls[name];
    if (!control.invalid || !(control.touched || control.dirty)) {
      return null;
    }
    const errors = control.errors;
    if (typeof errors?.['server'] === 'string') {
      return errors['server'];
    }
    switch (name) {
      case 'fullName':
        return errors?.['maxlength']
          ? 'Use 120 characters or fewer.'
          : "Enter the person's full name.";
      case 'email':
        if (errors?.['required']) {
          return 'Enter their email address.';
        }
        return errors?.['maxlength']
          ? 'That email address is too long.'
          : "That doesn't look like an email address. Check it for typos.";
      case 'role':
        return 'Choose a role.';
      case 'temporaryPassword':
        return passwordErrorMessage(errors);
    }
  }

  protected cancel(): void {
    void this.router.navigate(['/staff-accounts']);
  }

  protected async submit(): Promise<void> {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    try {
      const created = await firstValueFrom(
        this.service.create({
          email: value.email.trim(),
          fullName: value.fullName.trim(),
          role: value.role,
          temporaryPassword: value.temporaryPassword,
        }),
      );
      this.toasts.success(
        `Account created for ${created.fullName}. Share the temporary password with them in person.`,
      );
      await this.router.navigate(['/staff-accounts', created.id]);
    } catch (error) {
      this.showFailure(error);
    } finally {
      this.saving.set(false);
    }
  }

  private showFailure(error: unknown): void {
    const problem = asProblem(error);
    if (problem === null) {
      this.formError.set(GENERIC_ERROR);
      return;
    }
    if (problem.code === 'EMAIL_IN_USE') {
      this.setServerError('email', messageFor(problem));
      return;
    }
    if (problem.code === 'VALIDATION_FAILED') {
      let unplaced = problem.fieldErrors.length === 0;
      for (const fieldError of problem.fieldErrors) {
        if (!this.setServerError(fieldError.field, fieldError.message)) {
          unplaced = true;
        }
      }
      if (unplaced) {
        this.formError.set(messageFor(problem));
      }
      return;
    }
    this.formError.set(messageFor(problem));
  }

  /** Puts a server message on its field; returns false when the field isn't on this form. */
  private setServerError(field: string, message: string): boolean {
    const controls: Record<string, AbstractControl> = this.form.controls;
    const control = controls[field];
    if (control === undefined) {
      return false;
    }
    // Typing in the field re-runs its validators, which clears this error.
    control.setErrors({ server: message });
    control.markAsTouched();
    return true;
  }
}
