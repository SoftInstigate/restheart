# RESTHeart API guide


## operations on data

	/							(rest collection of dbs)
	/db							(rest collection of collections)
	/db/collection				(rest collection of documents)
	/db/collection/document		(document rest entity)

verbs  | /           | db                  | collection                | document
-------|-------------|---------------------|---------------------------|-----------------------
GET    | list of dbs | list of collections | get documents             | get document
POST   | NOT_ALLOWED | NOT_ALLOWED         | create document           | NOT_ALLOWED
PUT    | NOT_ALLOWED | create db           | batch update documents    | create/update document
PATCH  | NOT_ALLOWED | NOT_ALLOWED         | batch update documents(1) | update document(1)
DELETE | NOT_ALLOWED | NOT_ALLOWED(2)      | NOT_ALLOWED(3)            | delete document

1) limited to attributes in the request
2) should be "batch delete collections" but too dangerous (delete /db?entity can be used eventually)
3) should be "batch delete documents" but too dangerous (delete /db/collection?entity can be used eventually)

## mgmt operations and operations on metadata

mgmt operation are done against the following entities: account, db and collection

since those URIs are by default treated as collections, we put the entity query parameter to 

	/?entity				(account rest entity)
	/db?entity				(db rest entity)
	/db/collection?entity	(collection rest entity)

Note: ~~/db/collection/document?meta~~ are all NOT_ALLOWED

verbs | /                          | db                            |  collection                         
------|----------------------------|-------------------------------|----------------------------------
GET   | get account metadata       | get db metadata               | get collection metadata
POST  | NOT_ALLOWED(2)             | NOT_ALLOWED(2)                | NOT_ALLOWED(2)
PUT   | update account metadata(3) | create db/update db metadata  | create/update collection metadata
PATCH | update account metadata(1) | update db metadata(1)         | update collection metadata(1)
DELETE| NOT_ALLOWED(3)             | delete db                     | delete collection

1) limited to attributes in the request
2) post on entities are not allowed
3) account is created/deleted via service registration/termination