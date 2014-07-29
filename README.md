# RESTHeart API guide


## operations on data

	/							(rest collection of dbs)
	/db							(rest collection of collections)
	/db/collection				(rest collection of documents)
	/db/collection/document		(document rest entity)

verbs  | /               | db                           | collection                | document
-------|-----------------|------------------------------|---------------------------|-----------------------
GET    | embedded dbs    | db metadata + embedded colls | get coll + embedded docs  | get document
POST   | NOT_IMPLEMENTED | NOT_IMPLEMENTED              | create document           | NOT_IMPLEMENTED
PUT    | NOT_IMPLEMENTED | upsert db                    | upsert coll metadata      | create/update document
PATCH  | NOT_IMPLEMENTED | patch db metadata            | patch coll metadata       | patch document
DELETE | NOT_IMPLEMENTED | delete db (1)                | delete collection (2)     | delete document

1) only if db has no collections
2) only if collection is empty

## Opent point

how to allow batch operations?

idea PUT/PATCH/DELETE /db/collection/_embedded

## metadata

### db metadata

may include

*	ACL
*	visibility (see below)
*	mongodb db options
*	quota

###collection metadata

may include:


* link definition (for instance it defines which field is a link and target collection)
* schema constraints (required fields, fields types, etc)
* collection level ACL (not only admin, write, read)
* visibility (public, private, and something that allows users to access only data created by themself)
* default write concern

## TODOs





