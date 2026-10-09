import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfirmDialog } from './confirm-dialog';
import { ConfirmDialogService } from './confirm-dialog.service';

describe('ConfirmDialog', () => {
  let fixture: ComponentFixture<ConfirmDialog>;
  let service: ConfirmDialogService;
  let root: HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(ConfirmDialog);
    service = TestBed.inject(ConfirmDialogService);
    root = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  const buttons = () => Array.from(root.querySelectorAll('button'));
  const buttonLabelled = (label: string) =>
    buttons().find((b) => b.textContent?.trim() === label) as HTMLButtonElement;

  it('stays empty until asked', () => {
    expect(root.querySelector('h2')).toBeNull();
  });

  it('shows the question and resolves confirmed when accepted', async () => {
    const answer = service.confirm({
      title: 'Cancel this registration?',
      message: 'Priya will be removed from the workshop.',
      confirmLabel: 'Cancel registration',
      danger: true,
    });
    fixture.detectChanges();

    expect(root.querySelector('h2')?.textContent).toContain('Cancel this registration?');
    expect(root.textContent).toContain('Priya will be removed from the workshop.');
    expect(root.querySelector('dialog')?.hasAttribute('open')).toBe(true);
    expect(root.querySelector('textarea')).toBeNull();

    buttonLabelled('Cancel registration').click();
    fixture.detectChanges();

    await expect(answer).resolves.toEqual({ confirmed: true, reason: null });
    expect(root.querySelector('dialog')?.hasAttribute('open')).toBe(false);
  });

  it('resolves not confirmed when declined', async () => {
    const answer = service.confirm({
      title: 'Sure?',
      message: 'Really?',
      confirmLabel: 'Yes',
    });
    fixture.detectChanges();

    buttonLabelled('Cancel').click();

    await expect(answer).resolves.toEqual({ confirmed: false, reason: null });
  });

  it('returns the trimmed reason when one was asked for', async () => {
    const answer = service.confirm({
      title: 'Deactivate account?',
      message: 'They will no longer be able to sign in.',
      confirmLabel: 'Deactivate',
      danger: true,
      askReason: true,
    });
    fixture.detectChanges();

    const textarea = root.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = '  Left the centre  ';
    textarea.dispatchEvent(new Event('input'));
    buttonLabelled('Deactivate').click();

    await expect(answer).resolves.toEqual({ confirmed: true, reason: 'Left the centre' });
  });

  it('drops the reason when the person backs out', async () => {
    const answer = service.confirm({
      title: 'Deactivate account?',
      message: 'x',
      confirmLabel: 'Deactivate',
      askReason: true,
    });
    fixture.detectChanges();
    const textarea = root.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'changed my mind';
    textarea.dispatchEvent(new Event('input'));

    buttonLabelled('Cancel').click();

    await expect(answer).resolves.toEqual({ confirmed: false, reason: null });
  });

  it('treats Escape (the dialog closing itself) as cancel', async () => {
    const answer = service.confirm({ title: 'Sure?', message: 'x', confirmLabel: 'Yes' });
    fixture.detectChanges();

    root.querySelector('dialog')?.dispatchEvent(new Event('close'));

    await expect(answer).resolves.toEqual({ confirmed: false, reason: null });
  });

  it('treats a click on the backdrop as cancel but ignores clicks inside', async () => {
    const answer = service.confirm({ title: 'Sure?', message: 'x', confirmLabel: 'Yes' });
    fixture.detectChanges();

    root.querySelector('h2')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(service.pending()).not.toBeNull();

    root.querySelector('dialog')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));

    await expect(answer).resolves.toEqual({ confirmed: false, reason: null });
  });

  it('cancels an older question when a newer one arrives', async () => {
    const first = service.confirm({ title: 'One', message: 'x', confirmLabel: 'Yes' });
    const second = service.confirm({ title: 'Two', message: 'x', confirmLabel: 'Yes' });
    fixture.detectChanges();

    await expect(first).resolves.toEqual({ confirmed: false, reason: null });
    buttonLabelled('Yes').click();
    await expect(second).resolves.toEqual({ confirmed: true, reason: null });
  });
});
