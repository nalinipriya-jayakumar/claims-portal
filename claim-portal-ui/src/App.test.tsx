import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from './App';
import type { SubmitClaim, SubmitResult } from './api/claimsApi';
import { TODAY } from './test/fixtures';

const user = () => userEvent.setup();

function renderApp(submit: SubmitClaim = vi.fn()) {
  render(<App submit={submit} today={TODAY} />);
  return submit;
}

async function fillValidClaim(u: ReturnType<typeof user>) {
  await u.type(screen.getByLabelText('First name'), 'Jane');
  await u.type(screen.getByLabelText('Last name'), "O'Neil");
  await u.type(screen.getByLabelText('Email address'), 'jane.oneil@example.com');
  await u.type(screen.getByLabelText('Policy number'), 'HO-12345678');
  await u.type(screen.getByLabelText('Date of loss'), '2026-10-15');
  await u.selectOptions(screen.getByLabelText('Type of loss'), 'WATER');
  await u.type(screen.getByLabelText('Description of the damage or loss'), 'Pipe burst and flooded the basement.');
  await u.type(screen.getByLabelText('Street address'), '123 Main Street');
  await u.type(screen.getByLabelText('City or town'), 'Winnipeg');
  await u.selectOptions(screen.getByLabelText('Province or territory'), 'MB');
  await u.type(screen.getByLabelText('Postal code'), 'R3C 1A1');
  await u.click(screen.getByLabelText(/I agree that Red River Mutual may contact me/));
}

describe('claim portal', () => {
  it('submitting an empty form lists every required field in a focused summary and sends nothing', async () => {
    const u = user();
    const submit = renderApp();

    await u.click(screen.getByRole('button', { name: 'Submit claim' }));

    const summary = await screen.findByRole('alert');
    expect(summary).toHaveFocus();
    expect(within(summary).getByRole('heading')).toHaveTextContent('There are 12 problems with your claim');
    expect(within(summary).getByRole('link', { name: 'Enter your first name.' })).toHaveAttribute(
      'href',
      '#field-firstName',
    );
    expect(submit).not.toHaveBeenCalled();
  });

  it('marks invalid fields and links their error message for screen readers', async () => {
    const u = user();
    renderApp();

    const email = screen.getByLabelText('Email address');
    await u.type(email, 'not-an-email');
    await u.tab();

    expect(email).toHaveAttribute('aria-invalid', 'true');
    expect(email).toHaveAccessibleDescription(expect.stringContaining('Enter an email address like name@example.com.'));
  });

  it('summary links move focus to the field', async () => {
    const u = user();
    renderApp();
    await u.click(screen.getByRole('button', { name: 'Submit claim' }));

    await u.click(await screen.findByRole('link', { name: 'Enter the postal code.' }));

    expect(screen.getByLabelText('Postal code')).toHaveFocus();
  });

  it('disables the button while submitting, then shows the claim number and asks to check email', async () => {
    const u = user();
    let resolve!: (result: SubmitResult) => void;
    const submit = vi.fn<SubmitClaim>(() => new Promise((r) => (resolve = r)));
    renderApp(submit);
    await fillValidClaim(u);

    await u.click(screen.getByRole('button', { name: 'Submit claim' }));

    expect(screen.getByRole('button', { name: 'Submitting…' })).toBeDisabled();
    resolve({ kind: 'accepted', claimNumber: 'CLM-2026-000123', submittedAt: '', message: '' });

    expect(await screen.findByRole('heading', { name: 'Claim submitted' })).toHaveFocus();
    expect(screen.getByTestId('claim-number')).toHaveTextContent('CLM-2026-000123');
    expect(screen.getByText('jane.oneil@example.com')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Check your email' })).toBeInTheDocument();
    expect(submit).toHaveBeenCalledTimes(1);
  });

  it('shows server-side validation errors against the matching fields', async () => {
    const u = user();
    renderApp(
      vi.fn<SubmitClaim>().mockResolvedValue({
        kind: 'invalid',
        errors: [{ field: 'propertyAddress.postalCode', code: 'INVALID_FORMAT', message: 'Postal code not recognised.' }],
      }),
    );
    await fillValidClaim(u);

    await u.click(screen.getByRole('button', { name: 'Submit claim' }));

    const summary = await screen.findByRole('alert');
    expect(within(summary).getByRole('link', { name: 'Postal code not recognised.' })).toBeInTheDocument();
    expect(screen.getByLabelText('Postal code')).toHaveAttribute('aria-invalid', 'true');
  });

  it('explains a failed submission and gives a reference to quote', async () => {
    const u = user();
    renderApp(vi.fn<SubmitClaim>().mockResolvedValue({ kind: 'failed', correlationId: 'ref-123' }));
    await fillValidClaim(u);

    await u.click(screen.getByRole('button', { name: 'Submit claim' }));

    const notice = await screen.findByRole('alert');
    await waitFor(() => expect(notice).toHaveFocus());
    expect(notice).toHaveTextContent('We could not submit your claim');
    expect(notice).toHaveTextContent('ref-123');
    expect(screen.getByRole('button', { name: 'Submit claim' })).toBeEnabled();
  });

  it('retrying the same claim after a failure reuses its idempotency key', async () => {
    const u = user();
    const submit = vi.fn<SubmitClaim>().mockResolvedValue({ kind: 'failed', correlationId: 'ref-1' });
    renderApp(submit);
    await fillValidClaim(u);

    await u.click(screen.getByRole('button', { name: 'Submit claim' }));
    await screen.findByRole('alert');
    await u.click(screen.getByRole('button', { name: 'Submit claim' }));

    await waitFor(() => expect(submit).toHaveBeenCalledTimes(2));
    const [firstKey, secondKey] = submit.mock.calls.map(([, key]) => key);
    expect(firstKey).toMatch(/^[0-9a-f-]{36}$/);
    expect(secondKey).toBe(firstKey);
  });

  it('changing the claim after a failure uses a new idempotency key', async () => {
    const u = user();
    const submit = vi.fn<SubmitClaim>().mockResolvedValue({ kind: 'failed', correlationId: 'ref-2' });
    renderApp(submit);
    await fillValidClaim(u);
    await u.click(screen.getByRole('button', { name: 'Submit claim' }));
    await screen.findByRole('alert');

    await u.type(screen.getByLabelText('Description of the damage or loss'), ' The dryer too.');
    await u.click(screen.getByRole('button', { name: 'Submit claim' }));

    await waitFor(() => expect(submit).toHaveBeenCalledTimes(2));
    const [firstKey, secondKey] = submit.mock.calls.map(([, key]) => key);
    expect(secondKey).not.toBe(firstKey);
  });
});
