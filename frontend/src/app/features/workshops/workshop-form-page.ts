import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';

import { messageFor } from '@core/http/messages';
import { ProblemError, isProblemError } from '@core/http/problem';
import { ToastService } from '@core/layout/toast.service';
import { Button } from '@shared/ui/button';
import { EmptyState } from '@shared/ui/empty-state';
import { FieldControl, FormField } from '@shared/ui/form-field';

import { toIsoDate } from './workshop-filters';
import { localDateValue, localTimeValue, toIsoInstant } from './workshop-format';
import { Location, Workshop, WorkshopRequest } from './workshop.models';
import { WorkshopsService } from './workshops.service';

export const CODE_PATTERN = /^[A-Z0-9-]{3,32}$/;

export type FieldName =
  | 'code'
  | 'title'
  | 'instructor'
  | 'locationId'
  | 'date'
  | 'startTime'
  | 'endTime'
  | 'capacity'
  | 'description';

/** The API names the start and end instants; the form splits them into date + times. */
const SERVER_FIELD_TO_CONTROL: Record<string, FieldName> = {
  code: 'code',
  title: 'title',
  instructor: 'instructor',
  locationId: 'locationId',
  startsAt: 'startTime',
  endsAt: 'endTime',
  capacity: 'capacity',
  description: 'description',
};

function integerValidator(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  return typeof value === 'number' && !Number.isInteger(value) ? { integer: true } : null;
}

/**
 * Cross-field rules for the schedule: the end is after the start and, when scheduling something
 * new, the start is still in the future. Reports `endBeforeStart` / `startInPast` on the group.
 */
export function scheduleValidator(
  requireFutureStart: () => boolean,
  now: () => Date = () => new Date(),
): ValidatorFn {
  return (group) => {
    const date = String(group.get('date')?.value ?? '');
    const startIso = toIsoInstant(date, String(group.get('startTime')?.value ?? ''));
    const endIso = toIsoInstant(date, String(group.get('endTime')?.value ?? ''));
    const errors: ValidationErrors = {};
    if (startIso !== null && endIso !== null && new Date(endIso) <= new Date(startIso)) {
      errors['endBeforeStart'] = true;
    }
    if (startIso !== null && requireFutureStart() && new Date(startIso) <= now()) {
      errors['startInPast'] = true;
    }
    return Object.keys(errors).length > 0 ? errors : null;
  };
}

