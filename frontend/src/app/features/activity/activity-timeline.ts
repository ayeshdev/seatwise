import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { formatDateTime } from '@features/workshops/workshop-format';
import { Button } from '@shared/ui/button';
import { Spinner } from '@shared/ui/spinner';

import {
  ActionTone,
  actionLabel,
  actionTone,
  fieldLabel,
  formatChangeValue,
  formatRelative,
} from './audit-format';
import { AuditEntityType, AuditEvent, AuditQuery } from './audit.models';
import { AuditService } from './audit.service';

export const DEFAULT_PAGE_SIZE = 20;
export const LOAD_ERROR_MESSAGE = "We couldn't load the activity. Check your connection, then try again.";

const DOT: Record<ActionTone, string> = {
  neutral: 'bg-quiet',
  full: 'bg-full',
  ok: 'bg-ok',
};
const LABEL: Record<ActionTone, string> = {
  neutral: 'text-ink-muted',
  full: 'text-full',
  ok: 'text-ok',
};

interface ChangeRow {
  label: string;
  from: string;
  to: string;
}

interface Scope {
  workshopId: string | null;
  entityType: AuditEntityType | null;
  entityId: string | null;
  from: string | null;
  to: string | null;
  size: number;
  refreshKey: number;
}

/**
 * A vertical, newest-first timeline of what changed and who did it. Give it one of
 * `{ workshopId }` (a workshop and its bookings), `{ entityType, entityId }` (one record) or
 * `{ entityType }` (everything of that kind), optionally narrowed by a date range. It reloads from
 * the first page whenever any input changes (bump `refreshKey` to force it).
 */
