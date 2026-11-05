-- Baseline schema for seat-service.
-- Generated from the JPA model against MySQL 8.4, then reviewed. Never edit an applied migration:
-- add V2__..., V3__... for every later change.
-- Enum columns are VARCHAR without CHECK lists so new enum values need no schema change.

create table cabin_class (
    display_order integer not null,
    is_active bit not null,
    is_bookable bit not null,
    typical_seat_pitch integer,
    typical_seat_width integer,
    aircraft_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    code varchar(255) not null,
    description varchar(255),
    name varchar(255) not null,
    seat_type varchar(255),
    primary key (id)
) engine=InnoDB;

create table flight_instance_cabin (
    booked_seats integer,
    total_seats integer not null,
    cabin_class_id bigint,
    flight_instance_id bigint not null,
    id bigint not null auto_increment,
    primary key (id)
) engine=InnoDB;

create table seat (
    base_price decimal(38,2),
    column_letter varchar(1),
    has_extra_legroom bit,
    has_extra_width bit,
    has_power_outlet bit,
    has_tv_screen bit,
    is_active bit,
    is_available bit,
    is_blocked bit,
    is_emergency_exist bit,
    premium_super_charge decimal(38,2),
    seat_pitch integer,
    seat_row integer not null,
    seat_width integer,
    cabin_class_id bigint,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    seat_map_id bigint,
    updated_at datetime(6) not null,
    version bigint not null,
    seat_type varchar(20),
    created_by varchar(255),
    seat_number varchar(255) not null,
    updated_by varchar(255),
    primary key (id)
) engine=InnoDB;

create table seat_instance (
    fare decimal(12,2),
    premium_supercharge decimal(12,2),
    created_at datetime(6),
    flight_id bigint not null,
    flight_instance_cabin_id bigint,
    flight_instance_id bigint not null,
    flight_schedule_id bigint,
    held_by_booking_id bigint,
    hold_expires_at datetime(6),
    id bigint not null auto_increment,
    seat_id bigint,
    updated_at datetime(6),
    version bigint not null,
    status varchar(20) not null,
    primary key (id)
) engine=InnoDB;

create table seat_map (
    left_seats_per_row integer not null,
    right_seats_per_row integer not null,
    total_rows integer not null,
    airline_id bigint not null,
    cabin_class_id bigint,
    id bigint not null auto_increment,
    name varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create index idx_seat_instance_flight_instance 
   on seat_instance (flight_instance_id);

create index idx_seat_instance_held_by_booking 
   on seat_instance (held_by_booking_id);

create index idx_seat_instance_status_expiry 
   on seat_instance (status, hold_expires_at);

alter table seat_map 
   add constraint UKfhjin5he9c9ns244aljpj8801 unique (cabin_class_id);

alter table flight_instance_cabin 
   add constraint FKangb39woge1xoxt6aeye2un52 
   foreign key (cabin_class_id) 
   references cabin_class (id);

alter table seat 
   add constraint FKflc1d4ly7mfi8jpdsom6npuja 
   foreign key (cabin_class_id) 
   references cabin_class (id);

alter table seat 
   add constraint FKkflenuc0rq5lcc6qt45v0auwy 
   foreign key (seat_map_id) 
   references seat_map (id);

alter table seat_instance 
   add constraint FKjv5p0yhiiow7x1mek7vk71513 
   foreign key (flight_instance_cabin_id) 
   references flight_instance_cabin (id);

alter table seat_instance 
   add constraint FKo25gohad9bdfemi3h1f0duisd 
   foreign key (seat_id) 
   references seat (id);

alter table seat_map 
   add constraint FKcd3k4r047ekr92w6a2rpvf62p 
   foreign key (cabin_class_id) 
   references cabin_class (id);
