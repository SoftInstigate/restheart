/*
 * RESTHeart - the data REST API server
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers;

import org.restheart.handlers.collection.DeleteCollectionHandler;
import org.restheart.handlers.collection.GetCollectionHandler;
import org.restheart.handlers.collection.PatchCollectionHandler;
import org.restheart.handlers.collection.PostCollectionHandler;
import org.restheart.handlers.collection.PutCollectionHandler;
import org.restheart.handlers.database.DeleteDBHandler;
import org.restheart.handlers.database.GetDBHandler;
import org.restheart.handlers.database.PatchDBHandler;
import org.restheart.handlers.database.PutDBHandler;
import org.restheart.handlers.document.DeleteDocumentHandler;
import org.restheart.handlers.document.GetDocumentHandler;
import org.restheart.handlers.document.PatchDocumentHandler;
import org.restheart.handlers.document.PutDocumentHandler;
import org.restheart.handlers.files.PutFileHandler;
import org.restheart.handlers.indexes.DeleteIndexHandler;
import org.restheart.handlers.indexes.GetIndexesHandler;
import org.restheart.handlers.indexes.PutIndexHandler;
import org.restheart.handlers.root.GetRootHandler;

/**
 * This builder allows to create partially-initializaed instances of a
 * RequestDispacherHandler, which are useful for unit testing. For example, set
 * all handlers to 'null', except for the handler under test.
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
class RequestDispacherHandlerBuilder {

    private GetRootHandler rootGet;
    private GetDBHandler dbGet;
    private PutDBHandler dbPut;
    private DeleteDBHandler dbDelete;
    private PatchDBHandler dbPatch;
    private GetCollectionHandler collectionGet;
    private PostCollectionHandler collectionPost;
    private PutCollectionHandler collectionPut;
    private DeleteCollectionHandler collectionDelete;
    private PatchCollectionHandler collectionPatch;
    private GetDocumentHandler documentGet;
    private PutDocumentHandler documentPut;
    private DeleteDocumentHandler documentDelete;
    private PatchDocumentHandler documentPatch;
    private GetIndexesHandler indexesGet;
    private PutIndexHandler indexPut;
    private DeleteIndexHandler indexDelete;
    private PutFileHandler filePut;

    public RequestDispacherHandlerBuilder() {
    }

    public RequestDispacherHandlerBuilder setRootGet(GetRootHandler rootGet) {
        this.rootGet = rootGet;
        return this;
    }

    public RequestDispacherHandlerBuilder setDbGet(GetDBHandler dbGet) {
        this.dbGet = dbGet;
        return this;
    }

    public RequestDispacherHandlerBuilder setDbPut(PutDBHandler dbPut) {
        this.dbPut = dbPut;
        return this;
    }

    public RequestDispacherHandlerBuilder setDbDelete(DeleteDBHandler dbDelete) {
        this.dbDelete = dbDelete;
        return this;
    }

    public RequestDispacherHandlerBuilder setDbPatch(PatchDBHandler dbPatch) {
        this.dbPatch = dbPatch;
        return this;
    }

    public RequestDispacherHandlerBuilder setCollectionGet(GetCollectionHandler collectionGet) {
        this.collectionGet = collectionGet;
        return this;
    }

    public RequestDispacherHandlerBuilder setCollectionPost(PostCollectionHandler collectionPost) {
        this.collectionPost = collectionPost;
        return this;
    }

    public RequestDispacherHandlerBuilder setCollectionPut(PutCollectionHandler collectionPut) {
        this.collectionPut = collectionPut;
        return this;
    }

    public RequestDispacherHandlerBuilder setCollectionDelete(DeleteCollectionHandler collectionDelete) {
        this.collectionDelete = collectionDelete;
        return this;
    }

    public RequestDispacherHandlerBuilder setCollectionPatch(PatchCollectionHandler collectionPatch) {
        this.collectionPatch = collectionPatch;
        return this;
    }

    public RequestDispacherHandlerBuilder setDocumentGet(GetDocumentHandler documentGet) {
        this.documentGet = documentGet;
        return this;
    }

    public RequestDispacherHandlerBuilder setDocumentPut(PutDocumentHandler documentPut) {
        this.documentPut = documentPut;
        return this;
    }

    public RequestDispacherHandlerBuilder setDocumentDelete(DeleteDocumentHandler documentDelete) {
        this.documentDelete = documentDelete;
        return this;
    }

    public RequestDispacherHandlerBuilder setDocumentPatch(PatchDocumentHandler documentPatch) {
        this.documentPatch = documentPatch;
        return this;
    }

    public RequestDispacherHandlerBuilder setIndexesGet(GetIndexesHandler indexesGet) {
        this.indexesGet = indexesGet;
        return this;
    }

    public RequestDispacherHandlerBuilder setIndexPut(PutIndexHandler indexPut) {
        this.indexPut = indexPut;
        return this;
    }

    public RequestDispacherHandlerBuilder setIndexDelete(DeleteIndexHandler indexDelete) {
        this.indexDelete = indexDelete;
        return this;
    }

    public RequestDispacherHandlerBuilder setFilePut(PutFileHandler filePut) {
        this.filePut = filePut;
        return this;
    }

    public RequestDispacherHandler createRequestDispacherHandler() {
        return new RequestDispacherHandler(rootGet, dbGet, dbPut, dbDelete, dbPatch, collectionGet, collectionPost, collectionPut, collectionDelete, collectionPatch, documentGet, documentPut, documentDelete, documentPatch, indexesGet, indexPut, indexDelete, filePut);
    }

}
