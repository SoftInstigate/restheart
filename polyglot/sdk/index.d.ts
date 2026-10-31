// Type declarations of the restheart:sdk module: the SDK of RESTHeart JavaScript and
// TypeScript plugins. Types only. The runtime is RESTHeart, which serves the module to the
// plugins it deploys; every decision about what a value is (a missing query parameter is
// undefined, a repeated header is its first value, an ObjectId is { $oid }) is made once, in
// Java, in org.restheart.polyglot.spi, and only described here.
//
// Versioned in lockstep with RESTHeart: @restheart/sdk 9.14.0 declares what RESTHeart 9.14.0
// provides. A plugin's package.json names the SDK major it was written for in "rh:spi".

declare module "restheart:sdk" {
    // ---------------------------------------------------------------- content kinds

    /**
     * The type of the request and response content a plugin works with.
     *
     * - `string`: the body as a string
     * - `json`: the body as native data (objects, arrays, strings, numbers)
     * - `bson`: like json, with the BSON types in canonical extended JSON: `{ $oid }`, `{ $date }`, ...
     * - `bytes`: the body as bytes
     * - `mongo`: interceptors only, requests handled by the MongoDB service; `req.mongo` is available
     * - `csv`: interceptors only, requests handled by the csv loader; the content is an array of documents
     */
    export type ContentKind = "string" | "json" | "bson" | "bytes" | "mongo" | "csv";

    /** A JSON value as a plugin reads and writes it. */
    export type Json = null | boolean | number | string | Json[] | { [key: string]: Json };

    /** A document in canonical extended JSON: `{ _id: { $oid: "..." }, when: { $date: "..." } }`. */
    export type Document = { [key: string]: Bson };

    /**
     * A BSON value in canonical extended JSON. Numbers: an integer that fits in int32 is an
     * int32, everything else a double; `$numberInt`, `$numberLong`, `$numberDouble` and
     * `$numberDecimal` override that. A native `Date` is accepted on write; reads return `{ $date }`.
     */
    export type Bson =
        | null
        | boolean
        | number
        | string
        | Date
        | Bson[]
        | Document
        | { $oid: string }
        | { $date: string | number }
        | { $numberInt: string }
        | { $numberLong: string }
        | { $numberDouble: string }
        | { $numberDecimal: string }
        | { $binary: { base64: string; subType: string } }
        | { $regularExpression: { pattern: string; options: string } }
        | { $timestamp: { t: number; i: number } }
        | { $minKey: 1 }
        | { $maxKey: 1 };

    /** Bytes, as the runtime hands them over: a Java byte[] seen as an array of numbers. */
    export interface Bytes {
        readonly length: number;
        [index: number]: number;
    }

    /** What a method taking bytes accepts: a string (UTF-8), bytes, a Uint8Array or an ArrayBuffer. */
    export type BytesLike = string | Bytes | Uint8Array | ArrayBuffer;

    /** The content type of each kind. */
    export type Content<K extends ContentKind> =
        K extends "string" ? string | null :
        K extends "json" ? Json :
        K extends "bson" ? Bson :
        K extends "bytes" ? Bytes | null :
        K extends "mongo" ? Bson :
        K extends "csv" ? Document[] :
        never;

    // ---------------------------------------------------------------- request

    /** The authenticated account, `null` when the request is not authenticated. */
    export interface Account {
        readonly name: string;
        readonly roles: readonly string[];
    }

    /** The query parameters: `req.query.limit` is the first value, a string, `undefined` when absent. */
    export interface Query {
        readonly [name: string]: string | undefined;
    }

    /** The query parameters with every value: `req.queryAll.tag` is an array, `undefined` when absent. */
    export interface QueryAll {
        readonly [name: string]: readonly string[] | undefined;
    }

    /** The headers, case-insensitive: `req.headers["content-type"]` is the first value, `undefined` when absent. */
    export interface Headers {
        [name: string]: string | undefined;
    }

    /** `req.mongo`: the MongoDB part of a request handled by the MongoDB service (`contentKind: "mongo"`). */
    export interface MongoRequest {
        readonly dbName: string | null;
        readonly collectionName: string | null;
        /** The document id as native data, `{ $oid }` for an ObjectId, a string or a number otherwise. */
        readonly documentId: Bson;
        /** The `filter` query parameter parsed, `null` when absent. */
        readonly filter: Document | null;
        /** The `sort` query parameter parsed, `null` when absent. */
        readonly sort: Document | null;
        /** The `keys` query parameter parsed, `null` when absent. */
        readonly projection: Document | null;
        readonly page: number;
        readonly pagesize: number;
        readonly count: boolean;
        isDocument(): boolean;
        isCollection(): boolean;
        isDb(): boolean;
        isCollectionIndexes(): boolean;
        isFile(): boolean;
        isBulkDocuments(): boolean;
        isCount(): boolean;
        /** The Java `org.restheart.exchange.MongoRequest`, the escape hatch. */
        readonly raw: unknown;
    }

