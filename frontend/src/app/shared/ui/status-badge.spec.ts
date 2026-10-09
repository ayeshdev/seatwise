import { TestBed } from '@angular/core/testing';

import { Status, StatusBadge, statusLabel } from './status-badge';

describe('StatusBadge', () => {
  it.each([
    ['OPEN', 'Open'],
    ['FULL', 'Full'],
    ['IN_PROGRESS', 'In progress'],
    ['COMPLETED', 'Completed'],
    ['CANCELLED', 'Cancelled'],
    ['ACTIVE', 'Registered'],
    ['WAITLISTED', 'Waitlisted'],
  ] as [Status, string][])('shows %s as "%s"', (status, label) => {
    const fixture = TestBed.createComponent(StatusBadge);
    fixture.componentRef.setInput('status', status);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(statusLabel(status)).toBe(label);
    expect(text.trim()).toBe(label);
    expect(text).not.toContain('_');
  });
});
