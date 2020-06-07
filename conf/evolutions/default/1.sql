# --- !Ups

create table account (
  id                        bigserial primary key,
  username                  varchar not null UNIQUE,
  password                  varchar not null,
  full_name                 varchar,
  is_admin                  boolean not null default false,
  email                     varchar not null UNIQUE,
  created_at                timestamp with time zone not null default now(),
  has_picture               boolean not null default false,
  description               varchar not null default '',
  check (username <> ''),
  check (password <> ''),
  check (email <> '')
);

create table repository (
 id                  bigserial primary key,
 owner_id            bigint not null REFERENCES account(id),
 name                varchar not null,
 is_private          boolean not null default true,
 description         varchar not null default '',
 default_branch      varchar not null,
 created_at          timestamp with time zone not null default now(),
 unique (owner_id, name),
 check (name <> ''),
 check (default_branch <> '')
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
CREATE INDEX repository_owner_idx ON repository (owner_id);

# --- !Downs

drop table if exists collaborator, account, repository;

drop index if exists account_usernamex;
drop index if exists account_mailx;

drop index if exists repository_idx;
drop index if exists repository_owner_idx;
