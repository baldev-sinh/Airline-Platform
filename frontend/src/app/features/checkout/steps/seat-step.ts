import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { SeatInstance } from '../../../core/models/api-models';
import { MoneyPipe } from '../../../shared/money-pipe';
import { CheckoutStore } from '../checkout-store';
import { isSelectable } from '../seat-layout';

const SEAT_TYPE_LABELS: Record<string, string> = {
  WINDOW: 'window',
  AISLE: 'aisle',
  MIDDLE: 'middle',
  EXTRA_LEGROOM: 'extra legroom',
};

@Component({
  selector: 'app-seat-step',
  imports: [MoneyPipe, NgTemplateOutlet],
  templateUrl: './seat-step.html',
  styleUrl: './seat-step.scss',
})
export class SeatStep {
  protected readonly store = inject(CheckoutStore);
  protected readonly layout = this.store.seatLayout;
  protected readonly passengers = computed(() =>
    Array.from({ length: this.store.passengerCount() }, (_, i) => ({
      index: i,
      name: this.store.passengerName(i),
      seat: this.store.selectedSeats()[i],
    })),
  );

  protected isFree(seat: SeatInstance): boolean {
    return isSelectable(seat);
  }

  protected typeLabel(seat: SeatInstance): string {
    const type = seat.seat?.seatType ?? seat.seatType ?? '';
    return SEAT_TYPE_LABELS[type] ?? '';
  }

  protected isExtraLegroom(seat: SeatInstance): boolean {
    return (
      !!seat.seat?.hasExtraLegroom ||
      seat.seat?.seatType === 'EXTRA_LEGROOM' ||
      !!seat.seat?.isEmergencyExit
    );
  }

  /** Full sentence for screen readers, e.g. "Seat 12A, window, ₹350, available". */
  protected describe(seat: SeatInstance, price: string): string {
    const occupant = this.store.occupantOf(seat.id);
    const parts = [`Seat ${seat.seatNumber}`, this.typeLabel(seat), price];
    if (occupant !== -1) {
      parts.push(`selected for ${this.store.passengerName(occupant)}`);
    } else {
      parts.push(this.isFree(seat) ? 'available' : 'not available');
    }
    return parts.filter(Boolean).join(', ');
  }
}
