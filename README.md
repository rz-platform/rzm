Razam [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/razam/razam/blob/master/LICENSE) [![Build Status](https://travis-ci.org/razamgit/razam.svg?branch=master)](https://travis-ci.org/razamgit/razam)
=====

*RZ* (Razam) is web-based Git repository hosting (much like GitHub) for writing science papers in a browser and full integration with TeX.

Build a TeX template, write an article in an online editor, invite collaborators. You can do everything without leaving your browser.

Or use any Git-client and write an article in your favorite editor.

This is [Play](https://playframework.com/documentation/latest/Home) application that uses Scala on the front end, and communicates with PostgreSQL using [Anorm](https://playframework.github.io/anorm/).

Package *git* contains parts of code inherited from GitBucket project.

*In heavy development*

# Local development

Init psql db:

```
sudo -u postgres psql
create database razam;
create user razam with encrypted password 'razam';
grant all privileges on database razam to razam;
```

Build front-end:

```
cd public
npm install
npm run build
```

Run app in live-reload mode:

```
sbt run
```

Open http://localhost:9000

Run tests:

```
sbt test
```