import { AbstractControl, ValidationErrors } from '@angular/forms';

export function passwordByteLength(control: AbstractControl<string>): ValidationErrors | null {
  return new TextEncoder().encode(control.value).length > 72 ? { passwordTooLong: true } : null;
}
