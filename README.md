# TexFlow [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/texflow/texflow/blob/master/LICENSE) [![Build Status](https://github.com/texflow/texflow/workflows/test_backend/badge.svg)](https://github.com/texflow/texflow/actions)


TexFlow is a TeX publishing platform.

* Blazingly fast UI without JS-frameworks

* Full document history

* Git as storage

Fully-functional Git Server with TexFlow-authentication implemented in [tf-gitserver](https://github.com/texflow/tf-gitserver).

## Development

TexFlow uses Git on top of the file system as a data master. User accounts and repositories meta-data persists in Redis. This is Scala 2.13 application.

The front-end is implemented using Twirl template engine (https://github.com/playframework/twirl). CSS styles and separate per-page JS bundles are stored in [./frontend](./frontend).

### Backend development

* JDK 11+ required;
* https://scala-sbt.org
* libsodium
* Redis 5+

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

### Frontend development

Frontend guide can be found here: [./docs/frontend.md](./docs/frontend.md).

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
target/universal/stage/bin/texflow -Dplay.http.secret.key=secret
```

## Copyright

Copyright 2021 Eugene Bosiakov (@bosiakov).

TexFlow contains parts of code inherited from [GitBucket](https://github.com/gitbucket/gitbucket) project.

[Turbolinks license](./frontend/turbolinks/README.md).

iA-Fonts [license](./public/fonts/LICENSE.md).

Icons made by <a href="https://www.flaticon.com/authors/becris" title="Becris">Becris</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a>