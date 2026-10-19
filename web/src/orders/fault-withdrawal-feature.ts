/** The destructive withdrawal surface requires an explicit isolated-build opt-in. */
export function isFaultWithdrawalEnabled(value: unknown): boolean {
  return value === 'true';
}

export const faultWithdrawalEnabled = isFaultWithdrawalEnabled(import.meta.env?.VITE_FAULT_WITHDRAWAL_ENABLED);
