import { useEffect, useMemo, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { submitClaim as defaultSubmitClaim, toRequest } from '../api/claimsApi';
import type { SubmitClaim } from '../api/claimsApi';
import { ErrorSummary } from './ErrorSummary';
import type { SummaryItem } from './ErrorSummary';
import { CheckboxField, SelectField, TextAreaField, TextField } from './fields';
import type { FieldName } from './fields';
import { LOSS_TYPES, PROVINCES } from './options';
import {
  DATE_OF_LOSS_MAX_AGE_DAYS,
  DESCRIPTION_MAX,
  EMPTY_FORM,
  buildClaimSchema,
  localToday,
  minusDays,
} from './schema';
import type { ClaimFormValues } from './schema';

/** Display order of the error summary: the order fields appear on the page. */
const FIELD_ORDER: FieldName[] = [
  'firstName', 'lastName', 'email', 'phone', 'policyNumber', 'dateOfLoss', 'lossType',
  'street', 'city', 'province', 'postalCode', 'description', 'consentAcknowledged',
];

/** API error paths (FieldError.field in the OpenAPI contract) to the form's flat field names. */
const SERVER_FIELDS: Record<string, FieldName> = {
  'customer.firstName': 'firstName',
  'customer.lastName': 'lastName',
  'customer.email': 'email',
  'customer.phone': 'phone',
  policyNumber: 'policyNumber',
  dateOfLoss: 'dateOfLoss',
  lossType: 'lossType',
  'propertyAddress.street': 'street',
  'propertyAddress.city': 'city',
  'propertyAddress.province': 'province',
  'propertyAddress.postalCode': 'postalCode',
  description: 'description',
  consentAcknowledged: 'consentAcknowledged',
};

export interface AcceptedClaim {
  claimNumber: string;
  email: string;
}

interface ClaimFormProps {
  onAccepted: (claim: AcceptedClaim) => void;
  submit?: SubmitClaim;
  /** The customer's calendar date; injectable for tests. */
  today?: string;
}

export function ClaimForm({ onAccepted, submit = defaultSubmitClaim, today = localToday() }: ClaimFormProps) {
  const schema = useMemo(() => buildClaimSchema(today), [today]);
  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ClaimFormValues>({
    resolver: zodResolver(schema),
    defaultValues: EMPTY_FORM,
    // Validate a field when it loses focus, then live as the customer corrects it.
    mode: 'onTouched',
    // Focus goes to the error summary instead, which lists every problem.
    shouldFocusError: false,
  });

  const [showSummary, setShowSummary] = useState(false);
  // Re-render trigger for focus requests; the request itself lives in pendingFocus.
  const [, setFocusRequest] = useState(0);
  const pendingFocus = useRef(false);
  const [failureReference, setFailureReference] = useState<string | null>(null);
  const summaryRef = useRef<HTMLDivElement>(null);
  const failureRef = useRef<HTMLDivElement>(null);

  // React Hook Form publishes its errors after onInvalid returns, so the summary can appear one
  // render after the request. Keep the request until a render actually shows its target.
  useEffect(() => {
    if (!pendingFocus.current) {
      return;
    }
    const target = failureReference ? failureRef.current : summaryRef.current;
    if (target) {
      target.focus();
      pendingFocus.current = false;
    }
  });

  const requestFocus = () => {
    pendingFocus.current = true;
    setFocusRequest((n) => n + 1);
  };

  const summaryItems: SummaryItem[] = showSummary
    ? FIELD_ORDER.flatMap((name) => {
        const message = errors[name]?.message;
        return message ? [{ name, message }] : [];
      })
    : [];

  /*
   * One Idempotency-Key per claim. Re-sending exactly the same claim (after a network failure or a
   * server error) reuses the key, so if the first attempt did reach the server the customer gets
   * that claim number back instead of a second claim. Any change to the answers is a new claim
   * and gets a new key; reusing the old one would be rejected with 409.
   */
  const lastAttempt = useRef<{ request: string; key: string } | null>(null);
  const idempotencyKeyFor = (values: ClaimFormValues) => {
    const request = JSON.stringify(toRequest(values));
    if (lastAttempt.current?.request !== request) {
      lastAttempt.current = { request, key: crypto.randomUUID() };
    }
    return lastAttempt.current.key;
  };

  const onValid = async (values: ClaimFormValues) => {
    setFailureReference(null);
    const result = await submit(values, idempotencyKeyFor(values));

    if (result.kind === 'accepted') {
      onAccepted({ claimNumber: result.claimNumber, email: values.email.trim() });
      return;
    }
    if (result.kind === 'invalid') {
      const mapped = result.errors.filter((error) => SERVER_FIELDS[error.field]);
      mapped.forEach((error) =>
        setError(SERVER_FIELDS[error.field]!, { type: 'server', message: error.message }),
      );
      if (mapped.length > 0) {
        setShowSummary(true);
        requestFocus();
        return;
      }
    }
    // Server error, network failure, or a 400 that names no form field.
    setFailureReference(result.correlationId ?? 'unavailable');
    requestFocus();
  };

  const onInvalid = () => {
    setFailureReference(null);
    setShowSummary(true);
    requestFocus();
  };

  const description = watch('description') ?? '';

  return (
    <form className="claim-form" noValidate onSubmit={handleSubmit(onValid, onInvalid)} aria-busy={isSubmitting}>
      {failureReference && (
        <div className="notice notice--error" role="alert" tabIndex={-1} ref={failureRef}>
          <h2 className="notice__title">We could not submit your claim</h2>
          <p>Nothing has been saved. Please try again in a few minutes.</p>
          <p>
            If the problem continues, call us and quote reference <strong>{failureReference}</strong>.
          </p>
        </div>
      )}

      <ErrorSummary ref={summaryRef} items={summaryItems} />

      <fieldset className="section">
        <legend className="section__title">Your details</legend>
        <TextField name="firstName" label="First name" registration={register('firstName')} error={errors.firstName}
                   autoComplete="given-name" width="medium" />
        <TextField name="lastName" label="Last name" registration={register('lastName')} error={errors.lastName}
                   autoComplete="family-name" width="medium" />
        <TextField name="email" label="Email address" type="email" inputMode="email" registration={register('email')}
                   error={errors.email} autoComplete="email"
                   hint="We will send your claim number and next steps to this address." />
        <TextField name="phone" label="Phone number" type="tel" inputMode="tel" optional registration={register('phone')}
                   error={errors.phone} autoComplete="tel-national" width="medium"
                   hint="An adjuster may call you about your claim." />
      </fieldset>

      <fieldset className="section">
        <legend className="section__title">Your policy</legend>
        <TextField name="policyNumber" label="Policy number" registration={register('policyNumber')}
                   error={errors.policyNumber} width="medium"
                   hint="You can find it on your policy documents, for example HO-12345678." />
      </fieldset>

      <fieldset className="section">
        <legend className="section__title">What happened</legend>
        <TextField name="dateOfLoss" label="Date of loss" type="date" registration={register('dateOfLoss')}
                   error={errors.dateOfLoss} width="medium" max={today}
                   min={minusDays(today, DATE_OF_LOSS_MAX_AGE_DAYS)}
                   hint={`When the damage or loss happened. It must be within the last ${DATE_OF_LOSS_MAX_AGE_DAYS} days.`} />
        <SelectField name="lossType" label="Type of loss" registration={register('lossType')} error={errors.lossType}
                     options={LOSS_TYPES} />
        <TextAreaField name="description" label="Description of the damage or loss"
                       registration={register('description')} error={errors.description}
                       length={description.length} maxLength={DESCRIPTION_MAX}
                       hint="Tell us what happened and what was damaged or taken. At least 20 characters." />
      </fieldset>

      <fieldset className="section">
        <legend className="section__title">Property address</legend>
        <TextField name="street" label="Street address" registration={register('street')} error={errors.street}
                   autoComplete="address-line1" />
        <TextField name="city" label="City or town" registration={register('city')} error={errors.city}
                   autoComplete="address-level2" width="medium" />
        <SelectField name="province" label="Province or territory" registration={register('province')}
                     error={errors.province} options={PROVINCES} autoComplete="address-level1" />
        <TextField name="postalCode" label="Postal code" registration={register('postalCode')}
                   error={errors.postalCode} autoComplete="postal-code" width="short" />
      </fieldset>

      <fieldset className="section">
        <legend className="section__title">Consent</legend>
        <CheckboxField name="consentAcknowledged" registration={register('consentAcknowledged')}
                       error={errors.consentAcknowledged}
                       label="I agree that Red River Mutual may contact me about this claim and use the information I have given to handle it." />
      </fieldset>

      <button className="button" type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Submitting…' : 'Submit claim'}
      </button>
    </form>
  );
}
