import { z } from 'zod';
import { isLossType, isProvince } from './options';

/**
 * Client-side rules, mirroring the OpenAPI contract (claims-api/src/main/resources/openapi/claims-api.yaml). They exist for a fast, helpful form;
 * the Claims API re-validates everything and is the source of truth.
 */

// Same patterns as the contract; the API enforces them through code generated from it.
const PERSON_NAME = /^[A-Za-zÀ-ÖØ-öø-ÿ][A-Za-zÀ-ÖØ-öø-ÿ' -]*$/;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const PHONE_DIGITS = /^[2-9][0-9]{2}[2-9][0-9]{6}$/;
const POLICY_NUMBER = /^HO-[0-9]{8}$/;
const POSTAL_CODE = /^[A-Za-z][0-9][A-Za-z] ?[0-9][A-Za-z][0-9]$/;

export const DESCRIPTION_MIN = 20;
export const DESCRIPTION_MAX = 2000;
export const DATE_OF_LOSS_MAX_AGE_DAYS = 365;

/** Formatting the customer might type, such as (204) 555-0142, is accepted and stripped. */
export const phoneDigits = (phone: string) => phone.replace(/\D/g, '');

const requiredText = (requiredMessage: string, max: number, tooLongMessage: string) =>
  z.string().trim().min(1, { message: requiredMessage, abort: true }).max(max, tooLongMessage);

const personName = (label: string) =>
  requiredText(`Enter your ${label}.`, 50, `${capitalize(label)} must be 50 characters or fewer.`).regex(
    PERSON_NAME,
    `${capitalize(label)} can contain letters, spaces, apostrophes and hyphens.`,
  );

/** ISO date (YYYY-MM-DD) `days` before `isoDate`, using calendar arithmetic. */
export function minusDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() - days);
  return date.toISOString().slice(0, 10);
}

/** Today's date in the customer's own time zone, as YYYY-MM-DD. */
export function localToday(): string {
  return new Date().toLocaleDateString('en-CA');
}

/**
 * @param today the customer's calendar date (YYYY-MM-DD); injected so tests are deterministic.
 */
export function buildClaimSchema(today: string) {
  const oldest = minusDays(today, DATE_OF_LOSS_MAX_AGE_DAYS);

  return z.object({
    firstName: personName('first name'),
    lastName: personName('last name'),
    email: requiredText('Enter your email address.', 254, 'Email address must be 254 characters or fewer.').regex(
      EMAIL,
      'Enter an email address like name@example.com.',
    ),
    phone: z
      .string()
      .refine((value) => value.trim() === '' || PHONE_DIGITS.test(phoneDigits(value)), {
        message: 'Enter a 10-digit phone number, like 204 555 0142.',
      }),
    policyNumber: requiredText('Enter your policy number.', 11, 'Enter a policy number like HO-12345678.').regex(
      POLICY_NUMBER,
      'Enter a policy number like HO-12345678.',
    ),
    // ISO date strings compare correctly as text.
    dateOfLoss: z
      .string()
      .min(1, { message: 'Enter the date of loss.', abort: true })
      .refine((value) => value <= today, { message: 'Date of loss cannot be in the future.' })
      .refine((value) => value >= oldest, {
        message: `Date of loss must be within the last ${DATE_OF_LOSS_MAX_AGE_DAYS} days.`,
      }),
    lossType: z.string().refine((value): boolean => isLossType(value), { message: 'Select the type of loss.' }),
    street: requiredText('Enter the street address.', 100, 'Street address must be 100 characters or fewer.'),
    city: requiredText('Enter the city.', 60, 'City must be 60 characters or fewer.'),
    province: z
      .string()
      .refine((value): boolean => isProvince(value), { message: 'Select the province or territory.' }),
    postalCode: z.string().trim().min(1, { message: 'Enter the postal code.', abort: true }).regex(POSTAL_CODE, 'Enter a postal code like R3C 1A1.'),
    description: z
      .string()
      .trim()
      .min(1, { message: 'Describe the damage or loss.', abort: true })
      .min(DESCRIPTION_MIN, `Description must be at least ${DESCRIPTION_MIN} characters.`)
      .max(DESCRIPTION_MAX, 'Description must be 2,000 characters or fewer.'),
    consentAcknowledged: z
      .boolean()
      .refine((value) => value, { message: 'Confirm that you agree to be contacted about this claim.' }),
  });
}

export type ClaimFormValues = z.infer<ReturnType<typeof buildClaimSchema>>;

export const EMPTY_FORM: ClaimFormValues = {
  firstName: '',
  lastName: '',
  email: '',
  phone: '',
  policyNumber: '',
  dateOfLoss: '',
  lossType: '',
  street: '',
  city: '',
  province: '',
  postalCode: '',
  description: '',
  consentAcknowledged: false,
};

function capitalize(text: string) {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
