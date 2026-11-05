-- Baseline schema for user-service.
-- Generated from the JPA model against MySQL 8.4, then reviewed. Never edit an applied migration:
-- add V2__..., V3__... for every later change.
-- Enum columns are VARCHAR without CHECK lists so new enum values need no schema change.

create table users (
    enabled bit not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    last_login datetime(6),
    updated_at datetime(6) not null,
    phone varchar(20),
    role varchar(30) not null,
    full_name varchar(100) not null,
    password varchar(100) not null,
    email varchar(255) not null,
    primary key (id)
) engine=InnoDB;

alter table users 
   add constraint uk_users_email unique (email);
