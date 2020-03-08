# --- Sample dataset

# --- !Ups

insert into account(userName, fullName, mailAddress, password, registeredDate) values
                   ('admin', 'Admin', 'a@a.com', '$2a$10$u1iJ7joxRhUAnzkk3SzcOeNikUG4uRy4RMX2BHi5fAb3TD2ukPrUK', '2019-03-07');

# --- !Downs

TRUNCATE TABLE collaborator, account, repository RESTART IDENTITY;