import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { BookingApi } from '../../core/api/booking-api';
import { Booking } from '../../core/models/api-models';
import { PaymentReturnPage } from './payment-return-page';

describe('PaymentReturnPage', () => {
  const verifyPayment = vi.fn();
  const confirmed = { id: 21, status: 'CONFIRMED', passengers: [] } as unknown as Booking;

  beforeEach(() => {
    verifyPayment.mockReset().mockReturnValue(of({}));
    TestBed.configureTestingModule({
      imports: [PaymentReturnPage],
      providers: [
        provideRouter([]),
        { provide: BookingApi, useValue: { verifyPayment, get: () => of(confirmed) } },
      ],
    });
  });

  /** Opens the page as Razorpay's redirect would, with the given query parameters. */
  async function open(query: Record<string, string>) {
    const fixture = TestBed.createComponent(PaymentReturnPage);
    fixture.componentRef.setInput('bookingId', '21');
    for (const [name, value] of Object.entries(query)) {
      fixture.componentRef.setInput(name, value);
    }
    await fixture.whenStable();
    return fixture;
  }

  it('sends the payment id and the payment link id from the redirect to the server', async () => {
    const fixture = await open({
      razorpay_payment_id: 'pay_RealTest123',
      razorpay_payment_link_id: 'plink_RealTest456',
    });

    expect(verifyPayment).toHaveBeenCalledWith({
      razorpayPaymentId: 'pay_RealTest123',
      razorpayPaymentLinkId: 'plink_RealTest456',
    });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain("You're booked!");
  });

  it('drops a malformed payment link id instead of sending it', async () => {
    await open({ razorpay_payment_id: 'pay_RealTest123', razorpay_payment_link_id: 'not-a-link' });
    expect(verifyPayment).toHaveBeenCalledWith({ razorpayPaymentId: 'pay_RealTest123' });
  });

  it('does not call verify without a well-formed payment id', async () => {
    await open({ razorpay_payment_id: '<script>' });
    expect(verifyPayment).not.toHaveBeenCalled();
  });
});