    /** The request as a plugin sees it. */
    export interface Request<K extends ContentKind = "string"> {
        readonly path: string;
        readonly method: string;
        readonly url: string;
        readonly queryString: string | null;
        readonly query: Query;
        readonly queryAll: QueryAll;
        readonly headers: Headers;
        readonly account: Account | null;
        /** The content, typed by the content kind. Assignable in a request interceptor. */
        content: Content<K>;
        contentType: string | null;
        /** The MongoDB part, for `contentKind: "mongo"`; `null` otherwise. */
        readonly mongo: K extends "mongo" ? MongoRequest : null;
        /** The Java `org.restheart.exchange.Request`, the escape hatch. */
        readonly raw: unknown;

        isGet(): boolean;
        isPost(): boolean;
        isPut(): boolean;
        isPatch(): boolean;
        isDelete(): boolean;
        isOptions(): boolean;
        isHead(): boolean;
        isAuthenticated(): boolean;
        /** Whether the request is handled by the service of that name, for an interceptor's `resolve`. */
        isHandledBy(serviceName: string): boolean;
        /** The parameters of the path against a template: `req.pathParams("/users/{id}").id`. */
        pathParams(template: string): { readonly [name: string]: string | undefined };
        /** The value of a cookie, `null` when absent. */
        cookie(name: string): string | null;
        /** The first value of a header, `null` when absent. */
        header(name: string): string | null;
        /** A parameter attached to the request by another plugin, `null` when absent. */
        param(name: string): unknown;
        /** Attaches a parameter to the request, for the plugins that run after this one. */
        attach(name: string, value: unknown): void;
    }

    // ---------------------------------------------------------------- response

    /** The response as a plugin sees it. */
    export interface Response<K extends ContentKind = "string"> {
        statusCode: number;
        readonly headers: Headers;
        /** The content, typed by the content kind. */
        content: Content<K>;
        contentType: string | null;
        /** The Java `org.restheart.exchange.Response`, the escape hatch. */
        readonly raw: unknown;

        /** Sets the content as JSON, the content type and, when none is set yet, status 200. */
        json(value: Json | Bson | unknown): void;
        /** Sets the content as text, `text/plain` and, when none is set yet, status 200. */
        text(value: string): void;
        /** Sets the content as bytes, `application/octet-stream` and, when none is set yet, status 200. */
        bytes(value: BytesLike): void;
        /** The error body RESTHeart answers with everywhere: `{ msg }` with the status. */
        setInError(status: number, message?: string): void;
        /** Sets a header; the same as `res.headers[name] = value`. */
        header(name: string, value: string): void;
    }

    // ---------------------------------------------------------------- declaration

    /** The uri match policy of a service. */
    export type MatchPolicy = "PREFIX" | "EXACT";

    /** When an interceptor runs. */
    export type InterceptPoint = "REQUEST_BEFORE_EXCHANGE_INIT" | "REQUEST_BEFORE_AUTH" | "REQUEST_AFTER_AUTH" | "RESPONSE" | "RESPONSE_ASYNC";

    /** The content kinds a service can declare. */
    export type ServiceContentKind = Exclude<ContentKind, "mongo" | "csv">;

    export interface ServiceDefinition<K extends ServiceContentKind = "string"> {
        name: string;
        description: string;
        uri: string;
        /** `true` to require authentication and authorization. Default `false`. */
        secured?: boolean;
        /** Default `"PREFIX"`. */
        matchPolicy?: MatchPolicy;
        /** Default `"string"`. */
        contentKind?: K;
        handle(req: Request<K>, res: Response<K>): void;
    }

    export interface InterceptorDefinition<K extends ContentKind = "string"> {
        name: string;
        description: string;
        /** Default `"REQUEST_AFTER_AUTH"`. */
        interceptPoint?: InterceptPoint;
        /** Default `"string"`. An interceptor runs on the requests of the services with the same kind. */
        contentKind?: K;
        handle(req: Request<K>, res: Response<K>): void;
        /** Whether to run `handle` on this request. */
        resolve(req: Request<K>, res: Response<K>): boolean;
    }

    /** A service plugin. */
    export type Service<K extends ServiceContentKind = "string"> = Readonly<ServiceDefinition<K>> & { readonly __kind: "service" };

    /** An interceptor plugin. */
    export type Interceptor<K extends ContentKind = "string"> = Readonly<InterceptorDefinition<K>> & { readonly __kind: "interceptor" };

