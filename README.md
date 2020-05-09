Razam [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/razam/razam/blob/master/LICENSE) [![Build Status](https://travis-ci.org/razamgit/razam.svg?branch=master)](https://travis-ci.org/razamgit/razam)
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Frazamgit%2Frazam.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2Frazamgit%2Frazam?ref=badge_shield)
=====

Razam is web-based Git repository hosting (much like GitHub) for writing scientific papers in a browser and full integration with TeX.

Build a TeX template, write an article in an online editor, invite collaborators. You can do everything without leaving your browser.

Or use any Git-client and write an article in your favorite editor.

Fully-functional Git Server with authentication against Razam implemented in [Razam Git Server](https://github.com/razamgit/gitserver).

## Development

This is [Play](https://playframework.com/documentation/latest/Home) application that uses Scala and communicates with PostgreSQL using [Anorm](https://playframework.github.io/anorm/).

Front-end is written in VanillaJS and packaged using Parcel.

---

Razam requires Postgres database. You can find default credentials in [application.conf](https://github.com/razamgit/razam/blob/master/conf/application.conf) (db.default section). Snippet for creation:

```
create database razam;
create user razam with encrypted password 'razam';
grant all privileges on database razam to razam;
```

Build front-end:

```
cd public && npm install && npm run build
```

Run app in live-reload mode:

```
sbt run
```

Open http://localhost:9000

### Run tests

```
sbt test
```

### Production build

```
cd public && npm run build
cd .. &&  sbt dist
target/universal/stage/bin/razam  -Dplay.evolutions.db.default.autoApply=true  -Dplay.http.secret.key=secret
```

## Copyright

Copyright 2020 Eugene Bosiakov (@bosiakov). 

Package *git* contains parts of code inherited from [GitBucket](https://github.com/gitbucket/gitbucket) project.


## License
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Frazamgit%2Frazam.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Frazamgit%2Frazam?ref=badge_large)