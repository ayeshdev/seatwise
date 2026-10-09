import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Button } from './button';
import { EmptyState } from './empty-state';
import { FieldControl, FormField } from './form-field';
import { Pager } from './pager';
import { Spinner } from './spinner';

@Component({
  imports: [FormField, FieldControl],
  template: `
    <sw-form-field label="Email" [hint]="hint()" [error]="error()">
      <input swFieldControl type="email" />
    </sw-form-field>
  `,
})
class FieldHost {
  readonly hint = signal<string | null>('We send the receipt here');
  readonly error = signal<string | null>(null);
}

@Component({
  imports: [Pager],
  template: `<sw-pager [page]="page()" [size]="20" [totalItems]="45" (pageChange)="page.set($event)" />`,
})
class PagerHost {
  readonly page = signal(0);
}

describe('sw-form-field', () => {
  it('links label, hint and error to the control', () => {
    const fixture = TestBed.createComponent(FieldHost);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const input = el.querySelector('input') as HTMLInputElement;
    const label = el.querySelector('label') as HTMLLabelElement;

    expect(label.htmlFor).toBe(input.id);
    expect(input.getAttribute('aria-invalid')).toBeNull();
    const hintId = input.getAttribute('aria-describedby') as string;
    expect(el.querySelector(`#${hintId}`)?.textContent).toContain('receipt');

    fixture.componentInstance.error.set('Enter a valid email address');
    fixture.detectChanges();

    expect(input.getAttribute('aria-invalid')).toBe('true');
    const ids = (input.getAttribute('aria-describedby') as string).split(' ');
    expect(ids).toHaveLength(2);
    expect(el.querySelector(`#${ids[1]}`)?.textContent).toContain('valid email');
  });

  it('omits aria-describedby when there is nothing to describe', () => {
    const fixture = TestBed.createComponent(FieldHost);
    fixture.componentInstance.hint.set(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('input').hasAttribute('aria-describedby')).toBe(
      false,
    );
  });
});

describe('sw-pager', () => {
  it('summarises the page and moves between pages', () => {
    const fixture = TestBed.createComponent(PagerHost);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const [previous, next] = Array.from(el.querySelectorAll('button'));

    expect(el.textContent).toContain('1–20 of 45');
    expect(previous.disabled).toBe(true);

    next.click();
    fixture.detectChanges();
    expect(el.textContent).toContain('21–40 of 45');

    next.click();
    fixture.detectChanges();
    expect(el.textContent).toContain('41–45 of 45');
    expect(next.disabled).toBe(true);
  });
});

describe('sw-button', () => {
  it('disables itself and flags busy while loading', () => {
    const fixture = TestBed.createComponent(Button);
    fixture.componentRef.setInput('loading', true);
    fixture.componentRef.setInput('variant', 'primary');
    fixture.detectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector('button') as HTMLButtonElement;

    expect(button.disabled).toBe(true);
    expect(button.getAttribute('aria-busy')).toBe('true');
    expect(button.className).toContain('bg-accent');
  });

  it('defaults to a non-submitting button', () => {
    const fixture = TestBed.createComponent(Button);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('button')?.type).toBe('button');
  });
});

describe('sw-empty-state and sw-spinner', () => {
  it('renders the heading and description', () => {
    const fixture = TestBed.createComponent(EmptyState);
    fixture.componentRef.setInput('heading', 'Nothing yet');
    fixture.componentRef.setInput('description', 'Check back later');
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nothing yet');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Check back later');
  });

  it('gives the spinner a screen-reader label', () => {
    const fixture = TestBed.createComponent(Spinner);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="status"]')?.textContent).toBe(
      'Loading',
    );
  });
});
