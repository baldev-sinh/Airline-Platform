-- Baseline schema for ancillary-service.
-- Generated from the JPA model against MySQL 8.4, then reviewed. Never edit an applied migration:
-- add V2__..., V3__... for every later change.
-- Enum columns are VARCHAR without CHECK lists so new enum values need no schema change.

create table ancillary (
    display_order integer,
    airline_id bigint,
    id bigint not null auto_increment,
    description varchar(255),
    metadata varchar(255),
    name varchar(255) not null,
    rfisc varchar(255),
    sub_type varchar(255),
    type varchar(255),
    primary key (id)
) engine=InnoDB;

create table flight_cabin_ancillary (
    available bit,
    included_in_fare bit,
    max_quantity integer,
    price decimal(38,2),
    ancillary_id bigint,
    cabin_class_id bigint not null,
    flight_id bigint not null,
    id bigint not null auto_increment,
    primary key (id)
) engine=InnoDB;

create table flight_meal (
    available bit,
    display_order integer,
    price decimal(38,2),
    flight_id bigint not null,
    id bigint not null auto_increment,
    meal_id bigint,
    primary key (id)
) engine=InnoDB;

create table insurance_coverage (
    active bit not null,
    coverage_amount decimal(38,2) not null,
    display_order integer,
    is_flat bit not null,
    ancillary_id bigint,
    id bigint not null auto_increment,
    coverage_type varchar(30) not null,
    name varchar(200) not null,
    description varchar(1000),
    claim_condition varchar(255),
    emergency_contact varchar(255),
    primary key (id)
) engine=InnoDB;

create table meal (
    advance_booking_hours integer,
    available bit,
    display_order integer,
    requires_advance_booking bit,
    airline_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    code varchar(255) not null,
    dietary_restriction varchar(255),
    image_url varchar(255),
    ingredients varchar(255),
    meal_type varchar(255) not null,
    name varchar(255) not null,
    primary key (id)
) engine=InnoDB;

alter table flight_cabin_ancillary 
   add constraint FKec50f3ri3sh8uifwawu7rdskh 
   foreign key (ancillary_id) 
   references ancillary (id);

alter table flight_meal 
   add constraint FKq50vd27syv2fosvdnu30kmtn9 
   foreign key (meal_id) 
   references meal (id);

alter table insurance_coverage 
   add constraint FKojwp68ddwo0f7qwru3s4hqrc6 
   foreign key (ancillary_id) 
   references ancillary (id);
