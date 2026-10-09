import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { SessionStore } from '@core/auth/session.store';
import { messageFor } from '@core/http/messages';
import { isProblemError } from '@core/http/problem';
import { ToastService } from '@core/layout/toast.service';
import { RegisterPanel } from '@features/registrations/register-panel';
import { RegistrationHistory } from '@features/registrations/registration-history';
import { Button } from '@shared/ui/button';
import { ConfirmDialogService } from '@shared/ui/confirm-dialog.service';
import { EmptyState } from '@shared/ui/empty-state';
import { SeatMeter } from '@shared/ui/seat-meter';
import { StatusBadge } from '@shared/ui/status-badge';

import { formatWhen } from './workshop-format';
import { Workshop } from './workshop.models';
import { WorkshopsService } from './workshops.service';

/** How often seat counts are re-read while the page is on screen. */
export const POLL_INTERVAL_MS = 15_000;

type LoadState = 'loading' | 'ready' | 'notFound' | 'error';

@Component({
  selector: 'sw-workshop-detail-page',
  imports: [RouterLink, Button, EmptyState, SeatMeter, StatusBadge, RegisterPanel, RegistrationHistory],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/workshops" class="mb-4 inline-block text-sm text-ink-muted hover:text-ink">
      ← All workshops
    </a>

    @switch (loadState()) {
      @case ('loading') {
        <div role="status" aria-label="Loading workshop" class="flex flex-col gap-4">
          <span class="h-8 w-2/3 rounded-button bg-surface-muted motion-safe:animate-pulse"></span>
          <span class="h-32 rounded-card bg-surface-muted motion-safe:animate-pulse"></span>
        </div>
      }
      @case ('notFound') {
        <sw-empty-state
          heading="We couldn't find that workshop"
          description="It may have been removed. Go back to the list and pick another."
        />
      }
      @case ('error') {
        <sw-empty-state
          heading="We couldn't load this workshop"
          description="Check your connection, then try again."
        >
          <sw-button (click)="retry()">Try again</sw-button>
        </sw-empty-state>
      }
      @default {
        @if (workshop(); as w) {
          <header class="mb-6 flex flex-wrap items-start justify-between gap-4">
            <div>
              <p class="text-sm text-ink-muted tabular-nums">{{ w.code }}</p>
              <h1 class="mt-1 text-3xl">{{ w.title }}</h1>
              <div class="mt-3"><sw-status-badge [status]="w.status" /></div>
            </div>
            @if (isManager() && canChange()) {
              <div class="flex flex-wrap gap-2">
                <sw-button (click)="edit()">Edit</sw-button>
                <sw-button variant="danger" (click)="cancelWorkshop()">Cancel workshop</sw-button>
              </div>
            }
          </header>

          @if (w.status === 'CANCELLED') {
            <p class="mb-6 rounded-card border border-line bg-surface-muted p-4">
              This workshop is cancelled. Existing registrations are kept; no new bookings are accepted.
            </p>
          }

          <div class="grid gap-6 lg:grid-cols-3">
            <div class="flex flex-col gap-6 lg:col-span-2">
              <section
                class="rounded-card border border-line bg-surface p-5"
                aria-labelledby="sw-facts-h"
              >
                <h2 id="sw-facts-h" class="mb-4 text-xl">Details</h2>
                <dl class="grid gap-x-6 gap-y-4 sm:grid-cols-2">
                  <div>
                    <dt class="text-sm text-ink-muted">When</dt>
                    <dd class="font-medium tabular-nums">{{ when(w) }}</dd>
                  </div>
                  <div>
                    <dt class="text-sm text-ink-muted">Where</dt>
                    <dd class="font-medium">{{ w.location.name }}</dd>
                  </div>
                  <div>
                    <dt class="text-sm text-ink-muted">Instructor</dt>
                    <dd class="font-medium">{{ w.instructor }}</dd>
                  </div>
                  @if (w.description) {
                    <div class="sm:col-span-2">
                      <dt class="text-sm text-ink-muted">About</dt>
                      <dd class="whitespace-pre-line">{{ w.description }}</dd>
                    </div>
                  }
                </dl>
              </section>

              <sw-registration-history
                [workshopId]="w.id"
                [refreshKey]="historyBump()"
                (changed)="refresh(true)"
              />
            </div>

            <div class="flex flex-col gap-6">
              <section
                class="rounded-card border border-line bg-surface p-5"
                aria-labelledby="sw-seats-h"
              >
                <h2 id="sw-seats-h" class="mb-3 text-xl">Seats</h2>
                <sw-seat-meter [seatsLeft]="w.seatsLeft" [capacity]="w.capacity" />
                <p class="mt-3 text-sm text-ink-muted tabular-nums">{{ waitlistText(w) }}</p>
              </section>

              <sw-register-panel [workshop]="w" (changed)="refresh(true)" />
            </div>
          </div>
        }
      }
    }
  `,
})
export class WorkshopDetailPage {
  private readonly service = inject(WorkshopsService);
  private readonly session = inject(SessionStore);
  private readonly toasts = inject(ToastService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly doc = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly id = toSignal(this.route.paramMap.pipe(map((p) => p.get('id') ?? '')), {
    initialValue: this.route.snapshot.paramMap.get('id') ?? '',
  });
  protected readonly workshop = signal<Workshop | null>(null);
  protected readonly loadState = signal<LoadState>('loading');
  /** Bumped whenever the bookings table should reload. */
  protected readonly historyBump = signal(0);

  protected readonly isManager = computed(() => this.session.role() === 'MANAGER');
  protected readonly canChange = computed(() => {
    const status = this.workshop()?.status;
    return status !== 'CANCELLED' && status !== 'COMPLETED';
  });

  private timer: ReturnType<typeof setInterval> | null = null;
  private requestSeq = 0;
  private historyRefreshPending = false;

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => this.load(id));
    });

    // Re-read seat counts every 15 s, but only while someone can actually see the page.
    const onVisibility = (): void => {
      if (this.doc.visibilityState === 'visible') {
        this.refresh(false);
        this.startPolling();
      } else {
        this.stopPolling();
      }
    };
    this.doc.addEventListener('visibilitychange', onVisibility);
    if (this.doc.visibilityState !== 'hidden') {
      this.startPolling();
    }
    this.destroyRef.onDestroy(() => {
      this.stopPolling();
      this.doc.removeEventListener('visibilitychange', onVisibility);
    });
  }

  protected when(w: Workshop): string {
    return formatWhen(w.startsAt, w.endsAt);
  }

  protected waitlistText(w: Workshop): string {
    switch (w.waitlistCount) {
      case 0:
        return 'No one is on the waitlist.';
      case 1:
        return '1 person on the waitlist.';
      default:
        return `${w.waitlistCount} people on the waitlist.`;
    }
  }

  protected retry(): void {
    this.load(this.id());
  }

  protected edit(): void {
    void this.router.navigate(['/workshops', this.id(), 'edit']);
  }

  /** Re-reads the workshop. `reloadBookings` also reloads the table (it reloads anyway if the counts moved). */
  protected refresh(reloadBookings: boolean): void {
    if (reloadBookings) {
      this.historyRefreshPending = true;
    }
    const seq = ++this.requestSeq;
    this.service
      .get(this.id())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (fresh) => {
          if (seq !== this.requestSeq) {
            return; // a newer answer is on its way or already here
          }
          const before = this.workshop();
          this.workshop.set(fresh);
          this.loadState.set('ready');
          const countsMoved =
            before !== null &&
            (before.seatsTaken !== fresh.seatsTaken || before.waitlistCount !== fresh.waitlistCount);
          if (this.historyRefreshPending || countsMoved) {
            this.historyRefreshPending = false;
            this.historyBump.update((n) => n + 1);
          }
        },
        // A missed refresh is harmless: the next one (or the global offline toast) covers it.
        error: () => undefined,
      });
  }

  protected async cancelWorkshop(): Promise<void> {
    const w = this.workshop();
    if (w === null) {
      return;
    }
    const answer = await this.confirmDialog.confirm({
      title: `Cancel ${w.title}?`,
      message: 'Existing registrations are kept; no new bookings will be accepted.',
      confirmLabel: 'Cancel workshop',
      cancelLabel: 'Keep workshop',
      danger: true,
      askReason: true,
      reasonLabel: 'Reason (optional)',
    });
    if (!answer.confirmed) {
      return;
    }
    this.service
      .cancel(w.id, answer.reason)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.toasts.success('The workshop is cancelled.');
          this.refresh(true);
        },
        error: (error: unknown) => {
          if (isProblemError(error)) {
            this.toasts.error(messageFor(error));
            this.refresh(false);
          }
        },
      });
  }

  private load(id: string): void {
    this.loadState.set('loading');
    this.workshop.set(null);
    const seq = ++this.requestSeq;
    this.service
      .get(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (fresh) => {
          if (seq !== this.requestSeq) {
            return;
          }
          this.workshop.set(fresh);
          this.loadState.set('ready');
        },
        error: (error: unknown) => {
          if (seq !== this.requestSeq) {
            return;
          }
          this.loadState.set(isProblemError(error) && error.status === 404 ? 'notFound' : 'error');
        },
      });
  }

  private startPolling(): void {
    this.timer ??= setInterval(() => this.refresh(false), POLL_INTERVAL_MS);
  }

  private stopPolling(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}
