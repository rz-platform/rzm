# RZM [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/rz-platform/rzm/blob/master/LICENSE) [![Build Status](https://github.com/rz-platform/rzm/workflows/test_backend/badge.svg)](https://github.com/rz-platform/rzm/actions)


RZM is a TeX publishing platform.

* Blazingly fast UI without JS-frameworks

* Full document history

* Git as storage

Fully-functional Git Server with authentication against RZM implemented in [rz-gitserver](https://github.com/rz-platform/rz-gitserver).

## Development

This is Scala 2.13 application that uses Play and communicates with PostgreSQL using Anorm (https://playframework.github.io/anorm/).

The front-end is implemented using Twirl template engine (https://github.com/playframework/twirl). CSS styles and separate per-page JS bundles are stored in [./frontend](./frontend).

### Backend development


* JDK 11+ required;
* https://scala-sbt.org

Run app in live-reload mode (from repository root):

```
sbt run
```

Code format:

```
sbt scalafmt
```

Testing suite:

```
sbt test
```

RZM requires Postgres database. You can find default credentials in [application.conf](https://github.com/rz-platform/rzm/blob/master/conf/application.conf) (db.default section).


### Frontend development

Frontend guide can be found here: [./frontend/Readme.md](./frontend/Readme.md).

In order to make navigation blazingly fast we use a turbolinks fork ([./frontend/turbolinks](./frontend/turbolinks/README.md)).

* Node.js 12 or higher;
* NPM 6+;

```
cd frontend && npm install
npm run watch
```

Formatter and linter:

```
npm run prettier && npm run check
```


### Production build

```
cd frontend && npm run build && cd -
sbt dist
target/universal/stage/bin/rzm  -Dplay.evolutions.db.default.autoApply=true  -Dplay.http.secret.key=secret
```

## Copyright

Copyright 2020 Eugene Bosiakov (@bosiakov).

RZM contains parts of code of the following projects:

* [GitBucket](https://github.com/gitbucket/gitbucket/blob/master/LICENSE), Apache 2 Licence
* [t2v/play2-auth](https://github.com/t2v/play2-auth/blob/master/LICENSE), Apache 2 Licence
* [Turbolinks](./frontend/turbolinks/README.md), MIT License

Icons made by <a href="https://www.flaticon.com/authors/becris" title="Becris">Becris</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a>