import { useEffect, useRef } from 'react';
import type { AcceptedClaim } from './ClaimForm';

interface ConfirmationProps {
  claim: AcceptedClaim;
  onStartAnother: () => void;
}

/**
 * Shown once the API has accepted the claim. It confirms acceptance, not delivery: the email is
 * sent asynchronously by another service, so the wording says it will arrive shortly.
 */
export function Confirmation({ claim, onStartAnother }: ConfirmationProps) {
  const headingRef = useRef<HTMLHeadingElement>(null);

  // Move focus to the result so screen readers announce it and keyboard users start here.
  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  return (
    <section className="confirmation" aria-labelledby="confirmation-title">
      <div className="confirmation__panel">
        <h1 className="confirmation__title" id="confirmation-title" tabIndex={-1} ref={headingRef}>
          Claim submitted
        </h1>
        <p className="confirmation__label">Your claim number</p>
        <p className="confirmation__number" data-testid="claim-number">
          {claim.claimNumber}
        </p>
      </div>

      <h2>Check your email</h2>
      <p>
        We are sending a confirmation to <strong>{claim.email}</strong>. It should arrive in the next few minutes.
        If you cannot find it, check your junk or spam folder.
      </p>

      <h2>What happens next</h2>
      <ol>
        <li>An adjuster will contact you within two business days.</li>
        <li>If it is safe to do so, take photos of the damage and keep receipts for any emergency repairs.</li>
        <li>Quote your claim number whenever you contact us.</li>
      </ol>

      <button className="button button--secondary" type="button" onClick={onStartAnother}>
        Submit another claim
      </button>
    </section>
  );
}
