# --- Sample dataset

# --- !Ups

insert into account(username, full_name, email, password, created_at) values
                   ('admin', 'Admin', 'a@a.com', '$2a$10$u1iJ7joxRhUAnzkk3SzcOeNikUG4uRy4RMX2BHi5fAb3TD2ukPrUK', '2019-03-07');

# --- !Downs

TRUNCATE TABLE collaborator, account, repository, ssh_key RESTART IDENTITY;