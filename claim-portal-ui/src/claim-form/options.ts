import type { components } from '../api/generated/claims-api';

/** Value sets from the OpenAPI contract (generated; see `npm run generate:api`). */
export type LossType = components['schemas']['LossType'];
export type Province = components['schemas']['Province'];

/*
 * Labels are keyed by the contract's enums. Record<...> makes the lists exhaustive: when the
 * contract adds or removes a value, the type check fails here until the labels are updated.
 */
const LOSS_TYPE_LABELS: Record<LossType, string> = {
  FIRE: 'Fire',
  WATER: 'Water damage',
  WIND_HAIL: 'Wind or hail',
  THEFT: 'Theft',
  VANDALISM: 'Vandalism',
  OTHER: 'Other',
};

const PROVINCE_LABELS: Record<Province, string> = {
  AB: 'Alberta',
  BC: 'British Columbia',
  MB: 'Manitoba',
  NB: 'New Brunswick',
  NL: 'Newfoundland and Labrador',
  NS: 'Nova Scotia',
  NT: 'Northwest Territories',
  NU: 'Nunavut',
  ON: 'Ontario',
  PE: 'Prince Edward Island',
  QC: 'Quebec',
  SK: 'Saskatchewan',
  YT: 'Yukon',
};

const toOptions = <T extends string>(labels: Record<T, string>) =>
  (Object.entries(labels) as [T, string][]).map(([value, label]) => ({ value, label }));

export const LOSS_TYPES = toOptions(LOSS_TYPE_LABELS);
export const PROVINCES = toOptions(PROVINCE_LABELS);

export const isLossType = (value: string): value is LossType => value in LOSS_TYPE_LABELS;
export const isProvince = (value: string): value is Province => value in PROVINCE_LABELS;
