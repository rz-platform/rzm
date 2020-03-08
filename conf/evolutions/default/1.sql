# --- First database schema

# --- !Ups

create table account (
  id                        bigserial primary key,
  username                  varchar(255) not null UNIQUE,
  password                  varchar(255) not null,
  fullname                  varchar(255) default '',
  isAdmin                   boolean not null default false,
  isRemoved                 boolean not null default false,
  mailAddress               varchar(255) not null UNIQUE,
  registeredDate            timestamp not null default current_timestamp,
  image                     varchar(255) not null default '',
  description               varchar(255) not null default ''
);

create table repository (
 id bigserial primary key,
 name varchar(255) not null,
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

# --- !Downs

drop table if exists collaborator, account, repository;
