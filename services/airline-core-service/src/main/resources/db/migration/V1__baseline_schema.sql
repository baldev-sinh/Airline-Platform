-- Baseline schema for airline-core-service.
-- Generated from the JPA model against MySQL 8.4, then reviewed. Never edit an applied migration:
-- add V2__..., V3__... for every later change.
-- Enum columns are VARCHAR without CHECK lists so new enum values need no schema change.

create table aircraft (
    business_seats integer,
    cruising_speed_kmh integer,
    economy_seats integer,
    first_class_seats integer,
    is_available bit,
    max_altitude_ft integer,
    next_maintenance_date date,
    premium_economy_seats integer,
    range_km integer,
    registration_date date,
    seating_capacity integer not null,
    year_of_manufacture integer,
    airline_id bigint,
    created_at datetime(6) not null,
    current_airport_id bigint,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    status varchar(20) not null,
    manufacturer varchar(50) not null,
    code varchar(255) not null,
    model varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table airline (
    created_at datetime(6) not null,
    headquarters_city_id bigint,
    id bigint not null auto_increment,
    owner_id bigint not null,
    updated_at datetime(6) not null,
    updated_by_id bigint,
    alias varchar(255),
    alliance varchar(255),
    email varchar(255),
    hours varchar(255),
    iata_code varchar(255) not null,
    icao_code varchar(255) not null,
    logo_url varchar(255),
    name varchar(255) not null,
    phone varchar(255),
    status varchar(255),
    website varchar(255),
    primary key (id)
) engine=InnoDB;

alter table aircraft 
   add constraint UKrob2mayh9oayxjnn573i7kooq unique (code);

alter table airline 
   add constraint UKl8wcl84r4vu02vhu9vxt8atah unique (iata_code);

alter table airline 
   add constraint UKb2upo3vouxu1bbmagmy6fwt25 unique (icao_code);

alter table aircraft 
   add constraint FKjlf3erek6x7moh3fya657mukb 
   foreign key (airline_id) 
   references airline (id);
