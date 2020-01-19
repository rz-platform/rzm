# --- Sample dataset

# --- !Ups

insert into account(userName, fullName, mailAddress, password, isAdmin, registeredDate, image, isRemoved, description) values
                   ('admin',   'Admin',  'a@a.com',   '$2a$10$u1iJ7joxRhUAnzkk3SzcOeNikUG4uRy4RMX2BHi5fAb3TD2ukPrUK',     true,    '2011-10-14',   null,   false,    null);

# --- !Downs

delete from collaborator, account, repository;