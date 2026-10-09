import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SeatMeter, seatTone } from './seat-meter';

describe('SeatMeter', () => {
  let fixture: ComponentFixture<SeatMeter>;

  function render(seatsLeft: number, capacity: number): HTMLElement {
    fixture = TestBed.createComponent(SeatMeter);
    fixture.componentRef.setInput('seatsLeft', seatsLeft);
    fixture.componentRef.setInput('capacity', capacity);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows "3 of 20 left" in the warn tone with an accessible label', () => {
    const el = render(3, 20);
    const meter = el.querySelector('[role="meter"]');

    expect(el.textContent).toContain('3 of 20 left');
    expect(meter?.getAttribute('aria-label')).toBe('3 of 20 seats left');
    expect(meter?.getAttribute('aria-valuenow')).toBe('3');
    expect(meter?.getAttribute('aria-valuemax')).toBe('20');
    expect(el.querySelector('.bg-warn')).not.toBeNull();
  });

  it('uses the accent fill when there is plenty of room', () => {
    const el = render(12, 20);

    expect(el.querySelector('.bg-accent')).not.toBeNull();
    expect(el.querySelector('.bg-warn')).toBeNull();
    expect((el.querySelector('.bg-accent') as HTMLElement).style.width).toBe('40%');
  });

  it('uses the full tone at zero and says so in words', () => {
    const el = render(0, 20);

    expect(el.textContent).toContain('0 of 20 left');
    expect(el.querySelector('[role="meter"]')?.getAttribute('aria-label')).toBe(
      'No seats left, 0 of 20',
    );
    expect(el.querySelector('.bg-full')).not.toBeNull();
    expect((el.querySelector('.bg-full') as HTMLElement).style.width).toBe('100%');
  });

  it('clamps nonsense values', () => {
    const el = render(-2, 10);

    expect(el.textContent).toContain('0 of 10 left');
  });

  it.each([
    [0, 'none'],
    [1, 'low'],
    [3, 'low'],
    [4, 'plenty'],
  ] as const)('maps %i seats left to the %s tone', (left, tone) => {
    expect(seatTone(left)).toBe(tone);
  });
});
