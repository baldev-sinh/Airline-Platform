-- Baseline schema for location-service.
-- Generated from the JPA model against MySQL 8.4, then reviewed. Never edit an applied migration:
-- add V2__..., V3__... for every later change.
-- Enum columns are VARCHAR without CHECK lists so new enum values need no schema change.

create table airport (
    iata_code varchar(3) not null,
    latitude float(53),
    longitude float(53),
    city_id bigint,
    id bigint not null auto_increment,
    time_zone_id varchar(50),
    name varchar(255) not null,
    postal_code varchar(255),
    street varchar(255),
    primary key (id)
) engine=InnoDB;

create table city (
    id bigint not null auto_increment,
    region_code varchar(10),
    time_zone_id varchar(50),
    city_code varchar(255) not null,
    country_code varchar(255) not null,
    country_name varchar(255) not null,
    name varchar(255) not null,
    primary key (id)
) engine=InnoDB;

alter table airport 
   add constraint UKj4nn5inm55h1u68kgmvvlqjyu unique (iata_code);

alter table airport 
   add constraint FKf583ieaw0ttnwklqy222tfru0 
   foreign key (city_id) 
   references city (id);
