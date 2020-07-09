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

create table ssh_key (
  id                        serial primary key,
  account_id                bigint not null REFERENCES account(id),
  public_key                varchar not null,
  created_at                timestamp with time zone not null default now(),
  check (public_key  <> '')
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

CREATE INDEX ssh_key_accountx ON ssh_key (account_id);

CREATE INDEX collaborator_user_idx ON collaborator (user_id);

CREATE INDEX repository_namex ON repository (name);
CREATE INDEX repository_owner_idx ON repository (owner_id);

# --- !Downs

drop table if exists collaborator, account, repository, ssh_key;

drop index if exists account_usernamex;
drop index if exists account_mailx;

drop index if exists collaborator_user_idx;
drop index if exists ssh_key_accountx;

drop index if exists repository_namex;
drop index if exists repository_owner_idx;
