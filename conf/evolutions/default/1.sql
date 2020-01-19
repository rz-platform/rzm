# --- First database schema

# --- !Ups

create table account (
  id                        bigserial primary key,
  username                  varchar(255) not null UNIQUE,
  password                  varchar(255) not null,
  fullname                  varchar(255),
  isAdmin                   boolean not null,
  isRemoved                 boolean not null,
  mailAddress               varchar(255) not null UNIQUE,
  registeredDate            timestamp,
  image                     varchar(255),
  description               varchar(255)
);

create table repository (
 id bigserial primary key,
 name varchar(255) not null,
 isPrivate boolean not null,
 description varchar(255),
 defaultBranch varchar(255) not null,
 registeredDate  timestamp not null,
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
