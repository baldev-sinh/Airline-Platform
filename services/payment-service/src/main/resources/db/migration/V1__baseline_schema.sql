-- Baseline schema for payment-service.
-- Generated from the JPA model against MySQL 8.4, then reviewed. Never edit an applied migration:
-- add V2__..., V3__... for every later change.
-- Enum columns are VARCHAR without CHECK lists so new enum values need no schema change.

create table payment (
    amount decimal(12,2) not null,
    currency varchar(3) not null,
    booking_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    paid_at datetime(6),
    updated_at datetime(6) not null,
    user_id bigint not null,
    version bigint not null,
    provider varchar(20) not null,
    status varchar(20) not null,
    provider_order_id varchar(64),
    provider_payment_id varchar(64),
    refund_id varchar(64),
    transaction_id varchar(64) not null,
    failure_reason varchar(500),
    checkout_url varchar(255),
    primary key (id)
) engine=InnoDB;

create index idx_payment_booking 
   on payment (booking_id);

alter table payment 
   add constraint uk_payment_transaction_id unique (transaction_id);

alter table payment 
   add constraint uk_payment_provider_payment_id unique (provider_payment_id);

alter table payment 
   add constraint uk_payment_provider_order_id unique (provider_order_id);
