import { test, expect } from '@playwright/test';
import { passwordSchema } from '../src/users/contracts';

for (const value of ['scan2026', '87654321', 'abcdefgh', '仓库密码现场使用', 'a'.repeat(72), '密'.repeat(24)]) {
  test(`new password accepts ${value.length} characters / ${new TextEncoder().encode(value).length} bytes: ${value.slice(0, 8)}`, () => {
    expect(passwordSchema.parse(value)).toBe(value);
  });
}

for (const value of ['', 'short7x', '密码不足七个字']) {
  test(`new password rejects ${value.length} characters: ${value || 'empty'}`, () => {
    const result = passwordSchema.safeParse(value);
    expect(result.success).toBe(false);
    if (!result.success) expect(result.error.issues[0]?.message).toBe('密码至少8位');
  });
}

for (const value of ['a'.repeat(73), '密'.repeat(25)]) {
  test(`new password rejects ${new TextEncoder().encode(value).length} UTF-8 bytes`, () => {
    const result = passwordSchema.safeParse(value);
    expect(result.success).toBe(false);
    if (!result.success) expect(result.error.issues[0]?.message).toBe('密码最多72个UTF-8字节');
  });
}
