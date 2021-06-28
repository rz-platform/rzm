# Architecture

Play framework encourages you to have "view-model-data" as the top-level namespaces.
That's OK for smaller systems, but as an application grows, you need to modularize further.

RZ split top level into domain-oriented modules, which are internally layered.

Don't use layers as the top-level modules in a complex application. Instead, make your top-level modules be full-stack.
 [Martin Fowler](https://martinfowler.com/bliki/PresentationDomainDataLayering.html) 

## Domain

See [Glossary](./glossary.md) for unified language of the project.