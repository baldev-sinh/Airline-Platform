-- Baseline schema for booking-service.
-- Generated from the JPA model against MySQL 8.4, then reviewed. Never edit an applied migration:
-- add V2__..., V3__... for every later change.
-- Enum columns are VARCHAR without CHECK lists so new enum values need no schema change.

create table booking (
    ancillary_amount decimal(12,2),
    arrival_airport_code varchar(3),
    currency varchar(3),
    departure_airport_code varchar(3),
    fare_amount decimal(12,2),
    flexible_ticket bit not null,
    meal_amount decimal(12,2),
    seat_amount decimal(12,2),
    ticket_issued bit not null,
    total_amount decimal(12,2),
    booking_reference varchar(6) not null,
    airline_id bigint not null,
    arrival_date_time datetime(6),
    booking_date datetime(6) not null,
    cancelled_at datetime(6),
    departure_date_time datetime(6),
    fare_id bigint not null,
    flight_id bigint not null,
    flight_instance_id bigint not null,
    id bigint not null auto_increment,
    last_modified datetime(6) not null,
    payment_id bigint,
    ticket_time_limit datetime(6),
    user_id bigint not null,
    version bigint not null,
    flight_number varchar(10),
    cabin_class varchar(20) not null,
    status varchar(20) not null,
    airline_name varchar(100),
    arrival_city varchar(100),
    departure_city varchar(100),
    fare_name varchar(100),
    cancellation_reason varchar(255),
    email varchar(255),
    phone varchar(255),
    primary key (id)
) engine=InnoDB;

create table booking_ancillary_ids (
    ancillary_id bigint not null,
    booking_id bigint not null
) engine=InnoDB;

create table booking_meal_ids (
    booking_id bigint not null,
    meal_id bigint not null
) engine=InnoDB;

create table booking_seat_instance_ids (
    booking_id bigint not null,
    seat_instance_id bigint not null
) engine=InnoDB;

create table passenger (
    date_of_birth date not null,
    is_active bit,
    booking_id bigint not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    primary_user_id bigint,
    seat_instance_id bigint,
    updated_at datetime(6),
    version bigint not null,
    gender varchar(20) not null,
    phone varchar(20),
    nationality varchar(60),
    first_name varchar(100) not null,
    last_name varchar(100) not null,
    email varchar(255),
    primary key (id)
) engine=InnoDB;

create table ticket (
    booking_id bigint not null,
    id bigint not null auto_increment,
    issued_at datetime(6),
    passenger_id bigint not null,
    ticket_number varchar(13) not null,
    status varchar(20) not null,
    primary key (id)
) engine=InnoDB;

create index idx_booking_user 
   on booking (user_id);

create index idx_booking_airline_status 
   on booking (airline_id, status);

create index idx_booking_flight_instance 
   on booking (flight_instance_id);

alter table booking 
   add constraint uk_booking_reference unique (booking_reference);

alter table ticket 
   add constraint uk_ticket_number unique (ticket_number);

alter table booking_ancillary_ids 
   add constraint FK9ef037jenayg4drhc18koxgqq 
   foreign key (booking_id) 
   references booking (id);

alter table booking_meal_ids 
   add constraint FKh42x63499w6868cmhhck40378 
   foreign key (booking_id) 
   references booking (id);

alter table booking_seat_instance_ids 
   add constraint FK5ikhpxv0wd0q4iwpmmpn741jj 
   foreign key (booking_id) 
   references booking (id);

alter table passenger 
   add constraint FKtco0omesfld1qi5sw76eomvt4 
   foreign key (booking_id) 
   references booking (id);

alter table ticket 
   add constraint FKrg7x158t96nucwslhq2bad6qm 
   foreign key (booking_id) 
   references booking (id);

alter table ticket 
   add constraint FK3y0nr3g8pk6ygc173mjaaumgh 
   foreign key (passenger_id) 
   references passenger (id);
