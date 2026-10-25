import { describe, expect, it, vi } from 'vitest';
import { submitClaim, toRequest } from './claimsApi';
import { VALID_VALUES } from '../test/fixtures';

const jsonResponse = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });

describe('toRequest', () => {
  it('nests and normalizes the flat form values into the API shape', () => {
    expect(toRequest({ ...VALID_VALUES, firstName: ' Jane ', policyNumber: 'ho-12345678' })).toEqual({
      customer: { firstName: 'Jane', lastName: "O'Neil", email: 'jane.oneil@example.com', phone: '2045550142' },
      policyNumber: 'HO-12345678',
      dateOfLoss: '2026-10-15',
      lossType: 'WATER',
      propertyAddress: { street: '123 Main Street', city: 'Winnipeg', province: 'MB', postalCode: 'R3C 1A1' },
      description: 'Pipe burst in the basement overnight and flooded the laundry room.',
      consentAcknowledged: true,
    });
  });

  it('omits an empty phone number', () => {
    expect(toRequest({ ...VALID_VALUES, phone: '' }).customer).not.toHaveProperty('phone');
  });
});

describe('submitClaim', () => {
  it('posts JSON with a request id and returns the claim number on 201', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(201, { claimNumber: 'CLM-2026-000123', status: 'SUBMITTED', submittedAt: '2026-10-18T15:00:00Z' }),
    );

    const result = await submitClaim(VALID_VALUES, fetchMock);

    expect(result).toMatchObject({ kind: 'accepted', claimNumber: 'CLM-2026-000123' });
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe('/api/v1/claims');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.headers['X-Request-Id']).toMatch(/^[0-9a-f-]{36}$/);
    expect(JSON.parse(init.body).customer.email).toBe('jane.oneil@example.com');
  });

  it('returns field errors on 400', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(400, {
        correlationId: 'abc',
        errors: [{ field: 'dateOfLoss', code: 'DATE_IN_FUTURE', message: 'Date of loss cannot be in the future.' }],
      }),
    );

    expect(await submitClaim(VALID_VALUES, fetchMock)).toEqual({
      kind: 'invalid',
      correlationId: 'abc',
      errors: [{ field: 'dateOfLoss', code: 'DATE_IN_FUTURE', message: 'Date of loss cannot be in the future.' }],
    });
  });

  it('reports a server error with its correlation id', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(500, { correlationId: 'ref-500' }));

    expect(await submitClaim(VALID_VALUES, fetchMock)).toEqual({ kind: 'failed', correlationId: 'ref-500' });
  });

  it('turns a network failure into a result instead of throwing', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

    const result = await submitClaim(VALID_VALUES, fetchMock);

    if (result.kind !== 'failed') {
      throw new Error('expected a failed result');
    }
    expect(result.correlationId).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('treats a non-JSON gateway error as a failure', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502 }));

    expect((await submitClaim(VALID_VALUES, fetchMock)).kind).toBe('failed');
  });
});
