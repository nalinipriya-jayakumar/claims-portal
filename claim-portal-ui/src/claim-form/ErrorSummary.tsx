import { forwardRef } from 'react';
import type { MouseEvent } from 'react';
import type { FieldName } from './fields';
import { fieldId } from './fields';

export interface SummaryItem {
  name: FieldName;
  message: string;
}

interface ErrorSummaryProps {
  items: SummaryItem[];
}

/**
 * Lists every problem at the top of the form after a failed submit. Receives focus so screen
 * reader and keyboard users land on it; each link moves focus to the field it describes.
 */
export const ErrorSummary = forwardRef<HTMLDivElement, ErrorSummaryProps>(function ErrorSummary({ items }, ref) {
  if (items.length === 0) {
    return null;
  }

  const focusField = (event: MouseEvent<HTMLAnchorElement>, name: FieldName) => {
    event.preventDefault();
    const element = document.getElementById(fieldId(name));
    element?.focus();
    element?.scrollIntoView?.({ block: 'center' });
  };

  return (
    <div className="error-summary" ref={ref} tabIndex={-1} role="alert" aria-labelledby="error-summary-title">
      <h2 className="error-summary__title" id="error-summary-title">
        There {items.length === 1 ? 'is a problem' : `are ${items.length} problems`} with your claim
      </h2>
      <ul className="error-summary__list">
        {items.map((item) => (
          <li key={item.name}>
            <a href={`#${fieldId(item.name)}`} onClick={(event) => focusField(event, item.name)}>
              {item.message}
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
});