@Component({
  selector: 'sw-workshop-form-page',
  imports: [ReactiveFormsModule, RouterLink, Button, EmptyState, FormField, FieldControl],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a
      [routerLink]="isEdit() ? ['/workshops', workshopId()] : ['/workshops']"
      class="mb-4 inline-block text-sm text-ink-muted hover:text-ink"
    >
      ← {{ isEdit() ? 'Back to the workshop' : 'All workshops' }}
    </a>

    <h1 class="mb-6 text-3xl">{{ isEdit() ? 'Edit workshop' : 'Schedule a workshop' }}</h1>

    @if (loadFailed()) {
      <sw-empty-state
        heading="We couldn't open this workshop"
        description="It may have been removed, or the connection dropped. Go back and try again."
      />
    } @else {
      @if (staleBanner()) {
        <div class="mb-6 flex flex-wrap items-center gap-3 rounded-card border border-warn/50 bg-warn/10 p-4" role="alert">
          <p class="flex-1">{{ staleBanner() }}</p>
          <sw-button size="sm" (click)="reloadWorkshop()">Reload</sw-button>
        </div>
      }
      @if (banner(); as message) {
        <p class="mb-6 rounded-card border border-full/50 bg-full/10 p-4" role="alert">{{ message }}</p>
      }

      <form
        class="grid max-w-3xl gap-5 rounded-card border border-line bg-surface p-6 sm:grid-cols-2"
        [formGroup]="form"
        (ngSubmit)="save()"
        novalidate
      >
        <sw-form-field label="Code" hint="Capital letters, numbers and dashes, like POT-0412" [error]="errorFor('code')">
          <input
            swFieldControl
            type="text"
            class="block w-full border-line bg-surface uppercase text-ink"
            formControlName="code"
            autocomplete="off"
            maxlength="32"
          />
        </sw-form-field>

        <sw-form-field label="Location" [error]="errorFor('locationId')">
          <select
            swFieldControl
            class="block w-full border-line bg-surface text-ink"
            formControlName="locationId"
          >
            <option value="">Choose a location</option>
            @for (location of locations(); track location.id) {
              <option [value]="location.id">{{ location.name }}</option>
            }
          </select>
        </sw-form-field>

        <div class="sm:col-span-2">
          <sw-form-field label="Title" [error]="errorFor('title')">
            <input
              swFieldControl
              type="text"
              class="block w-full border-line bg-surface text-ink"
              formControlName="title"
              autocomplete="off"
              maxlength="200"
            />
          </sw-form-field>
        </div>

        <div class="sm:col-span-2">
          <sw-form-field label="Instructor" [error]="errorFor('instructor')">
            <input
              swFieldControl
              type="text"
              class="block w-full border-line bg-surface text-ink"
              formControlName="instructor"
              autocomplete="off"
              maxlength="120"
            />
          </sw-form-field>
        </div>

        <div class="grid gap-5 sm:col-span-2 sm:grid-cols-3">
          <sw-form-field label="Date" [error]="errorFor('date')">
            <input
              swFieldControl
              type="date"
              class="block w-full border-line bg-surface text-ink"
              formControlName="date"
              [attr.min]="isEdit() ? null : today"
            />
          </sw-form-field>
          <sw-form-field label="Start time" [error]="errorFor('startTime')">
            <input
              swFieldControl
              type="time"
              class="block w-full border-line bg-surface text-ink"
              formControlName="startTime"
            />
          </sw-form-field>
          <sw-form-field label="End time" [error]="errorFor('endTime')">
            <input
              swFieldControl
              type="time"
              class="block w-full border-line bg-surface text-ink"
              formControlName="endTime"
            />
          </sw-form-field>
        </div>

        <sw-form-field
          label="Capacity"
          hint="Seats available, from 1 to 500"
          [error]="errorFor('capacity')"
        >
          <input
            swFieldControl
            type="number"
            inputmode="numeric"
            class="block w-full border-line bg-surface text-ink"
            formControlName="capacity"
            min="1"
            max="500"
          />
        </sw-form-field>

        <div class="sm:col-span-2">
          <sw-form-field label="Description (optional)" [error]="errorFor('description')">
            <textarea
              swFieldControl
              rows="4"
              class="block w-full border-line bg-surface text-ink"
              formControlName="description"
              maxlength="2000"
            ></textarea>
          </sw-form-field>
        </div>

        <div class="flex flex-wrap justify-end gap-2 sm:col-span-2">
          <sw-button (click)="back()">Cancel</sw-button>
          <sw-button type="submit" variant="primary" [loading]="saving()">
            {{ isEdit() ? 'Save changes' : 'Schedule workshop' }}
          </sw-button>
        </div>
      </form>
    }
  `,
})
export class WorkshopFormPage {
  private readonly service = inject(WorkshopsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly workshopId = signal(this.route.snapshot.paramMap.get('id') ?? '');
  protected readonly isEdit = computed(() => this.workshopId() !== '');
  protected readonly locations = signal<readonly Location[]>([]);
  protected readonly saving = signal(false);
  protected readonly loadFailed = signal(false);
  protected readonly banner = signal<string | null>(null);
  protected readonly staleBanner = signal<string | null>(null);
  protected readonly today = toIsoDate(new Date());

  private readonly serverErrors = signal<Partial<Record<FieldName, string>>>({});
  /** The workshop as loaded for editing: its `version` rides along on save. */
  private loaded: Workshop | null = null;

  protected readonly form = new FormGroup(
    {
      code: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.pattern(CODE_PATTERN)],
      }),
      title: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      instructor: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      locationId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      date: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      startTime: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      endTime: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      capacity: new FormControl<number | null>(null, {
        validators: [Validators.required, Validators.min(1), Validators.max(500), integerValidator],
      }),
      description: new FormControl('', { nonNullable: true }),
    },
    { validators: [scheduleValidator(() => !this.isEdit())] },
  );

  constructor() {
    this.service
      .listLocations()
      .pipe(
        catchError(() => of<Location[]>([])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((locations) => this.locations.set(locations));

    // Codes are always capitals, so type them that way for people.
    this.form.controls.code.valueChanges.pipe(takeUntilDestroyed()).subscribe((value) => {
      const upper = value.toUpperCase();
      if (upper !== value) {
        this.form.controls.code.setValue(upper, { emitEvent: false });
      }
    });

    // Editing a field drops the server's complaint about its old value.
    for (const name of Object.keys(this.form.controls) as FieldName[]) {
      const control: AbstractControl = this.form.controls[name];
      control.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
        if (this.serverErrors()[name] !== undefined) {
          this.serverErrors.update((errors) => ({ ...errors, [name]: undefined }));
        }
      });
    }

    if (this.isEdit()) {
      this.loadWorkshop();
    }
  }

  /** What to show under a field: the server's word first, else our own check once it was touched. */
  protected errorFor(name: FieldName): string | null {
    const server = this.serverErrors()[name];
    if (server) {
      return server;
    }
    const control = this.form.controls[name];
    if (!control.touched) {
      return null;
    }
    switch (name) {
      case 'code':
        return control.hasError('required')
          ? 'Enter a workshop code.'
          : control.hasError('pattern')
            ? 'Use 3 to 32 capital letters, numbers or dashes, like POT-0412.'
            : null;
      case 'title':
        return control.hasError('required') ? 'Enter a title.' : null;
      case 'instructor':
        return control.hasError('required') ? "Enter the instructor's name." : null;
      case 'locationId':
        return control.hasError('required') ? 'Choose where it is held.' : null;
      case 'date':
        return control.hasError('required') ? 'Pick a date.' : null;
      case 'startTime':
        if (control.hasError('required')) {
          return 'Pick a start time.';
        }
        return this.form.hasError('startInPast') ? 'The start must be in the future.' : null;
      case 'endTime':
        if (control.hasError('required')) {
          return 'Pick an end time.';
        }
        return this.form.hasError('endBeforeStart') ? 'The end time must be after the start time.' : null;
      case 'capacity':
        return control.hasError('required')
          ? 'Enter the number of seats.'
          : control.invalid
            ? 'Enter a whole number from 1 to 500.'
            : null;
      case 'description':
        return null;
    }
  }

  protected back(): void {
    void this.router.navigate(this.isEdit() ? ['/workshops', this.workshopId()] : ['/workshops']);
  }

  protected reloadWorkshop(): void {
    this.staleBanner.set(null);
    this.banner.set(null);
    this.loadWorkshop();
  }

  protected save(): void {
    if (this.saving()) {
      return;
    }
    this.banner.set(null);
    this.staleBanner.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const request = this.buildRequest();
    if (request === null) {
      return;
    }

    this.saving.set(true);
    const call = this.isEdit()
      ? this.service.update(this.workshopId(), request)
      : this.service.create(request);
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.toasts.success(this.isEdit() ? 'Changes saved.' : 'The workshop is scheduled.');
        void this.router.navigate(['/workshops', saved.id ?? this.workshopId()]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.showServerError(error);
      },
    });
  }

  private buildRequest(): WorkshopRequest | null {
    const v = this.form.getRawValue();
    const startsAt = toIsoInstant(v.date, v.startTime);
    const endsAt = toIsoInstant(v.date, v.endTime);
    if (startsAt === null || endsAt === null || v.capacity === null) {
      return null;
    }
    const request: WorkshopRequest = {
      code: v.code.trim().toUpperCase(),
      title: v.title.trim(),
      instructor: v.instructor.trim(),
      description: v.description.trim() === '' ? null : v.description.trim(),
      locationId: v.locationId,
      startsAt,
      endsAt,
      capacity: v.capacity,
    };
    if (this.isEdit() && this.loaded !== null) {
      request.version = this.loaded.version;
    }
    return request;
  }

  private showServerError(error: unknown): void {
    if (!isProblemError(error)) {
      return;
    }
    switch (error.code) {
      case 'VALIDATION_FAILED':
        this.showFieldErrors(error);
        break;
      case 'CAPACITY_BELOW_TAKEN':
        this.serverErrors.update((errors) => ({ ...errors, capacity: this.capacityMessage(error) }));
        break;
      case 'STALE_VERSION':
        this.staleBanner.set(messageFor(error));
        break;
      default:
        this.banner.set(messageFor(error));
    }
  }

  private showFieldErrors(error: ProblemError): void {
    const mapped: Partial<Record<FieldName, string>> = {};
    let unplaced = false;
    for (const fieldError of error.fieldErrors) {
      const control = SERVER_FIELD_TO_CONTROL[fieldError.field];
      if (control === undefined) {
        unplaced = true;
      } else {
        mapped[control] = fieldError.message;
      }
    }
    this.serverErrors.update((errors) => ({ ...errors, ...mapped }));
    if (unplaced || error.fieldErrors.length === 0) {
      this.banner.set(messageFor(error));
    }
  }

  private capacityMessage(error: ProblemError): string {
    const taken = this.loaded?.seatsTaken;
    const tail = taken === undefined ? '' : `at least ${taken} seats are already taken.`;
    if (error.detail && tail) {
      return `${error.detail} — ${tail}`;
    }
    if (tail) {
      return `${tail.charAt(0).toUpperCase()}${tail.slice(1)}`;
    }
    return messageFor(error);
  }

  private loadWorkshop(): void {
    this.loadFailed.set(false);
    this.service
      .get(this.workshopId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (workshop) => {
          this.loaded = workshop;
          this.serverErrors.set({});
          this.form.reset({
            code: workshop.code,
            title: workshop.title,
            instructor: workshop.instructor,
            locationId: workshop.location.id,
            date: localDateValue(workshop.startsAt),
            startTime: localTimeValue(workshop.startsAt),
            endTime: localTimeValue(workshop.endsAt),
            capacity: workshop.capacity,
            description: workshop.description ?? '',
          });
        },
        error: () => this.loadFailed.set(true),
      });
  }
}
