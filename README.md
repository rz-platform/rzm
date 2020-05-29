Razam [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/razam/razam/blob/master/LICENSE) [![Build Status](https://travis-ci.org/razamgit/razam.svg?branch=master)](https://travis-ci.org/razamgit/razam)
=====

Razam is web-based Git repository hosting (much like GitHub) for writing scientific papers in a browser and full integration with TeX.

Build a TeX template, write an article in an online editor, invite collaborators. You can do everything without leaving your browser.

Or use any Git-client and write an article in your favorite editor.

Fully-functional Git Server with authentication against Razam implemented in [Razam Git Server](https://github.com/razamgit/gitserver).

## Development

This is Scala application that uses [Play](https://playframework.com/documentation/latest/Home) and communicates with PostgreSQL using [Anorm](https://playframework.github.io/anorm/).

Front-end is written in VanillaJS and packaged using Parcel. Stylesheets are written in SCSS and following [SASS Guidelines](https://sass-guidelin.es/). HTML [views](https://github.com/razamgit/razam/tree/master/app/views) are following [CodeGuide](https://codeguide.co/).

---

Razam requires Postgres database. You can find default credentials in [application.conf](https://github.com/razamgit/razam/blob/master/conf/application.conf) (db.default section). Snippet for creation:

```
create database razam;
create user razam with encrypted password 'razam';
grant all privileges on database razam to razam;
```

Build front-end:

```
cd frontend && npm install && npm run build
```

Run app in live-reload mode (from repository root):

```
sbt run
```

Open http://localhost:9000

### Run tests

```
sbt test
```

### Format code

```
sbt scalafmt
```


### Production build

```
cd frontend && npm run build
cd .. &&  sbt dist
target/universal/stage/bin/razam  -Dplay.evolutions.db.default.autoApply=true  -Dplay.http.secret.key=secret
```

## Copyright

Copyright 2020 Eugene Bosiakov (@bosiakov).

Project contains parts of code inherited from [GitBucket](https://github.com/gitbucket/gitbucket) project.
