-- Refresh tokens (hashed), rotated on every use; see RefreshToken.
create table refresh_token (
    id bigint not null auto_increment,
    user_id bigint not null,
    token_hash char(64) not null,
    family_id char(36) not null,
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    revoked_at datetime(6),
    replaced_by_id bigint,
    user_agent varchar(255),
    primary key (id)
) engine=InnoDB;

alter table refresh_token add constraint uk_refresh_token_hash unique (token_hash);
create index idx_refresh_token_family on refresh_token (family_id);
create index idx_refresh_token_user on refresh_token (user_id);
create index idx_refresh_token_expires on refresh_token (expires_at);
