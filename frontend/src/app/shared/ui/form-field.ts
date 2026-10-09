import {
  ChangeDetectionStrategy,
  Component,
  Directive,
  computed,
  forwardRef,
  inject,
  input,
} from '@angular/core';

let nextFieldId = 0;

/**
 * Label, hint and error text around one control. Put `swFieldControl` on the projected
 * input/select/textarea so it gets the id, `aria-describedby` and `aria-invalid` wiring:
 *
 *   <sw-form-field label="Email" hint="We'll send the receipt here" [error]="emailError()">
 *     <input swFieldControl type="email" formControlName="email" />
 *   </sw-form-field>
 */
@Component({
  selector: 'sw-form-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <label class="mb-1 block text-sm font-medium text-ink" [for]="controlId">{{ label() }}</label>
    <ng-content />
    @if (hint()) {
      <p class="mt-1 text-sm text-ink-muted" [id]="hintId">{{ hint() }}</p>
    }
    @if (error()) {
      <p class="mt-1 text-sm text-full" [id]="errorId">{{ error() }}</p>
    }
  `,
})
export class FormField {
  readonly label = input.required<string>();
  readonly hint = input<string | null>(null);
  readonly error = input<string | null>(null);

  private readonly uid = `sw-field-${nextFieldId++}`;
  readonly controlId = `${this.uid}-control`;
  readonly hintId = `${this.uid}-hint`;
  readonly errorId = `${this.uid}-error`;

  readonly hasError = computed(() => !!this.error());
  readonly describedBy = computed(() => {
    const ids = [this.hint() ? this.hintId : null, this.error() ? this.errorId : null];
    const present = ids.filter((id): id is string => id !== null);
    return present.length > 0 ? present.join(' ') : null;
  });
}

@Directive({
  selector: '[swFieldControl]',
  host: {
    '[id]': 'field.controlId',
    '[attr.aria-describedby]': 'field.describedBy()',
    '[attr.aria-invalid]': 'field.hasError() ? "true" : null',
  },
})
export class FieldControl {
  protected readonly field = inject(forwardRef(() => FormField));
}
