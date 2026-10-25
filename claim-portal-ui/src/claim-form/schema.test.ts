import { describe, expect, it } from 'vitest';
import { buildClaimSchema, minusDays } from './schema';
import type { ClaimFormValues } from './schema';
import { TODAY, VALID_VALUES } from '../test/fixtures';

const schema = buildClaimSchema(TODAY);

function errorsFor(overrides: Partial<ClaimFormValues>) {
  const result = schema.safeParse({ ...VALID_VALUES, ...overrides });
  if (result.success) {
    return {};
  }
  // First issue per field, which is what the form shows.
  const errors: Record<string, string> = {};
  for (const issue of result.error.issues) {
    errors[issue.path.join('.')] ??= issue.message;
  }
  return errors;
}

describe('claim schema', () => {
  it('accepts a complete, valid claim', () => {
    expect(schema.safeParse(VALID_VALUES).success).toBe(true);
  });

  it.each([
    ['firstName', '', 'Enter your first name.'],
    ['firstName', 'J4ne', 'First name can contain letters, spaces, apostrophes and hyphens.'],
    ['email', 'jane@example', 'Enter an email address like name@example.com.'],
    ['policyNumber', 'HO-123', 'Enter a policy number like HO-12345678.'],
    ['lossType', '', 'Select the type of loss.'],
    ['province', 'XX', 'Select the province or territory.'],
    ['postalCode', '12345', 'Enter a postal code like R3C 1A1.'],
    ['description', 'Too short', 'Description must be at least 20 characters.'],
  ] as const)('rejects %s = "%s"', (field, value, message) => {
    expect(errorsFor({ [field]: value })[field]).toBe(message);
  });

  it('accepts accented names and formatted phone numbers', () => {
    expect(errorsFor({ firstName: 'Zoë', lastName: 'Saint-Pierre', phone: '204-555-0142' })).toEqual({});
  });

  it('treats phone as optional but checks it when given', () => {
    expect(errorsFor({ phone: '' })).toEqual({});
    expect(errorsFor({ phone: '123' }).phone).toMatch(/10-digit/);
  });

  it('accepts today and exactly 365 days ago, rejects tomorrow and older', () => {
    expect(errorsFor({ dateOfLoss: TODAY })).toEqual({});
    expect(errorsFor({ dateOfLoss: minusDays(TODAY, 365) })).toEqual({});
    expect(errorsFor({ dateOfLoss: '2026-10-19' }).dateOfLoss).toBe('Date of loss cannot be in the future.');
    expect(errorsFor({ dateOfLoss: minusDays(TODAY, 366) }).dateOfLoss).toBe(
      'Date of loss must be within the last 365 days.',
    );
  });

  it('requires consent', () => {
    expect(errorsFor({ consentAcknowledged: false }).consentAcknowledged).toMatch(/agree to be contacted/);
  });

  it('subtracts days across month and leap-year boundaries', () => {
    expect(minusDays('2026-03-01', 1)).toBe('2026-02-28');
    expect(minusDays('2028-03-01', 1)).toBe('2028-02-29');
  });
});
