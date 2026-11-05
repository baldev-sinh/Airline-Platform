-- Baseline schema for flight-ops-service.
-- Generated from the JPA model against MySQL 8.4, then reviewed. Never edit an applied migration:
-- add V2__..., V3__... for every later change.
-- Enum columns are VARCHAR without CHECK lists so new enum values need no schema change.

create table flight (
    aircraft_id bigint not null,
    airline_id bigint not null,
    arrival_airport_id bigint not null,
    created_at datetime(6) not null,
    departure_airport_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    status varchar(20) not null,
    flight_number varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table flight_instance (
    available_seats integer not null,
    is_active bit,
    max_advance_booking_days integer,
    min_advance_booking_days integer,
    total_seats integer not null,
    airline_id bigint,
    arrival_airport_id bigint not null,
    arrival_date_time datetime(6),
    departure_airport_id bigint not null,
    departure_date_time datetime(6),
    flight_id bigint,
    id bigint not null auto_increment,
    schedule_id bigint not null,
    status varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table flight_schedule (
    arrival_time time(0) not null,
    departure_time time(0) not null,
    end_date date not null,
    is_active bit,
    start_date date not null,
    arrival_airport_id bigint not null,
    departure_airport_id bigint not null,
    flight_id bigint,
    id bigint not null auto_increment,
    primary key (id)
) engine=InnoDB;

create table flight_schedule_operating_days (
    flight_schedule_id bigint not null,
    operating_days varchar(255)
) engine=InnoDB;

create table processed_inventory_event (
    processed_at datetime(6) not null,
    event_key varchar(64) not null,
    primary key (event_key)
) engine=InnoDB;

alter table flight 
   add constraint UKaucisqx7arn3fi6eyjmsvqdb3 unique (flight_number);

alter table flight_instance 
   add constraint FK8ed61pohjogri1fix0gvbv5uq 
   foreign key (flight_id) 
   references flight (id);

alter table flight_schedule 
   add constraint FK1voe8ky2xg3fw8x5jiwduigja 
   foreign key (flight_id) 
   references flight (id);

alter table flight_schedule_operating_days 
   add constraint FKnwtciphqido8b08cutk99tdoh 
   foreign key (flight_schedule_id) 
   references flight_schedule (id);
