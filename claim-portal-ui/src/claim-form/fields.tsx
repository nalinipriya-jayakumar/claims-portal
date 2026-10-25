import type { ReactNode } from 'react';
import type { FieldError, UseFormRegisterReturn } from 'react-hook-form';
import type { ClaimFormValues } from './schema';

export type FieldName = keyof ClaimFormValues;

export const fieldId = (name: FieldName) => `field-${name}`;

interface FieldProps {
  name: FieldName;
  label: string;
  hint?: ReactNode;
  error?: FieldError;
  optional?: boolean;
  children: (aria: AriaProps) => ReactNode;
}

interface AriaProps {
  id: string;
  'aria-invalid': boolean;
  'aria-describedby'?: string;
}

/** Label, hint and error wiring shared by every input, so accessibility is consistent. */
function Field({ name, label, hint, error, optional, children }: FieldProps) {
  const id = fieldId(name);
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`field${error ? ' field--error' : ''}`}>
      <label className="field__label" htmlFor={id}>
        {label}
        {optional && <span className="field__optional"> (optional)</span>}
      </label>
      {hint && (
        <p className="field__hint" id={hintId}>
          {hint}
        </p>
      )}
      {error?.message && (
        <p className="field__error" id={errorId}>
          <span className="visually-hidden">Error: </span>
          {error.message}
        </p>
      )}
      {children({ id, 'aria-invalid': Boolean(error), 'aria-describedby': describedBy })}
    </div>
  );
}

interface InputProps {
  name: FieldName;
  label: string;
  registration: UseFormRegisterReturn;
  error?: FieldError;
  hint?: ReactNode;
  optional?: boolean;
  type?: 'text' | 'email' | 'tel' | 'date';
  autoComplete?: string;
  inputMode?: 'text' | 'email' | 'tel' | 'numeric';
  width?: 'short' | 'medium' | 'full';
  min?: string;
  max?: string;
}

export function TextField({ registration, type = 'text', width = 'full', autoComplete, inputMode, min, max, ...field }: InputProps) {
  return (
    <Field {...field}>
      {(aria) => (
        <input
          {...aria}
          {...registration}
          className={`input input--${width}`}
          type={type}
          autoComplete={autoComplete}
          inputMode={inputMode}
          min={min}
          max={max}
          spellCheck={false}
        />
      )}
    </Field>
  );
}

interface SelectProps {
  name: FieldName;
  label: string;
  registration: UseFormRegisterReturn;
  error?: FieldError;
  options: readonly { value: string; label: string }[];
  autoComplete?: string;
}

export function SelectField({ registration, options, autoComplete, ...field }: SelectProps) {
  return (
    <Field {...field}>
      {(aria) => (
        <select {...aria} {...registration} className="input input--medium" autoComplete={autoComplete}>
          <option value="">Select…</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      )}
    </Field>
  );
}

interface TextAreaProps {
  name: FieldName;
  label: string;
  registration: UseFormRegisterReturn;
  error?: FieldError;
  hint?: ReactNode;
  length: number;
  maxLength: number;
}

export function TextAreaField({ registration, length, maxLength, ...field }: TextAreaProps) {
  const counterId = `${fieldId(field.name)}-counter`;
  return (
    <Field {...field}>
      {(aria) => (
        <>
          <textarea
            {...aria}
            {...registration}
            aria-describedby={[aria['aria-describedby'], counterId].filter(Boolean).join(' ')}
            className="input input--full textarea"
            rows={6}
          />
          <p className={`field__counter${length > maxLength ? ' field__counter--over' : ''}`} id={counterId}>
            {length.toLocaleString('en-CA')} of {maxLength.toLocaleString('en-CA')} characters
          </p>
        </>
      )}
    </Field>
  );
}

interface CheckboxProps {
  name: FieldName;
  label: ReactNode;
  registration: UseFormRegisterReturn;
  error?: FieldError;
}

export function CheckboxField({ name, label, registration, error }: CheckboxProps) {
  const id = fieldId(name);
  const errorId = error ? `${id}-error` : undefined;
  return (
    <div className={`field${error ? ' field--error' : ''}`}>
      {error?.message && (
        <p className="field__error" id={errorId}>
          <span className="visually-hidden">Error: </span>
          {error.message}
        </p>
      )}
      <div className="checkbox">
        <input
          {...registration}
          id={id}
          type="checkbox"
          className="checkbox__input"
          aria-invalid={Boolean(error)}
          aria-describedby={errorId}
        />
        <label className="checkbox__label" htmlFor={id}>
          {label}
        </label>
      </div>
    </div>
  );
}
