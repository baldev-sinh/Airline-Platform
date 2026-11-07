import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthStore } from '../../core/auth/auth-store';
import { ApiError } from '../../core/http/api-error';
import { LoginPage } from './login-page';

describe('LoginPage', () => {
  const login = vi.fn();

  beforeEach(() => {
    login.mockReset();
    TestBed.configureTestingModule({
      imports: [LoginPage],
      providers: [
        provideRouter([]),
        { provide: AuthStore, useValue: { login, sessionExpired: () => false } },
      ],
    });
  });

  async function fillAndSubmit(fixture: ReturnType<typeof TestBed.createComponent<LoginPage>>) {
    const page = fixture.nativeElement as HTMLElement;
    const type = (selector: string, value: string) => {
      const input = page.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    type('#login-email', 'asha@example.com');
    type('#login-password', 'correct-horse-battery');
    page.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('does not submit an empty form and shows the errors', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    (fixture.nativeElement as HTMLElement)
      .querySelector('form')!
      .dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(login).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Email is required');
  });

  it('goes back to the page the guest came from', async () => {
    login.mockReturnValue(of({ id: 1 }));
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginPage);
    fixture.componentRef.setInput('returnUrl', '/book/42?pax=1');
    await fixture.whenStable();

    await fillAndSubmit(fixture);

    expect(login).toHaveBeenCalledWith({
      email: 'asha@example.com',
      password: 'correct-horse-battery',
    });
    expect(navigate).toHaveBeenCalledWith('/book/42?pax=1');
  });

  it('shows one generic message for wrong credentials', async () => {
    login.mockReturnValue(
      throwError(() => new ApiError(401, 'INVALID_CREDENTIALS', 'Invalid email or password')),
    );
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();

    await fillAndSubmit(fixture);

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent,
    ).toContain('That email and password combination is not right.');
  });
});