@Component({
  selector: 'sw-activity-timeline',
  imports: [RouterLink, Button, Spinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div aria-live="polite" [attr.aria-busy]="loading()">
      @if (items().length > 0) {
        <ol class="relative flex flex-col">
          @for (event of items(); track event.id; let last = $last) {
            <li class="relative flex gap-4" [class.pb-6]="!last">
              @if (!last) {
                <span class="absolute bottom-0 left-[0.3125rem] top-4 w-px bg-line" aria-hidden="true"></span>
              }
              <span
                class="relative mt-1.5 h-3 w-3 shrink-0 rounded-full ring-4 ring-surface"
                [class]="dot(event)"
                aria-hidden="true"
              ></span>
              <div class="min-w-0 flex-1">
                <p class="text-xs font-semibold uppercase tracking-wide" [class]="label(event)">
                  {{ action(event) }}
                </p>
                <p class="mt-0.5">{{ event.summary }}</p>
                <p class="mt-1 flex flex-wrap items-center gap-x-2 text-sm text-ink-muted">
                  <span>{{ actor(event) }}</span>
                  <span aria-hidden="true">·</span>
                  <time [attr.datetime]="event.occurredAt" class="tabular-nums">
                    {{ relative(event) }}, {{ absolute(event) }}
                  </time>
                  @if (showLinks() && link(event); as target) {
                    <span aria-hidden="true">·</span>
                    <a [routerLink]="target.path" class="text-accent hover:underline">{{
                      target.label
                    }}</a>
                  }
                </p>

                @if (changeRows(event); as rows) {
                  @if (rows.length > 0) {
                    <button
                      type="button"
                      class="mt-2 rounded-button text-sm font-medium text-accent hover:underline"
                      [attr.aria-expanded]="isOpen(event)"
                      [attr.aria-controls]="'sw-changes-' + event.id"
                      (click)="toggle(event)"
                    >
                      {{ isOpen(event) ? 'Hide details' : 'Details' }}
                    </button>
                    @if (isOpen(event)) {
                      <dl
                        [id]="'sw-changes-' + event.id"
                        class="mt-2 grid gap-x-4 gap-y-2 rounded-card border border-line bg-surface-muted p-3 text-sm sm:grid-cols-[max-content_1fr]"
                      >
                        @for (row of rows; track row.label) {
                          <dt class="text-ink-muted">{{ row.label }}</dt>
                          <dd class="min-w-0 break-words">
                            <span>{{ row.from }}</span>
                            <span class="mx-1.5 text-ink-muted" aria-label="changed to">→</span>
                            <span class="font-medium">{{ row.to }}</span>
                          </dd>
                        }
                      </dl>
                    }
                  }
                }
              </div>
            </li>
          }
        </ol>

        <div class="mt-4 flex flex-col items-start gap-2">
          @if (failed()) {
            <p role="alert" class="text-sm">{{ errorMessage }}</p>
            <sw-button size="sm" (click)="retry()">Try again</sw-button>
          } @else if (loading()) {
            <sw-spinner label="Loading more activity" />
          } @else if (paged() && hasMore()) {
            <sw-button (click)="loadMore()">Load more</sw-button>
          }
        </div>
      } @else if (failed()) {
        <div
          role="alert"
          class="flex flex-col items-start gap-3 rounded-card border border-full/50 bg-full/10 p-4 text-sm"
        >
          <p>{{ errorMessage }}</p>
          <sw-button size="sm" (click)="retry()">Try again</sw-button>
        </div>
      } @else if (loading()) {
        <div role="status" aria-label="Loading activity" class="flex flex-col gap-4">
          @for (row of skeletonRows; track row) {
            <span class="h-12 rounded-card bg-surface-muted motion-safe:animate-pulse"></span>
          }
        </div>
      } @else {
        <p class="text-ink-muted">No activity yet.</p>
      }
    </div>
  `,
})
export class ActivityTimeline {
  private readonly service = inject(AuditService);
  private readonly destroyRef = inject(DestroyRef);

  readonly workshopId = input<string | null>(null);
  readonly entityType = input<AuditEntityType | null>(null);
  readonly entityId = input<string | null>(null);
  readonly from = input<string | null>(null);
  readonly to = input<string | null>(null);
  readonly pageSize = input(DEFAULT_PAGE_SIZE);
  /** False shows the first page only, with no "Load more". */
  readonly paged = input(true);
  /** Link entries to the workshop or staff account they are about. */
  readonly showLinks = input(false);
  /** Change this to reload from the first page (e.g. after an edit on the same screen). */
  readonly refreshKey = input(0);

  protected readonly errorMessage = LOAD_ERROR_MESSAGE;
  protected readonly skeletonRows = [1, 2, 3];

  protected readonly items = signal<readonly AuditEvent[]>([]);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);

  private readonly totalItems = signal(0);
  private readonly nextPage = signal(0);
  private readonly now = signal(new Date());
  private readonly open = signal<ReadonlySet<number>>(new Set());

  protected readonly hasMore = computed(() => this.items().length < this.totalItems());

  private readonly scope = computed<Scope>(() => ({
    workshopId: this.workshopId(),
    entityType: this.entityType(),
    entityId: this.entityId(),
    from: this.from(),
    to: this.to(),
    size: this.pageSize(),
    refreshKey: this.refreshKey(),
  }));

  private subscription: Subscription | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.subscription?.unsubscribe());
    effect(() => {
      this.scope();
      untracked(() => {
        this.items.set([]);
        this.totalItems.set(0);
        this.nextPage.set(0);
        this.open.set(new Set());
        this.fetch();
      });
    });
  }

  protected loadMore(): void {
    this.fetch();
  }

  protected retry(): void {
    this.fetch();
  }

  protected actor(event: AuditEvent): string {
    return event.actor?.fullName ?? 'System';
  }

  protected action(event: AuditEvent): string {
    return actionLabel(event.action);
  }

  protected dot(event: AuditEvent): string {
    return DOT[actionTone(event.action)];
  }

  protected label(event: AuditEvent): string {
    return LABEL[actionTone(event.action)];
  }

  protected relative(event: AuditEvent): string {
    return formatRelative(event.occurredAt, this.now());
  }

  protected absolute(event: AuditEvent): string {
    return formatDateTime(event.occurredAt);
  }

  protected link(event: AuditEvent): { path: string[]; label: string } | null {
    if (event.entityType === 'STAFF_ACCOUNT') {
      return { path: ['/staff-accounts', event.entityId], label: 'View account' };
    }
    return event.workshopId ? { path: ['/workshops', event.workshopId], label: 'View workshop' } : null;
  }

  protected changeRows(event: AuditEvent): ChangeRow[] {
    return Object.entries(event.changes ?? {}).map(([field, change]) => ({
      label: fieldLabel(field),
      from: formatChangeValue(field, change.from),
      to: formatChangeValue(field, change.to),
    }));
  }

  protected isOpen(event: AuditEvent): boolean {
    return this.open().has(event.id);
  }

  protected toggle(event: AuditEvent): void {
    this.open.update((current) => {
      const next = new Set(current);
      if (!next.delete(event.id)) {
        next.add(event.id);
      }
      return next;
    });
  }

  /** Reads the next page and appends it. A newer request replaces one still in flight. */
  private fetch(): void {
    this.subscription?.unsubscribe();
    this.loading.set(true);
    this.failed.set(false);
    const scope = this.scope();
    const query: AuditQuery = {
      workshopId: scope.workshopId,
      entityType: scope.entityType,
      entityId: scope.entityId,
      from: scope.from,
      to: scope.to,
      page: this.nextPage(),
      size: scope.size,
    };
    this.subscription = this.service.list(query).subscribe({
      next: (page) => {
        this.items.update((current) => [...current, ...page.items]);
        this.totalItems.set(page.items.length === 0 ? this.items().length : page.totalItems);
        this.nextPage.set(query.page + 1);
        this.now.set(new Date());
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.failed.set(true);
      },
    });
  }
}
