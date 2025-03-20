package org.restheart.exchange;

/**
 * Interface RawBodyAccessor
 * 
 * @param <T> the type of the raw body
 */
interface RawBodyAccessor<T> {

    /**
     * @return the request's raw body as type T
     */
    T getRawBody();

}
