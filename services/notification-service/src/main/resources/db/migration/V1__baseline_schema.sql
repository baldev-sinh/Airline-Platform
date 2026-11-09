-- Baseline schema for notification-service. Never edit an applied migration: add V2__..., V3__... instead.
-- Enum columns are VARCHAR without CHECK lists so new enum values need no schema change.

create table notification (
    id bigint not null auto_increment,
    event_key varchar(150) not null,
    channel varchar(16) not null,
    type varchar(50) not null,
    user_id bigint,
    recipient varchar(255),
    subject varchar(200) not null,
    summary varchar(1000) not null,
    body mediumtext,
    status varchar(16) not null,
    attempts integer not null,
    next_attempt_at datetime(6),
    last_error varchar(500),
    sent_at datetime(6),
    read_at datetime(6),
    version bigint not null,
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

-- Idempotency: one notification per event and channel, whatever Kafka redelivers.
alter table notification add constraint uk_notification_event_channel unique (event_key, channel);

-- Inbox listing, and the retry job's "due e-mails" scan.
create index idx_notification_user_created on notification (user_id, created_at);
create index idx_notification_status_next on notification (status, next_attempt_at);
