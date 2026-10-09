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
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';

import { SessionStore } from '@core/auth/session.store';
import { messageFor } from '@core/http/messages';
import { isProblemError } from '@core/http/problem';
import { ToastService } from '@core/layout/toast.service';
import { formatDateTime } from '@features/workshops/workshop-format';
import { Button } from '@shared/ui/button';
import { ConfirmDialogService } from '@shared/ui/confirm-dialog.service';
import { EmptyState } from '@shared/ui/empty-state';
import { StatusBadge } from '@shared/ui/status-badge';

import { Registration, RegistrationStatus } from './registration.models';
import { RegistrationsService } from './registrations.service';

export const ALREADY_CANCELLED_MESSAGE = 'A colleague already cancelled this booking.';

type HistoryFilter = RegistrationStatus | null;

const FILTERS: readonly { value: HistoryFilter; label: string }[] = [
  { value: null, label: 'All' },
  { value: 'ACTIVE', label: 'Registered' },
  { value: 'WAITLISTED', label: 'Waitlisted' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

const CHIP = 'rounded-button border px-3 py-1.5 text-sm font-medium transition-colors duration-150';
const CHIP_ON = `${CHIP} border-accent bg-accent-soft text-ink`;
const CHIP_OFF = `${CHIP} border-line bg-surface text-ink hover:bg-surface-muted`;

/**
 * Everything that happened on one workshop's bookings, newest first, with who and when. The page
 * changes `refreshKey` whenever the data may be out of date; this component reloads when it does.
 */
@Component({
  selector: 'sw-registration-history',
  imports: [Button, EmptyState, StatusBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <section aria-labelledby="sw-history-h">
      <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 id="sw-history-h" class="text-xl">Bookings</h2>
        <div role="group" aria-label="Show bookings" class="flex flex-wrap gap-2">
          @for (option of filters; track option.label) {
            <button
              type="button"
              [class]="filter() === option.value ? chipOn : chipOff"
              [attr.aria-pressed]="filter() === option.value"
              (click)="filter.set(option.value)"
            >
              {{ option.label }}
            </button>
          }
        </div>
      </div>

      @if (rows() === null && !failed()) {
        <div
          class="overflow-hidden rounded-card border border-line bg-surface"
          role="status"
          aria-label="Loading bookings"
        >
          @for (row of skeletonRows; track row) {
            <div class="flex gap-4 border-b border-line p-4 last:border-b-0">
              <span class="h-4 w-40 rounded-button bg-surface-muted motion-safe:animate-pulse"></span>
              <span class="h-4 w-20 rounded-button bg-surface-muted motion-safe:animate-pulse"></span>
              <span class="h-4 flex-1 rounded-button bg-surface-muted motion-safe:animate-pulse"></span>
            </div>
          }
        </div>
      } @else if (failed()) {
        <sw-empty-state
          heading="We couldn't load the bookings"
          description="Check your connection, then try again."
        >
          <sw-button (click)="reload()">Try again</sw-button>
        </sw-empty-state>
      } @else if (rows(); as list) {
        @if (list.length === 0) {
          <sw-empty-state [heading]="emptyHeading()" />
        } @else {
          <div class="overflow-x-auto rounded-card border border-line bg-surface">
            <table class="w-full min-w-[40rem] text-left">
              <caption class="sr-only">
                Bookings for this workshop, newest first
              </caption>
              <thead class="bg-surface-muted text-sm text-ink-muted">
                <tr>
                  <th scope="col" class="px-4 py-3 font-medium">Attendee</th>
                  <th scope="col" class="px-4 py-3 font-medium">Status</th>
                  <th scope="col" class="px-4 py-3 font-medium">Details</th>
                  @if (canCancel()) {
                    <th scope="col" class="px-4 py-3 font-medium"><span class="sr-only">Actions</span></th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (r of list; track r.id) {
                  <tr class="border-t border-line align-top">
                    <td class="px-4 py-3">
                      <div class="font-medium">{{ r.attendeeName }}</div>
                      <div class="text-sm text-ink-muted">{{ r.attendeeEmail }}</div>
                    </td>
                    <td class="px-4 py-3">
                      <div class="flex flex-col items-start gap-1.5">
                        <sw-status-badge [status]="r.status" />
                        @if (r.status === 'WAITLISTED' && r.waitlistPosition !== null) {
                          <span class="text-sm text-ink-muted tabular-nums">
                            Waitlist position {{ r.waitlistPosition }}
                          </span>
                        }
                        @if (r.status === 'ACTIVE' && r.promotedAt) {
                          <span
                            class="rounded-button border border-accent/50 bg-accent-soft px-2 py-0.5 text-xs font-medium"
                          >
                            Promoted from waitlist — call to confirm
                          </span>
                        }
                      </div>
                    </td>
                    <td class="px-4 py-3 text-sm">
                      <div>Registered by {{ r.registeredBy.fullName }} · {{ when(r.registeredAt) }}</div>
                      @if (r.status === 'CANCELLED' && r.cancelledAt) {
                        <div class="mt-1 text-ink-muted">
                          Cancelled by {{ r.cancelledBy?.fullName ?? 'someone' }} ·
                          {{ when(r.cancelledAt) }}@if (r.cancellationReason) {
                            — {{ r.cancellationReason }}
                          }
                        </div>
                      }
                    </td>
                    @if (canCancel()) {
                      <td class="px-4 py-3 text-right">
                        @if (r.status !== 'CANCELLED') {
                          <sw-button
                            size="sm"
                            [loading]="busyId() === r.id"
                            [ariaLabel]="'Cancel booking for ' + r.attendeeName"
                            (click)="cancel(r)"
                          >
                            Cancel
                          </sw-button>
                        }
                      </td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }
    </section>
  `,
})
export class RegistrationHistory {
  readonly workshopId = input.required<string>();
  /** Change this to make the table reload. */
  readonly refreshKey = input<unknown>(0);
  /** Fires after a cancel attempt (done or refused), so the page can refresh seats. */
  readonly changed = output<void>();

  private readonly service = inject(RegistrationsService);
  private readonly session = inject(SessionStore);
  private readonly toasts = inject(ToastService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly filters = FILTERS;
  protected readonly chipOn = CHIP_ON;
  protected readonly chipOff = CHIP_OFF;
  protected readonly skeletonRows = [1, 2, 3];

  protected readonly filter = signal<HistoryFilter>(null);
  protected readonly rows = signal<readonly Registration[] | null>(null);
  protected readonly failed = signal(false);
  protected readonly busyId = signal<string | null>(null);

  protected readonly canCancel = computed(() => {
    const role = this.session.role();
    return role === 'MANAGER' || role === 'STAFF';
  });
  protected readonly emptyHeading = computed(() => {
    switch (this.filter()) {
      case 'ACTIVE':
        return 'No one is registered yet.';
      case 'WAITLISTED':
        return 'The waitlist is empty.';
      case 'CANCELLED':
        return 'No cancelled bookings.';
      case null:
        return 'No bookings yet.';
    }
  });

  private readonly retry = signal(0);

  constructor() {
    toObservable(
      computed(() => ({
        id: this.workshopId(),
        filter: this.filter(),
        key: this.refreshKey(),
        retry: this.retry(),
      })),
    )
      .pipe(
        switchMap(({ id, filter }) =>
          this.service.history(id, filter).pipe(
            map((list) => ({ list, ok: true })),
            catchError(() => of({ list: null, ok: false })),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ list, ok }) => {
        this.failed.set(!ok);
        if (ok) {
          this.rows.set(list);
        }
      });
  }

  protected when(iso: string): string {
    return formatDateTime(iso);
  }

  protected reload(): void {
    this.failed.set(false);
    this.retry.update((n) => n + 1);
  }

  protected async cancel(registration: Registration): Promise<void> {
    if (this.busyId() !== null) {
      return;
    }
    const waitlisted = registration.status === 'WAITLISTED';
    const answer = await this.confirmDialog.confirm({
      title: `Cancel ${registration.attendeeName}'s booking?`,
      message: waitlisted
        ? 'They will leave the waitlist. The record is kept.'
        : 'The seat will be freed. The record is kept.',
      confirmLabel: 'Cancel booking',
      cancelLabel: 'Keep booking',
      danger: true,
      askReason: true,
      reasonLabel: 'Reason (optional)',
    });
    if (!answer.confirmed) {
      return;
    }

    this.busyId.set(registration.id);
    this.service
      .cancel(registration.id, answer.reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.busyId.set(null);
          this.toasts.success(`${result.cancelled.attendeeName}'s booking is cancelled.`);
          if (result.promoted) {
            this.toasts.show(
              `${result.promoted.attendeeName} moved up from the waitlist — please call them to confirm.`,
              'info',
              12000,
            );
          }
          this.changed.emit();
        },
        error: (error: unknown) => {
          this.busyId.set(null);
          if (isProblemError(error) && error.code === 'ALREADY_CANCELLED') {
            this.toasts.show(ALREADY_CANCELLED_MESSAGE, 'info');
            this.changed.emit();
          } else if (isProblemError(error)) {
            this.toasts.error(messageFor(error));
          }
        },
      });
  }
}
