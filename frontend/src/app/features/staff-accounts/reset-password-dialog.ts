import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { messageFor } from '@core/http/messages';
import { Button } from '@shared/ui/button';

import {
  StaffAccount,
  asProblem,
  firstName,
  passwordErrorMessage,
  temporaryPasswordValidator,
} from './staff-accounts.model';
import { StaffAccountsService } from './staff-accounts.service';
import { TemporaryPasswordField } from './temporary-password-field';

/**
 * "Set a new temporary password" for one account. Render it with `@if`; it opens itself, and
 * tells the parent when it was dismissed (`closed`) or the password was saved (`saved`).
 */
@Component({
  selector: 'sw-reset-password-dialog',
  imports: [ReactiveFormsModule, Button, TemporaryPasswordField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="sw-dialog" aria-labelledby="sw-reset-title" (close)="onNativeClose()">
      <form class="flex flex-col gap-4 p-6" (submit)="submit($event)" novalidate>
        <h2 id="sw-reset-title" class="text-xl">Set a new temporary password</h2>
        <p class="text-ink-muted">
          This replaces {{ name() }}'s current password. Share the new one with them in person.
        </p>

        @if (formError(); as message) {
          <div role="alert" class="rounded-card border border-full/50 bg-full/10 p-3 text-sm">
            {{ message }}
          </div>
        }

        <sw-temporary-password-field
          label="New temporary password"
          hint="They'll be asked to choose their own password the next time they sign in."
          [control]="password"
          [error]="passwordError()"
          [avoid]="account().email"
        />

        <div class="mt-2 flex justify-end gap-2">
          <sw-button (click)="dismiss()">Cancel</sw-button>
          <sw-button type="submit" variant="primary" [loading]="saving()">Set password</sw-button>
        </div>
      </form>
    </dialog>
  `,
})
export class ResetPasswordDialog {
  readonly account = input.required<StaffAccount>();
  readonly closed = output<void>();
  readonly saved = output<void>();

  private readonly service = inject(StaffAccountsService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly password = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, temporaryPasswordValidator(() => this.account().email)],
  });

  private finished = false;

  constructor() {
    afterNextRender(() => {
      const el = this.dialog().nativeElement;
      // jsdom and very old engines lack showModal; the `open` attribute is the fallback.
      if (typeof el.showModal === 'function') {
        el.showModal();
      } else {
        el.setAttribute('open', '');
      }
    });
  }

  protected name(): string {
    return firstName(this.account().fullName);
  }

  protected passwordError(): string | null {
    if (!this.password.invalid || !(this.password.touched || this.password.dirty)) {
      return null;
    }
    return passwordErrorMessage(this.password.errors);
  }

  protected dismiss(): void {
    this.finish(() => this.closed.emit());
  }

  /** Escape closes the native dialog; treat that as cancel. */
  protected onNativeClose(): void {
    this.finish(() => this.closed.emit());
  }

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    this.formError.set(null);
    if (this.password.invalid) {
      this.password.markAsTouched();
      return;
    }
    this.saving.set(true);
    try {
      await firstValueFrom(this.service.resetPassword(this.account().id, this.password.value));
      this.finish(() => this.saved.emit());
    } catch (error) {
      const problem = asProblem(error);
      if (problem === null) {
        this.formError.set('Something went wrong. Please try again, or reload the page.');
      } else {
        const fieldMessage = problem.fieldMessage('temporaryPassword');
        if (problem.code === 'VALIDATION_FAILED' && fieldMessage !== null) {
          this.password.setErrors({ server: fieldMessage });
          this.password.markAsTouched();
        } else {
          this.formError.set(messageFor(problem));
        }
      }
    } finally {
      this.saving.set(false);
    }
  }

  private finish(emit: () => void): void {
    if (this.finished) {
      return;
    }
    this.finished = true;
    emit();
  }
}
