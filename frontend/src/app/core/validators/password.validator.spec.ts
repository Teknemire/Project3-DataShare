import { FormControl } from '@angular/forms';
import { passwordByteLength } from './password.validator';

describe('Password UTF-8 limit', () => {
  for (const [label, accepted, rejected] of [
    ['ASCII', 'a'.repeat(72), 'a'.repeat(73)],
    ['accented characters', 'é'.repeat(36), 'é'.repeat(37)],
    ['emoji', '🔑'.repeat(18), '🔑'.repeat(19)],
  ]) {
    it(`accepts exactly 72 bytes and rejects longer passwords with ${label}`, () => {
      expect(passwordByteLength(new FormControl(accepted, { nonNullable: true }))).toBeNull();
      expect(passwordByteLength(new FormControl(rejected, { nonNullable: true }))).toEqual({
        passwordTooLong: true,
      });
    });
  }

  it('leaves an empty optional password valid', () => {
    expect(passwordByteLength(new FormControl('', { nonNullable: true }))).toBeNull();
  });
});