    /**
     * Declares a service: the default export of the plugin module.
     *
     * ```ts
     * export default defineService({
     *   name: "hello", description: "says hello", uri: "/hello", contentKind: "json",
     *   handle(req, res) { res.json({ msg: `hello ${req.query.name ?? "world"}` }); },
     * });
     * ```
     */
    export function defineService<K extends ServiceContentKind = "string">(def: ServiceDefinition<K>): Service<K>;

    /** Declares an interceptor: the default export of the plugin module. */
    export function defineInterceptor<K extends ContentKind = "string">(def: InterceptorDefinition<K>): Interceptor<K>;

    // ---------------------------------------------------------------- errors

    /** An error answered with its status and message. Thrown from `handle`. */
    export class HttpError extends Error {
        readonly status: number;
        constructor(status: number, message?: string);
    }
    export class BadRequestError extends HttpError { constructor(message?: string); }
    export class UnauthorizedError extends HttpError { constructor(message?: string); }
    export class ForbiddenError extends HttpError { constructor(message?: string); }
    export class NotFoundError extends HttpError { constructor(message?: string); }
    export class ConflictError extends HttpError { constructor(message?: string); }

    // ---------------------------------------------------------------- utilities, always present

    /** SLF4J-style `{}` placeholders, null-safe. One logger per plugin, named `org.restheart.polyglot.plugins.<name>`. */
    export interface Logger {
        trace(message: string, ...args: unknown[]): void;
        debug(message: string, ...args: unknown[]): void;
        info(message: string, ...args: unknown[]): void;
        warn(message: string, ...args: unknown[]): void;
        error(message: string, ...args: unknown[]): void;
        isTraceEnabled(): boolean;
        isDebugEnabled(): boolean;
        isInfoEnabled(): boolean;
        isWarnEnabled(): boolean;
        isErrorEnabled(): boolean;
    }
    export const logger: Logger;

    /** Digests and HMACs as lowercase hex. */
    export interface Crypto {
        sha256(data: BytesLike): string;
        sha512(data: BytesLike): string;
        sha1(data: BytesLike): string;
        md5(data: BytesLike): string;
        hmacSha256(key: BytesLike, data: BytesLike): string;
        hmacSha512(key: BytesLike, data: BytesLike): string;
        hmacSha1(key: BytesLike, data: BytesLike): string;
        randomBytes(n: number): Bytes;
        randomHex(n: number): string;
        uuid(): string;
        /** Compares in constant time, as a signature must be compared. */
        timingSafeEqual(a: BytesLike, b: BytesLike): boolean;
    }
    export const crypto: Crypto;

    export interface Encode {
        toBase64(data: BytesLike): string;
        fromBase64(s: string): Bytes;
        toBase64Url(data: BytesLike): string;
        fromBase64Url(s: string): Bytes;
        toHex(data: BytesLike): string;
        fromHex(s: string): Bytes;
        toUtf8(s: string): Bytes;
        fromUtf8(data: BytesLike): string;
        urlEncode(s: string): string;
        urlDecode(s: string): string;
    }
    export const encode: Encode;

    /** Reads a file shipped next to the plugin, inside the plugin directory only. */
    export interface Resource {
        (name: string): string;
        text(name: string): string;
        bytes(name: string): Bytes;
        exists(name: string): boolean;
    }
    export const resource: Resource;

    export interface BsonHelper {
        /** The hex string of `{ $oid: "..." }`, or of a string that already is one; `null` for `null`. */
        oid(value: { $oid: string } | string | null | undefined): string | null;
        /** A new ObjectId as `{ $oid: "..." }`. */
        newOid(): { $oid: string };
        isOid(value: unknown): boolean;
        /** Parses JSON with extended JSON wrappers to a document. */
        parse(json: string): Document;
        /** The canonical extended JSON of a value. */
        stringify(value: Bson): string;
    }
    export const bson: BsonHelper;

    /** The SDK version this RESTHeart provides, what `rh:spi` in package.json is checked against. */
    export const spiVersion: number;

    // ---------------------------------------------------------------- capabilities
    //
    // Importing a capability is also declaring it: a plugin that imports one the deployment
    // does not have fails at load time and is not deployed, with a log line naming it.

    /** The plugin's section of restheart.yml, by plugin name. Always available. */
    export const config: { readonly [key: string]: unknown };

    export interface HttpOptions {
        headers?: { [name: string]: string };
        /** Milliseconds; overrides the configured request-timeout for this call. */
        timeout?: number;
        auth?: { bearer: string } | { basic: { user: string; password?: string } };
        /** An `application/x-www-form-urlencoded` body. */
        form?: { [name: string]: string };
        /** An explicit JSON body. */
        json?: unknown;
        contentType?: string;
    }

    export interface HttpRequestSpec extends HttpOptions {
        method: string;
        url: string;
        body?: unknown;
    }

