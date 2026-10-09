import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { firstValueFrom, map } from 'rxjs';

import { Role, isRole } from '@core/auth/role';
import { messageFor } from '@core/http/messages';
import { Button } from '@shared/ui/button';
import { EmptyState } from '@shared/ui/empty-state';
import { Pager } from '@shared/ui/pager';
import { Spinner } from '@shared/ui/spinner';

import { AccountStatus, RoleChip } from './account-chips';
import { StaffAccount, StaffAccountQuery, asProblem, formatDate } from './staff-accounts.model';
import { StaffAccountsService } from './staff-accounts.service';

export const PAGE_SIZE = 20;

type RoleFilter = Role | 'ALL';

/** URL <-> filters: `?role=STAFF&deactivated=true&page=2` (page is zero-based, like the API). */
export function queryFromParams(params: { get(name: string): string | null }): StaffAccountQuery {
  const role = params.get('role');
  const page = Number.parseInt(params.get('page') ?? '', 10);
  return {
    role: isRole(role) ? role : null,
    active: params.get('deactivated') === 'true' ? null : true,
    page: Number.isInteger(page) && page > 0 ? page : 0,
    size: PAGE_SIZE,
  };
}

export function paramsFromQuery(query: StaffAccountQuery): Params {
  return {
    role: query.role,
    deactivated: query.active === null ? 'true' : null,
    page: query.page > 0 ? query.page : null,
  };
}

@Component({
  selector: 'sw-staff-accounts-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    Button,
    EmptyState,
    Pager,
    Spinner,
    RoleChip,
    AccountStatus,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mb-6 flex flex-wrap items-center justify-between gap-4">
      <h1 class="text-3xl">Staff accounts</h1>
      <sw-button variant="primary" (click)="add()">Add staff member</sw-button>
    </div>

    <div
      class="mb-4 flex flex-wrap items-end gap-x-6 gap-y-3 rounded-card border border-line bg-surface-muted p-4"
    >
      <div>
        <label class="mb-1 block text-sm font-medium" for="sw-role-filter">Role</label>
        <select
          id="sw-role-filter"
          class="block border-line bg-surface text-ink"
          [formControl]="roleFilter"
        >
          <option value="ALL">All</option>
          <option value="ADMIN">Admin</option>
          <option value="MANAGER">Programme manager</option>
          <option value="STAFF">Front desk</option>
        </select>
      </div>
      <label class="flex cursor-pointer items-center gap-2 pb-2 text-sm font-medium">
        <input type="checkbox" class="text-accent" [formControl]="showDeactivated" />
        Show deactivated
      </label>
    </div>

    @if (errorMessage(); as message) {
      <div role="alert" class="mb-4 rounded-card border border-full/50 bg-full/10 p-4 text-sm">
        <p>{{ message }}</p>
        <div class="mt-3">
          <sw-button size="sm" (click)="reload()">Try again</sw-button>
        </div>
      </div>
    }

    @if (loading() && items().length === 0) {
      <div class="flex justify-center py-12"><sw-spinner label="Loading staff accounts" /></div>
    } @else if (!errorMessage() && items().length === 0) {
      <sw-empty-state
        heading="No staff accounts found"
        description="Try a different role, or turn on “Show deactivated”."
      />
    } @else if (items().length > 0) {
      <div
        class="overflow-x-auto rounded-card border border-line bg-surface transition-opacity"
        [class.opacity-60]="loading()"
        [attr.aria-busy]="loading() ? 'true' : null"
      >
        <table class="w-full text-left text-sm">
          <caption class="sr-only">
            Staff accounts
          </caption>
          <thead class="bg-surface-muted text-ink-muted">
            <tr>
              <th scope="col" class="px-4 py-3 font-medium">Name</th>
              <th scope="col" class="px-4 py-3 font-medium">Email</th>
              <th scope="col" class="px-4 py-3 font-medium">Role</th>
              <th scope="col" class="px-4 py-3 font-medium">Status</th>
              <th scope="col" class="px-4 py-3 font-medium">Added</th>
            </tr>
          </thead>
          <tbody>
            @for (account of items(); track account.id) {
              <tr
                class="cursor-pointer border-t border-line hover:bg-surface-muted"
                (click)="open(account)"
              >
                <td class="px-4 py-3 font-medium">
                  <a
                    class="text-ink hover:underline"
                    [routerLink]="['/staff-accounts', account.id]"
                    (click)="$event.stopPropagation()"
                  >
                    {{ account.fullName }}
                  </a>
                </td>
                <td class="px-4 py-3 text-ink-muted">{{ account.email }}</td>
                <td class="px-4 py-3"><sw-role-chip [role]="account.role" /></td>
                <td class="px-4 py-3"><sw-account-status [active]="account.active" /></td>
                <td class="px-4 py-3 tabular-nums text-ink-muted">{{ added(account) }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <div class="mt-4">
        <sw-pager
          [page]="query().page"
          [size]="query().size"
          [totalItems]="totalItems()"
          (pageChange)="goToPage($event)"
        />
      </div>
    }
  `,
})
export class StaffAccountsPage {
  private readonly service = inject(StaffAccountsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly roleFilter = new FormControl<RoleFilter>('ALL', { nonNullable: true });
  protected readonly showDeactivated = new FormControl(false, { nonNullable: true });

  protected readonly query = toSignal(
    this.route.queryParamMap.pipe(map((params) => queryFromParams(params))),
    { initialValue: queryFromParams(this.route.snapshot.queryParamMap) },
  );

  protected readonly items = signal<readonly StaffAccount[]>([]);
  protected readonly totalItems = signal(0);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  private requestId = 0;

  constructor() {
    // URL -> controls (back button, shared links).
    effect(() => {
      const query = this.query();
      this.roleFilter.setValue(query.role ?? 'ALL', { emitEvent: false });
      this.showDeactivated.setValue(query.active === null, { emitEvent: false });
    });

    // Controls -> URL. Changing a filter goes back to the first page.
    this.roleFilter.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((value) => {
      this.navigate({ ...this.query(), role: value === 'ALL' ? null : value, page: 0 });
    });
    this.showDeactivated.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((on) => {
      this.navigate({ ...this.query(), active: on ? null : true, page: 0 });
    });

    // URL -> data.
    effect(() => {
      const query = this.query();
      untracked(() => void this.load(query));
    });
  }

  protected added(account: StaffAccount): string {
    return formatDate(account.createdAt);
  }

  protected add(): void {
    void this.router.navigate(['/staff-accounts', 'new']);
  }

  protected open(account: StaffAccount): void {
    void this.router.navigate(['/staff-accounts', account.id]);
  }

  protected goToPage(page: number): void {
    this.navigate({ ...this.query(), page });
  }

  protected reload(): void {
    void this.load(this.query());
  }

  private navigate(query: StaffAccountQuery): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: paramsFromQuery(query),
    });
  }

  private async load(query: StaffAccountQuery): Promise<void> {
    const id = ++this.requestId;
    this.loading.set(true);
    this.errorMessage.set(null);
    try {
      const result = await firstValueFrom(this.service.list(query));
      if (id !== this.requestId) {
        return; // a newer filter change already took over
      }
      this.items.set(result.items);
      this.totalItems.set(result.totalItems);
    } catch (error) {
      if (id !== this.requestId) {
        return;
      }
      this.items.set([]);
      this.totalItems.set(0);
      const problem = asProblem(error);
      this.errorMessage.set(
        problem === null
          ? 'Something went wrong. Please try again, or reload the page.'
          : messageFor(problem),
      );
    } finally {
      if (id === this.requestId) {
        this.loading.set(false);
      }
    }
  }
}
