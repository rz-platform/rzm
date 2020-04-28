# --- !Ups

create table account (
  id                        bigserial primary key,
  username                  varchar(36) not null UNIQUE,
  password                  varchar(255) not null,
  full_name                 varchar(36) not null default '',
  is_admin                  boolean not null default false,
  email                     varchar(50) not null UNIQUE,
  created_at                timestamp not null default current_timestamp,
  has_picture               boolean not null default false,
  description               varchar(255) not null default ''
);

create table repository (
 id                  bigserial primary key,
 owner_id            bigint not null REFERENCES account(id),
 name                varchar(36) not null,
 is_private          boolean not null default true,
 description         varchar(255) not null default '',
 default_branch      varchar(255) not null,
 created_at          timestamp with time zone not null default current_timestamp,
 updated_at          timestamp with time zone not null default current_timestamp,
 unique (owner_id, name)
);

create table collaborator (
 id               bigserial primary key,
 user_id          bigint not null REFERENCES account(id),
 repository_id    bigint not null REFERENCES repository(id),
 role             smallint not null,
 unique (user_id, repository_id)
);

CREATE UNIQUE INDEX account_usernamex ON account (username);
CREATE UNIQUE INDEX account_mailx ON account (email);

CREATE INDEX repository_idx ON repository (name);

# --- !Downs

drop table if exists collaborator, account, repository;

drop index if exists account_usernamex;
drop index if exists account_mailx;
drop index if exists repository_idx;