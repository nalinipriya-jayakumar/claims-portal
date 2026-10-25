import type { ClaimFormValues } from '../claim-form/schema';

/** The fixed "today" used by every test, so date rules are deterministic. */
export const TODAY = '2026-10-18';

export const VALID_VALUES: ClaimFormValues = {
  firstName: 'Jane',
  lastName: "O'Neil",
  email: 'jane.oneil@example.com',
  phone: '(204) 555-0142',
  policyNumber: 'HO-12345678',
  dateOfLoss: '2026-10-15',
  lossType: 'WATER',
  street: '123 Main Street',
  city: 'Winnipeg',
  province: 'MB',
  postalCode: 'r3c 1a1',
  description: 'Pipe burst in the basement overnight and flooded the laundry room.',
  consentAcknowledged: true,
};
