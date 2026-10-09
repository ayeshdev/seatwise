import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom, map } from 'rxjs';

import { Role } from '@core/auth/role';
import { SessionStore } from '@core/auth/session.store';
import { messageFor } from '@core/http/messages';
import { ToastService } from '@core/layout/toast.service';
import { Button } from '@shared/ui/button';
import { ConfirmDialogService } from '@shared/ui/confirm-dialog.service';
import { FieldControl, FormField } from '@shared/ui/form-field';
import { Spinner } from '@shared/ui/spinner';

import { AccountStatus, RoleChip } from './account-chips';
import { ResetPasswordDialog } from './reset-password-dialog';
import { RolePicker } from './role-picker';
import {
  StaffAccount,
  UpdateStaffAccount,
  asProblem,
  firstName,
  formatDate,
  notBlank,
} from './staff-accounts.model';
import { StaffAccountsService } from './staff-accounts.service';

const GENERIC_ERROR = 'Something went wrong. Please try again, or reload the page.';
export const SELF_NOTE = "You can't change your own role or deactivate yourself.";

@Component({
  selector: 'sw-staff-account-detail-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Button,
    FormField,
    FieldControl,
    RolePicker,
    Spinner,
    RoleChip,
    AccountStatus,
    ResetPasswordDialog,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="mb-2 text-sm">
      <a routerLink="/staff-accounts" class="text-ink-muted hover:underline"
        >&larr; Staff accounts</a
      >
    </p>

    @if (account(); as acc) {
      <div class="mb-6 flex flex-wrap items-center gap-3">
        <h1 class="text-3xl">{{ acc.fullName }}</h1>
        <sw-role-chip [role]="acc.role" />
        <sw-account-status [active]="acc.active" />
      </div>

      @if (stale()) {
        <div role="alert" class="mb-4 rounded-card border border-warn/60 bg-warn/10 p-4 text-sm">
          <p>
            Someone else changed this account while you were editing. Reload to see their changes.
          </p>
          <div class="mt-3"><sw-button size="sm" (click)="reload()">Reload</sw-button></div>
        </div>
      }
      @if (formError(); as message) {
        <div role="alert" class="mb-4 rounded-card border border-full/50 bg-full/10 p-4 text-sm">
          {{ message }}
        </div>
      }

      <div class="grid max-w-3xl gap-6">
        <dl
          class="grid gap-x-6 gap-y-3 rounded-card border border-line bg-surface p-6 text-sm sm:grid-cols-3"
        >
          <div>
            <dt class="text-ink-muted">Email</dt>
            <dd class="break-all font-medium">{{ acc.email }}</dd>
          </div>
          <div>
            <dt class="text-ink-muted">Added</dt>
            <dd class="font-medium">{{ date(acc.createdAt) }}</dd>
          </div>
          <div>
            <dt class="text-ink-muted">Last changed</dt>
            <dd class="font-medium">{{ date(acc.updatedAt) }}</dd>
          </div>
        </dl>

        <form
          class="flex flex-col gap-6 rounded-card border border-line bg-surface p-6"
          [formGroup]="form"
          (ngSubmit)="save()"
          novalidate
        >
          <h2 class="text-xl">Edit details</h2>

          <sw-form-field label="Full name" [error]="nameError()">
            <input
              swFieldControl
              type="text"
              class="block w-full border-line bg-surface text-ink"
              autocomplete="off"
              formControlName="fullName"
            />
          </sw-form-field>

          <sw-role-picker [control]="form.controls.role" />

          @if (isSelf()) {
            <p class="text-sm text-ink-muted">{{ selfNote }}</p>
          }

          <div class="flex justify-end">
            <sw-button
              type="submit"
              variant="primary"
              [loading]="saving()"
              [disabled]="!hasChanges()"
            >
              Save changes
            </sw-button>
          </div>
        </form>

        <section class="flex flex-col gap-4 rounded-card border border-line bg-surface p-6">
          <h2 class="text-xl">Access</h2>
          <div class="flex flex-wrap gap-3">
            <sw-button (click)="openReset()">Set a new temporary password</sw-button>
            @if (acc.active) {
              <sw-button
                variant="danger"
                [disabled]="isSelf()"
                [loading]="toggling()"
                (click)="deactivate()"
              >
                Deactivate account
              </sw-button>
            } @else {
              <sw-button [loading]="toggling()" (click)="reactivate()"
                >Reactivate account</sw-button
              >
            }
          </div>
          @if (isSelf()) {
            <p class="text-sm text-ink-muted">{{ selfNote }}</p>
          }
        </section>
      </div>

      @if (resetting()) {
        <sw-reset-password-dialog
          [account]="acc"
          (closed)="resetting.set(false)"
          (saved)="resetSaved(acc)"
        />
      }
    } @else if (loadError(); as message) {
      <div role="alert" class="max-w-xl rounded-card border border-full/50 bg-full/10 p-4 text-sm">
        <p>{{ message }}</p>
        <div class="mt-3"><sw-button size="sm" (click)="reload()">Try again</sw-button></div>
      </div>
    } @else {
      <div class="flex justify-center py-12"><sw-spinner label="Loading account" /></div>
    }
  `,
})
export class StaffAccountDetailPage {
  private readonly service = inject(StaffAccountsService);
  private readonly session = inject(SessionStore);
  private readonly route = inject(ActivatedRoute);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly toasts = inject(ToastService);

  protected readonly selfNote = SELF_NOTE;
  protected readonly account = signal<StaffAccount | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly formError = signal<string | null>(null);
  protected readonly stale = signal(false);
  protected readonly saving = signal(false);
  protected readonly toggling = signal(false);
  protected readonly resetting = signal(false);

  protected readonly form = new FormGroup({
    fullName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, notBlank, Validators.maxLength(120)],
    }),
    role: new FormControl<Role>('STAFF', { nonNullable: true }),
  });

  private readonly formValue = toSignal(this.form.valueChanges);
  private readonly id = toSignal(this.route.paramMap.pipe(map((p) => p.get('id'))), {
    initialValue: this.route.snapshot.paramMap.get('id'),
  });

  protected readonly isSelf = computed(() => {
    const account = this.account();
    return account !== null && this.session.me()?.id === account.id;
  });

  /** True when the form differs from what is saved. */
  protected readonly hasChanges = computed(() => {
    this.formValue(); // re-evaluate on every edit
    const account = this.account();
    return account !== null && Object.keys(this.changesFor(account)).length > 0;
  });

  private requestId = 0;

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => void this.load(id));
    });
  }

  protected date(iso: string): string {
    return formatDate(iso);
  }

  protected nameError(): string | null {
    const control = this.form.controls.fullName;
    if (!control.invalid || !(control.touched || control.dirty)) {
      return null;
    }
    const errors = control.errors;
    if (typeof errors?.['server'] === 'string') {
      return errors['server'];
    }
    return errors?.['maxlength'] ? 'Use 120 characters or fewer.' : "Enter the person's full name.";
  }

  protected reload(): void {
    void this.load(this.id());
  }

  protected openReset(): void {
    this.resetting.set(true);
  }

  protected resetSaved(account: StaffAccount): void {
    this.resetting.set(false);
    this.toasts.success(
      `New temporary password set for ${firstName(account.fullName)}. Share it with them in person.`,
    );
  }

  protected async save(): Promise<void> {
    const account = this.account();
    if (account === null) {
      return;
    }
    this.clearMessages();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const changes = this.changesFor(account);
    if (Object.keys(changes).length === 0) {
      return;
    }
    this.saving.set(true);
    try {
      const updated = await firstValueFrom(
        this.service.update(account.id, { ...changes, version: account.version }),
      );
      this.applyAccount(updated);
      this.toasts.success('Changes saved.');
    } catch (error) {
      this.showFailure(error);
    } finally {
      this.saving.set(false);
    }
  }

  protected async deactivate(): Promise<void> {
    const account = this.account();
    if (account === null || this.isSelf()) {
      return;
    }
    const answer = await this.confirmDialog.confirm({
      title: 'Deactivate this account?',
      message: `${firstName(account.fullName)} will no longer be able to sign in. Their booking history is kept.`,
      confirmLabel: 'Deactivate account',
      danger: true,
    });
    if (!answer.confirmed) {
      return;
    }
    await this.setActive(
      account,
      false,
      `Account deactivated. ${firstName(account.fullName)} can no longer sign in.`,
    );
  }

  protected async reactivate(): Promise<void> {
    const account = this.account();
    if (account === null) {
      return;
    }
    await this.setActive(
      account,
      true,
      `Account reactivated. ${firstName(account.fullName)} can sign in again.`,
    );
  }

  private async setActive(account: StaffAccount, active: boolean, success: string): Promise<void> {
    this.clearMessages();
    this.toggling.set(true);
    try {
      const updated = await firstValueFrom(
        this.service.update(account.id, { active, version: account.version }),
      );
      // Keep any unsaved edits in the form; only the saved copy moves on.
      this.account.set(updated);
      this.toasts.success(success);
    } catch (error) {
      this.showFailure(error);
    } finally {
      this.toggling.set(false);
    }
  }

  /** Only the fields that differ from what is saved. A disabled role is never sent. */
  private changesFor(account: StaffAccount): Omit<UpdateStaffAccount, 'version'> {
    const raw = this.form.getRawValue();
    const changes: Omit<UpdateStaffAccount, 'version'> = {};
    const name = raw.fullName.trim();
    if (name !== account.fullName) {
      changes.fullName = name;
    }
    if (!this.isSelf() && raw.role !== account.role) {
      changes.role = raw.role;
    }
    return changes;
  }

  private applyAccount(account: StaffAccount): void {
    this.account.set(account);
    this.form.reset({ fullName: account.fullName, role: account.role });
    if (this.session.me()?.id === account.id) {
      this.form.controls.role.disable();
    } else {
      this.form.controls.role.enable();
    }
  }

  private clearMessages(): void {
    this.formError.set(null);
    this.stale.set(false);
  }

  private showFailure(error: unknown): void {
    const problem = asProblem(error);
    if (problem === null) {
      this.formError.set(GENERIC_ERROR);
      return;
    }
    if (problem.code === 'STALE_VERSION') {
      this.stale.set(true);
      return;
    }
    const nameMessage = problem.fieldMessage('fullName');
    if (problem.code === 'VALIDATION_FAILED' && nameMessage !== null) {
      this.form.controls.fullName.setErrors({ server: nameMessage });
      this.form.controls.fullName.markAsTouched();
      return;
    }
    this.formError.set(messageFor(problem));
  }

  private async load(id: string | null): Promise<void> {
    const requestId = ++this.requestId;
    if (id === null) {
      return;
    }
    this.loadError.set(null);
    this.clearMessages();
    try {
      const account = await firstValueFrom(this.service.get(id));
      if (requestId === this.requestId) {
        this.applyAccount(account);
      }
    } catch (error) {
      if (requestId === this.requestId) {
        const problem = asProblem(error);
        const message = problem === null ? GENERIC_ERROR : messageFor(problem);
        // With an account already on screen, keep it and explain above it.
        if (this.account() !== null) {
          this.formError.set(message);
        } else {
          this.loadError.set(message);
        }
      }
    }
  }
}
