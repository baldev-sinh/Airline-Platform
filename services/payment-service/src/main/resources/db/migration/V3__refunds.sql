-- Refunds of captured payments (customer cancellations, payments that arrived after a booking was cancelled).
create table refund (
    id bigint not null auto_increment,
    refund_key varchar(100) not null,
    payment_id bigint not null,
    booking_id bigint not null,
    user_id bigint not null,
    amount decimal(12,2) not null,
    currency varchar(3) not null,
    status varchar(20) not null,
    provider_refund_id varchar(64),
    reason varchar(255),
    failure_reason varchar(500),
    attempts integer not null,
    processed_at datetime(6),
    version bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

create index idx_refund_payment on refund (payment_id);
create index idx_refund_status_updated on refund (status, updated_at);

alter table refund add constraint uk_refund_key unique (refund_key);
alter table refund add constraint uk_refund_provider_refund_id unique (provider_refund_id);
