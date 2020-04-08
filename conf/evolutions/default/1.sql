# --- !Ups

create table account (
  id                        bigserial primary key,
  username                  varchar(25) not null UNIQUE,
  password                  varchar(255) not null,
  fullname                  varchar(25) not null default '',
  isAdmin                   boolean not null default false,
  isRemoved                 boolean not null default false,
  mailAddress               varchar(50) not null UNIQUE,
  registeredDate            timestamp not null default current_timestamp,
  image                     varchar(255) not null default '',
  description               varchar(255) not null default ''
);

create table repository (
 id bigserial primary key,
 name varchar(25) not null,
 isPrivate boolean not null,
 description varchar(255) not null default '',
 defaultBranch varchar(255) not null,
 registeredDate  timestamp not null default current_timestamp,
 lastActivityDate timestamp not null
);

create table collaborator (
 id bigserial primary key,
 userId bigint not null REFERENCES account(id),
 repositoryId bigint not null REFERENCES repository(id),
 role smallint not null,
 unique (userId, repositoryId)
);

CREATE UNIQUE INDEX account_usernamex ON account (username);
CREATE UNIQUE INDEX account_mailx ON account (mailAddress);

CREATE INDEX repository_idx ON repository (name);

# --- !Downs

drop table if exists collaborator, account, repository;

drop index if exists account_usernamex;
drop index if exists account_mailx;
drop index if exists repository_idx;