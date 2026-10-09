import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { SessionStore } from '@core/auth/session.store';
import { GENERIC_MESSAGE, messageFor } from '@core/http/messages';
import { isProblemError } from '@core/http/problem';
import { ToastService } from '@core/layout/toast.service';
import { firstName } from '@features/workshops/workshop-format';
import type { Workshop } from '@features/workshops/workshop.models';
import { Button } from '@shared/ui/button';
import { FieldControl, FormField } from '@shared/ui/form-field';

import { Registration } from './registration.models';
import { RegistrationsService } from './registrations.service';

export const FULL_RACE_MESSAGE = 'Sorry — the last seat was just taken by a colleague.';
export const DUPLICATE_MESSAGE = 'This person is already booked (or waitlisted) on this workshop.';
export const FULL_UPFRONT_MESSAGE =
  'This workshop is full. You can add the attendee to the waitlist.';

/**
 * Two fields (name, email) and a button. Shown to Manager and Staff while the workshop can still
 * take bookings. The server decides whether there is a seat; this only explains what happened.
 */
@Component({
  selector: 'sw-register-panel',
  imports: [ReactiveFormsModule, Button, FormField, FieldControl],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    @if (visible()) {
      <section class="rounded-card border border-line bg-surface p-5" aria-labelledby="sw-register-h">
        <h2 id="sw-register-h" class="mb-1 text-xl">Register an attendee</h2>

        @if (conflict()) {
          <p class="mb-4 text-full" role="alert">{{ raceMessage }}</p>
        } @else if (isFull()) {
          <p class="mb-4 text-ink-muted">{{ fullMessage }}</p>
        } @else {
          <p class="mb-4 text-ink-muted">Enter who is booking. Their confirmation goes to this email.</p>
        }

        <form class="flex flex-col gap-4" [formGroup]="form" (ngSubmit)="onSubmit()" novalidate>
          <sw-form-field label="Attendee name" [error]="nameError()">
            <input
              swFieldControl
              type="text"
              class="block w-full border-line bg-surface text-ink"
              formControlName="attendeeName"
              autocomplete="off"
              maxlength="120"
            />
          </sw-form-field>

          <sw-form-field label="Email" [error]="emailError()">
            <input
              swFieldControl
              type="email"
              class="block w-full border-line bg-surface text-ink"
              formControlName="attendeeEmail"
              autocomplete="off"
              maxlength="200"
            />
          </sw-form-field>

          @if (formError(); as message) {
            <p class="text-full" role="alert">{{ message }}</p>
          }

          <div>
            @if (conflict()) {
              <sw-button [loading]="submitting()" (click)="addToWaitlist()">
                Add {{ attendeeFirstName() }} to the waitlist instead
              </sw-button>
            } @else if (isFull()) {
              <sw-button type="submit" variant="primary" [loading]="submitting()">
                Add to waitlist
              </sw-button>
            } @else {
              <sw-button type="submit" variant="primary" [loading]="submitting()">Register</sw-button>
            }
          </div>
        </form>
      </section>
    }
  `,
})
export class RegisterPanel {
  readonly workshop = input.required<Workshop>();
  /** Fires after anything that may have changed seats or the waitlist, so the page can refresh. */
  readonly changed = output<void>();

  private readonly service = inject(RegistrationsService);
  private readonly session = inject(SessionStore);
  private readonly toasts = inject(ToastService);

  protected readonly raceMessage = FULL_RACE_MESSAGE;
  protected readonly fullMessage = FULL_UPFRONT_MESSAGE;

  protected readonly form = new FormGroup({
    attendeeName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    attendeeEmail: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
  });

  protected readonly submitting = signal(false);
  /** The seat vanished between loading the page and pressing Register. */
  protected readonly conflict = signal(false);
  protected readonly formError = signal<string | null>(null);
  private readonly serverNameError = signal<string | null>(null);
  private readonly serverEmailError = signal<string | null>(null);

  protected readonly visible = computed(() => {
    const role = this.session.role();
    const status = this.workshop().status;
    return (role === 'MANAGER' || role === 'STAFF') && (status === 'OPEN' || status === 'FULL');
  });
  protected readonly isFull = computed(() => this.workshop().status === 'FULL');
  protected readonly attendeeFirstName = signal('');

  protected nameError(): string | null {
    const server = this.serverNameError();
    if (server !== null) {
      return server;
    }
    const control = this.form.controls.attendeeName;
    return control.touched && control.hasError('required') ? "Enter the attendee's name." : null;
  }

  protected emailError(): string | null {
    const server = this.serverEmailError();
    if (server !== null) {
      return server;
    }
    const control = this.form.controls.attendeeEmail;
    if (!control.touched) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Enter an email address.';
    }
    return control.hasError('email') ? "That email address doesn't look right." : null;
  }

  constructor() {
    // Any edit clears what the server last said about the old values.
    this.form.valueChanges.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(() => {
      this.conflict.set(false);
      this.formError.set(null);
      this.serverNameError.set(null);
      this.serverEmailError.set(null);
      this.attendeeFirstName.set(firstName(this.form.controls.attendeeName.value));
    });
  }

  protected onSubmit(): void {
    if (this.conflict()) {
      // They have to choose the waitlist button on purpose.
      return;
    }
    this.submit(this.isFull());
  }

  protected addToWaitlist(): void {
    this.submit(true);
  }

  private submit(joinWaitlist: boolean): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { attendeeName, attendeeEmail } = this.form.getRawValue();
    this.submitting.set(true);
    this.formError.set(null);

    this.service
      .register(this.workshop().id, {
        attendeeName: attendeeName.trim(),
        attendeeEmail: attendeeEmail.trim(),
        joinWaitlistIfFull: joinWaitlist,
      })
      .subscribe({
        next: (registration) => this.onRegistered(registration),
        error: (error: unknown) => this.onFailed(error),
      });
  }

  private onRegistered(registration: Registration): void {
    this.submitting.set(false);
    if (registration.status === 'WAITLISTED') {
      const position = registration.waitlistPosition;
      this.toasts.success(
        position === null
          ? `${registration.attendeeName} is on the waitlist.`
          : `${registration.attendeeName} is on the waitlist (position ${position}).`,
      );
    } else {
      this.toasts.success(`${registration.attendeeName} is registered.`);
    }
    this.form.reset();
    this.conflict.set(false);
    this.changed.emit();
  }

  private onFailed(error: unknown): void {
    this.submitting.set(false);
    if (!isProblemError(error)) {
      this.formError.set(GENERIC_MESSAGE);
      return;
    }
    switch (error.code) {
      case 'WORKSHOP_FULL':
        this.conflict.set(true);
        // The page re-reads the seat count so the meter shows the truth.
        this.changed.emit();
        break;
      case 'DUPLICATE_REGISTRATION':
        this.serverEmailError.set(DUPLICATE_MESSAGE);
        break;
      case 'VALIDATION_FAILED':
        this.serverNameError.set(error.fieldMessage('attendeeName'));
        this.serverEmailError.set(error.fieldMessage('attendeeEmail'));
        if (error.fieldErrors.length === 0) {
          this.formError.set(messageFor(error));
        }
        break;
      case 'WORKSHOP_NOT_OPEN':
        this.formError.set(messageFor(error));
        this.changed.emit();
        break;
      default:
        this.formError.set(messageFor(error));
    }
  }
}
