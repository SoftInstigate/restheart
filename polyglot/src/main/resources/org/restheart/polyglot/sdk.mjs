// The restheart:sdk module. Served by the runtime from memory, one instance per context,
// with the exports of the available capabilities appended by RhRuntime.sdkModuleSource().
//
// Everything with logic lives in Java (org.restheart.polyglot.spi); this file only re-exports
// it, defines the error classes a plugin throws and the two declaration helpers.

const __rt = globalThis.__restheart;

if (!__rt) {
    throw new Error("restheart:sdk can only be imported by a plugin deployed by RESTHeart");
}

// ---- always present ----

export const logger = __rt.logger();
export const crypto = __rt.crypto();
export const encode = __rt.encode();
export const bson = __rt.bson();

const __resource = __rt.resource();

/** Reads a file shipped next to the plugin, as UTF-8 text. */
export function resource(name) {
    return __resource.text(name);
}
resource.text = (name) => __resource.text(name);
resource.bytes = (name) => __resource.bytes(name);
resource.exists = (name) => __resource.exists(name);

export const spiVersion = __rt.spiVersion();

// ---- plugin declaration ----

function __define(kind, def) {
    if (def === null || typeof def !== "object") {
        throw new TypeError(`define${kind === "service" ? "Service" : "Interceptor"}() needs an object`);
    }
    if (typeof def.handle !== "function") {
        throw new TypeError(`${def.name ?? kind}: handle(req, res) must be a function`);
    }
    if (kind === "interceptor" && typeof def.resolve !== "function") {
        throw new TypeError(`${def.name ?? kind}: an interceptor needs resolve(req, res)`);
    }
    return Object.freeze({ ...def, __kind: kind, __sdk: true });
}

/** Declares a service: the default export of the plugin module. */
export function defineService(def) {
    return __define("service", def);
}

/** Declares an interceptor: the default export of the plugin module. */
export function defineInterceptor(def) {
    return __define("interceptor", def);
}

// ---- typed errors: thrown by handle(), answered with their status ----

export class HttpError extends Error {
    constructor(status, message) {
        super(message ?? `HTTP ${status}`);
        this.name = "HttpError";
        this.status = status;
    }
}

export class BadRequestError extends HttpError {
    constructor(message) {
        super(400, message ?? "Bad Request");
        this.name = "BadRequestError";
    }
}

export class UnauthorizedError extends HttpError {
    constructor(message) {
        super(401, message ?? "Unauthorized");
        this.name = "UnauthorizedError";
    }
}

export class ForbiddenError extends HttpError {
    constructor(message) {
        super(403, message ?? "Forbidden");
        this.name = "ForbiddenError";
    }
}

export class NotFoundError extends HttpError {
    constructor(message) {
        super(404, message ?? "Not Found");
        this.name = "NotFoundError";
    }
}

export class ConflictError extends HttpError {
    constructor(message) {
        super(409, message ?? "Conflict");
        this.name = "ConflictError";
    }
}
