-- Transactional outbox (common-lib OutboxPublisher / OutboxRelay).
-- Events are inserted in the same transaction as the state change they announce and relayed to Kafka after
-- commit, so a crash can no longer lose an event between "commit" and "send".
create table outbox_event (
    id bigint not null auto_increment,
    topic varchar(200) not null,
    message_key varchar(200),
    payload_type varchar(255) not null,
    payload mediumtext not null,
    trace_headers varchar(1000),
    -- PENDING -> PUBLISHED, or FAILED when it can never be published (needs a human)
    status varchar(16) not null,
    created_at datetime(6) not null,
    published_at datetime(6),
    attempts int not null default 0,
    last_error varchar(1000),
    primary key (id)
) engine=InnoDB;

-- The relay claims the oldest PENDING rows; cleanup deletes old PUBLISHED rows.
create index idx_outbox_status_id on outbox_event (status, id);
