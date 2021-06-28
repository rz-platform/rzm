# Glossary

## Document

The document represents the core entity of the project.

A document is a collection of files created by a user.
Files are stored in Git repositories on top of the file system.

Meta information (author, name, creation date, configuration, etc.) is stored in Redis.

## User

The user represents the client entity. Users are stored in Redis.

## Templates

Templates are a collection of predefined draft documents.

Templates are stored on top of the file system.

## Collaborator

A collaborator is a value object that represents the relationship between a user and a document.
Collaborators are stored in Redis.

## SSH key

The ssh key represents the entity of the user's ssh key. Keys are stored in Redis. 