    /** A 4xx or a 5xx does not throw: it is a response with `ok === false`. A connection failure or a timeout throws. */
    export interface HttpResponse {
        readonly status: number;
        readonly ok: boolean;
        readonly headers: Headers;
        /** The body as UTF-8 text. */
        readonly body: string;
        readonly contentType: string | null;
        json(): Json;
        bytes(): Bytes;
    }

    /** The shared, configured HTTP client of the `http-client` provider. Blocking, no promises. Always available. */
    export interface Http {
        get(url: string, options?: HttpOptions): HttpResponse;
        delete(url: string, options?: HttpOptions): HttpResponse;
        /** An object or array body is sent as JSON, a string as text, bytes as bytes. */
        post(url: string, body?: unknown, options?: HttpOptions): HttpResponse;
        put(url: string, body?: unknown, options?: HttpOptions): HttpResponse;
        patch(url: string, body?: unknown, options?: HttpOptions): HttpResponse;
        request(spec: HttpRequestSpec): HttpResponse;
    }
    export const http: Http;

    export interface FindOptions {
        limit?: number;
        skip?: number;
        sort?: Document;
        projection?: Document;
        batchSize?: number;
    }

    export interface UpdateOptions {
        upsert?: boolean;
        arrayFilters?: Document[];
    }

    /** The cursor of a `find` or an `aggregate`: iterable, blocking, lazy. */
    export interface Cursor extends Iterable<Document> {
        toArray(): Document[];
        first(): Document | null;
        forEach(fn: (doc: Document) => void): void;
    }

    export type BulkOperation =
        | { insertOne: { document: Document } }
        | { updateOne: { filter: Document; update: Document | Document[]; upsert?: boolean } }
        | { updateMany: { filter: Document; update: Document | Document[]; upsert?: boolean } }
        | { replaceOne: { filter: Document; replacement: Document; upsert?: boolean } }
        | { deleteOne: { filter: Document } }
        | { deleteMany: { filter: Document } };

    export interface Collection {
        name(): string;
        find(filter?: Document, options?: FindOptions): Cursor;
        findOne(filter?: Document, options?: FindOptions): Document | null;
        countDocuments(filter?: Document): number;
        estimatedDocumentCount(): number;
        distinct(field: string, filter?: Document): Bson[];
        aggregate(pipeline: Document[], options?: { allowDiskUse?: boolean; batchSize?: number }): Cursor;
        insertOne(document: Document): { acknowledged: boolean; insertedId: Bson };
        insertMany(documents: Document[]): { acknowledged: boolean; insertedCount: number; insertedIds: Bson[] };
        /** `update` is a document of operators, or an array of stages for a pipeline update. */
        updateOne(filter: Document, update: Document | Document[], options?: UpdateOptions): UpdateResult;
        updateMany(filter: Document, update: Document | Document[], options?: UpdateOptions): UpdateResult;
        replaceOne(filter: Document, replacement: Document, options?: { upsert?: boolean }): UpdateResult;
        deleteOne(filter: Document): { acknowledged: boolean; deletedCount: number };
        deleteMany(filter: Document): { acknowledged: boolean; deletedCount: number };
        bulkWrite(operations: BulkOperation[]): { acknowledged: boolean; insertedCount: number; matchedCount: number; modifiedCount: number; deletedCount: number; upsertedCount: number };
        createIndex(keys: Document, options?: { unique?: boolean; name?: string; sparse?: boolean; expireAfterSeconds?: number }): string;
        drop(): void;
        /** The driver's `MongoCollection<BsonDocument>`, the escape hatch. */
        raw(): unknown;
    }

    export interface UpdateResult {
        acknowledged: boolean;
        matchedCount: number;
        modifiedCount: number;
        upsertedId: Bson | null;
    }

    export interface Db {
        name(): string;
        collection(name: string): Collection;
        /** Any database command: `createIndexes`, `collStats`, `explain`, ... */
        command(command: Document): Document;
        collections(): string[];
        drop(): void;
        /** The driver's `MongoDatabase`, the escape hatch. */
        raw(): unknown;
    }

    /** The `mclient` provider. Available when MongoDB is enabled. */
    export interface Mongo {
        db(name: string): Db;
        databases(): string[];
        /** The driver's `MongoClient`, the escape hatch. */
        raw(): unknown;
    }
    export const mongo: Mongo;

    export interface MailMessage {
        to: string;
        /** The display name of the recipient. */
        name?: string;
        subject: string;
        html: string;
    }

    /** The `emails` provider. Available when email is configured. */
    export interface Mail {
        /** Sends on the calling thread and returns when the SMTP transaction is over. */
        send(message: MailMessage): void;
        /** Hands the send to the shared virtual threads executor and returns at once. */
        sendAsync(message: MailMessage): void;
    }
    export const mail: Mail;

    /** Any registered provider, by name, untyped: the configured, initialised instance. Always available. */
    export const providers: { readonly [name: string]: unknown };
}
