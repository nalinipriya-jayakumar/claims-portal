import { useState } from 'react';
import { ClaimForm } from './claim-form/ClaimForm';
import type { AcceptedClaim } from './claim-form/ClaimForm';
import { Confirmation } from './claim-form/Confirmation';
import type { SubmitClaim } from './api/claimsApi';

interface AppProps {
  submit?: SubmitClaim;
  today?: string;
}

export function App({ submit, today }: AppProps) {
  const [accepted, setAccepted] = useState<AcceptedClaim | null>(null);

  return (
    <>
      <a className="skip-link" href="#main">
        Skip to main content
      </a>
      <header className="site-header">
        <div className="container">
          <span className="site-header__name">Red River Mutual</span>
        </div>
      </header>

      <main className="container" id="main">
        {/* Confirmation moves focus to its heading, which is how the result is announced. */}
        {accepted ? (
          <Confirmation claim={accepted} onStartAnother={() => setAccepted(null)} />
        ) : (
          <>
            <h1 className="page-title">Submit a home insurance claim</h1>
            <p className="lead">
              Tell us about damage or loss to your home. It takes about five minutes. You will get a claim number as
              soon as you submit, and a confirmation by email.
            </p>
            <ClaimForm onAccepted={setAccepted} submit={submit} today={today} />
          </>
        )}
      </main>

      <footer className="site-footer">
        <div className="container">
          In an emergency, such as fire or a burst pipe, make your home safe and call emergency services first.
        </div>
      </footer>
    </>
  );
}
