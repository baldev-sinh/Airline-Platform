-- Baseline schema for pricing-service.
-- Generated from the JPA model against MySQL 8.4, then reviewed. Never edit an applied migration:
-- add V2__..., V3__... for every later change.
-- Enum columns are VARCHAR without CHECK lists so new enum values need no schema change.

create table baggage_policy (
    cabin_baggage_max_dimension integer,
    cabin_baggage_max_weight float(53),
    cabin_baggage_pieces integer,
    cabin_baggage_weight_per_piece float(53),
    check_in_baggage_max_weight float(53),
    check_in_baggage_pieces integer,
    check_in_baggage_weight_per_piece float(53),
    extra_baggage_allowance bit,
    free_checked_bags_allowance integer,
    priority_baggage bit,
    airline_id bigint,
    created_at datetime(6) not null,
    fare_id bigint,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    description varchar(255),
    name varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table fare (
    advance_seat_selection bit,
    airline_fees decimal(38,2),
    airport_transfer bit not null,
    base_fare decimal(38,2) not null,
    complimentary_beverages bit not null,
    complimentary_meals bit not null,
    current_price decimal(38,2) not null,
    extra_seat_space bit,
    fast_track_security bit not null,
    free_date_change bit,
    full_refund bit not null,
    guaranteed_seat_together bit,
    in_flight_entertainment bit not null,
    in_flight_internet bit not null,
    lounge_access bit not null,
    partial_refund bit not null,
    preferred_seat_choice bit,
    premium_meal_choice bit not null,
    priority_boarding bit not null,
    priority_checkin bit not null,
    rbd_code varchar(1) not null,
    taxes_and_fees decimal(38,2),
    cabin_class_id bigint not null,
    created_at datetime(6) not null,
    flight_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    cabin_class varchar(255),
    fare_label varchar(255),
    name varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table fare_rules (
    cancellation_fee decimal(38,2),
    change_deadline_hours integer,
    change_fee decimal(38,2),
    is_changeable bit,
    is_refundable bit,
    refund_deadline_days integer,
    airline_id bigint,
    created_at datetime(6) not null,
    fare_id bigint,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    rule_name varchar(255),
    primary key (id)
) engine=InnoDB;

alter table baggage_policy 
   add constraint UKsg6k1dds7qvin5nrjgpegblhv unique (fare_id);

alter table fare_rules 
   add constraint UKdo45693odksp1byrcdc759uah unique (fare_id);

alter table baggage_policy 
   add constraint FKjfdm4uwes9u1xdg40o46w37wn 
   foreign key (fare_id) 
   references fare (id);

alter table fare_rules 
   add constraint FKdw82coixps0jl4b1hmqrpeyvt 
   foreign key (fare_id) 
   references fare (id);
