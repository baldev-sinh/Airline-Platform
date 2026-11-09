-- Refund state of a cancelled ticketed booking (refund workflow: booking -> refund.requested -> payment-service).
alter table booking
    add column refund_status varchar(20) not null default 'NONE',
    add column refund_amount decimal(12,2),
    add column refund_reference varchar(64),
    add column refunded_at datetime(6);
