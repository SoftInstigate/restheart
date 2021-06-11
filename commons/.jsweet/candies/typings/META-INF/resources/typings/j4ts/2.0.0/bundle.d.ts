declare namespace java.beans {
    /**
     * General-purpose beans control methods. GWT only supports a limited subset of these methods. Only
     * the documented methods are available.
     * @class
     */
    class Beans {
        /**
         * @return {boolean} <code>true</code> if we are running in the design time mode.
         */
        static isDesignTime(): boolean;
    }
}
declare namespace java.io {
    /**
     * An {@code AutoCloseable} whose close method may throw an {@link IOException}.
     * @class
     */
    interface Closeable extends java.lang.AutoCloseable {
        /**
         * Closes the object and release any system resources it holds.
         *
         * <p>Although only the first call has any effect, it is safe to call close
         * multiple times on the same object. This is more lenient than the
         * overridden {@code AutoCloseable.close()}, which may be called at most
         * once.
         */
        close(): void;
    }
}
declare namespace java.io {
    /**
     * Defines an interface for classes that can (or need to) be flushed, typically
     * before some output processing is considered to be finished and the object
     * gets closed.
     * @class
     */
    interface Flushable {
        /**
         * Flushes the object by writing out any buffered data to the underlying
         * output.
         *
         * @throws IOException
         * if there are any issues writing the data.
         */
        flush(): void;
    }
}
declare namespace java.io {
    /**
     * This constructor does nothing. It is provided for signature
     * compatibility.
     * @class
     * @extends *
     */
    abstract class InputStream implements java.io.Closeable {
        /**
         * Size of the temporary buffer used when skipping bytes with {@link skip(long)}.
         */
        static MAX_SKIP_BUFFER_SIZE: number;
        constructor();
        /**
         * Returns an estimated number of bytes that can be read or skipped without blocking for more
         * input.
         *
         * <p>Note that this method provides such a weak guarantee that it is not very useful in
         * practice.
         *
         * <p>Firstly, the guarantee is "without blocking for more input" rather than "without
         * blocking": a read may still block waiting for I/O to complete&nbsp;&mdash; the guarantee is
         * merely that it won't have to wait indefinitely for data to be written. The result of this
         * method should not be used as a license to do I/O on a thread that shouldn't be blocked.
         *
         * <p>Secondly, the result is a
         * conservative estimate and may be significantly smaller than the actual number of bytes
         * available. In particular, an implementation that always returns 0 would be correct.
         * In general, callers should only use this method if they'd be satisfied with
         * treating the result as a boolean yes or no answer to the question "is there definitely
         * data ready?".
         *
         * <p>Thirdly, the fact that a given number of bytes is "available" does not guarantee that a
         * read or skip will actually read or skip that many bytes: they may read or skip fewer.
         *
         * <p>It is particularly important to realize that you <i>must not</i> use this method to
         * size a container and assume that you can read the entirety of the stream without needing
         * to resize the container. Such callers should probably write everything they read to a
         * {@link ByteArrayOutputStream} and convert that to a byte array. Alternatively, if you're
         * reading from a file, {@link File#length} returns the current length of the file (though
         * assuming the file's length can't change may be incorrect, reading a file is inherently
         * racy).
         *
         * <p>The default implementation of this method in {@code InputStream} always returns 0.
         * Subclasses should override this method if they are able to indicate the number of bytes
         * available.
         *
         * @return {number} the estimated number of bytes available
         * @throws IOException if this stream is closed or an error occurs
         */
        available(): number;
        /**
         * Closes this stream. Concrete implementations of this class should free
         * any resources during close. This implementation does nothing.
         *
         * @throws IOException
         * if an error occurs while closing this stream.
         */
        close(): void;
        /**
         * Sets a mark position in this InputStream. The parameter {@code readlimit}
         * indicates how many bytes can be read before the mark is invalidated.
         * Sending {@code reset()} will reposition the stream back to the marked
         * position provided {@code readLimit} has not been surpassed.
         * <p>
         * This default implementation does nothing and concrete subclasses must
         * provide their own implementation.
         *
         * @param {number} readlimit
         * the number of bytes that can be read from this stream before
         * the mark is invalidated.
         * @see #markSupported()
         * @see #reset()
         */
        mark(readlimit: number): void;
        /**
         * Indicates whether this stream supports the {@code mark()} and
         * {@code reset()} methods. The default implementation returns {@code false}.
         *
         * @return {boolean} always {@code false}.
         * @see #mark(int)
         * @see #reset()
         */
        markSupported(): boolean;
        read$(): number;
        read$byte_A(buffer: number[]): number;
        read$byte_A$int$int(buffer: number[], byteOffset: number, byteCount: number): number;
        /**
         * Reads up to {@code byteCount} bytes from this stream and stores them in
         * the byte array {@code buffer} starting at {@code byteOffset}.
         * Returns the number of bytes actually read or -1 if the end of the stream
         * has been reached.
         *
         * @throws IndexOutOfBoundsException
         * if {@code byteOffset < 0 || byteCount < 0 || byteOffset + byteCount > buffer.length}.
         * @throws IOException
         * if the stream is closed or another IOException occurs.
         * @param {Array} buffer
         * @param {number} byteOffset
         * @param {number} byteCount
         * @return {number}
         */
        read(buffer?: any, byteOffset?: any, byteCount?: any): any;
        /**
         * Resets this stream to the last marked location. Throws an
         * {@code IOException} if the number of bytes read since the mark has been
         * set is greater than the limit provided to {@code mark}, or if no mark
         * has been set.
         * <p>
         * This implementation always throws an {@code IOException} and concrete
         * subclasses should provide the proper implementation.
         *
         * @throws IOException
         * if this stream is closed or another IOException occurs.
         */
        reset(): void;
        /**
         * Skips at most {@code byteCount} bytes in this stream. The number of actual
         * bytes skipped may be anywhere between 0 and {@code byteCount}. If
         * {@code byteCount} is negative, this method does nothing and returns 0, but
         * some subclasses may throw.
         *
         * <p>Note the "at most" in the description of this method: this method may
         * choose to skip fewer bytes than requested. Callers should <i>always</i>
         * check the return value.
         *
         * <p>This default implementation reads bytes into a temporary buffer. Concrete
         * subclasses should provide their own implementation.
         *
         * @return {number} the number of bytes actually skipped.
         * @throws IOException if this stream is closed or another IOException
         * occurs.
         * @param {number} byteCount
         */
        skip(byteCount: number): number;
    }
}
declare namespace java.io {
    /**
     * Provides a series of utilities to be reused between IO classes.
     *
     * TODO(chehayeb): move these checks to InternalPreconditions.
     * @class
     */
    class IOUtils {
        static checkOffsetAndCount$byte_A$int$int(buffer: number[], byteOffset: number, byteCount: number): void;
        /**
         * Validates the offset and the byte count for the given array of bytes.
         *
         * @param {Array} buffer Array of bytes to be checked.
         * @param {number} byteOffset Starting offset in the array.
         * @param {number} byteCount Total number of bytes to be accessed.
         * @throws NullPointerException if the given reference to the buffer is null.
         * @throws IndexOutOfBoundsException if {@code byteOffset} is negative, {@code byteCount} is
         * negative or their sum exceeds the buffer length.
         */
        static checkOffsetAndCount(buffer?: any, byteOffset?: any, byteCount?: any): any;
        static checkOffsetAndCount$char_A$int$int(buffer: string[], charOffset: number, charCount: number): void;
        static checkOffsetAndCount$int$int$int(length: number, offset: number, count: number): void;
        constructor();
    }
}
declare namespace java.io {
    /**
     * Default constructor.
     * @class
     */
    abstract class OutputStream implements java.io.Closeable, java.io.Flushable {
        constructor();
        /**
         * Closes this stream. Implementations of this method should free any
         * resources used by the stream. This implementation does nothing.
         *
         * @throws IOException
         * if an error occurs while closing this stream.
         */
        close(): void;
        /**
         * Flushes this stream. Implementations of this method should ensure that
         * any buffered data is written out. This implementation does nothing.
         *
         * @throws IOException
         * if an error occurs while flushing this stream.
         */
        flush(): void;
        write$byte_A(buffer: number[]): void;
        write$byte_A$int$int(buffer: number[], offset: number, count: number): void;
        /**
         * Writes {@code count} bytes from the byte array {@code buffer} starting at
         * position {@code offset} to this stream.
         *
         * @param {Array} buffer
         * the buffer to be written.
         * @param {number} offset
         * the start position in {@code buffer} from where to get bytes.
         * @param {number} count
         * the number of bytes from {@code buffer} to write to this
         * stream.
         * @throws IOException
         * if an error occurs while writing to this stream.
         * @throws IndexOutOfBoundsException
         * if {@code offset < 0} or {@code count < 0}, or if
         * {@code offset + count} is bigger than the length of
         * {@code buffer}.
         */
        write(buffer?: any, offset?: any, count?: any): any;
        write$int(oneByte: number): void;
    }
}
declare namespace java.io {
    /**
     * JSweet implementation.
     * @class
     */
    abstract class Reader implements java.io.Closeable {
        lock: any;
        constructor(lock?: any);
        read$(): number;
        read$char_A(cbuf: string[]): number;
        read$char_A$int$int(cbuf: string[], off: number, len: number): number;
        read(cbuf?: any, off?: any, len?: any): any;
        /**
         * Maximum skip-buffer size
         */
        static maxSkipBufferSize: number;
        /**
         * Skip buffer, null until allocated
         */
        skipBuffer: string[];
        skip(n: number): number;
        ready(): boolean;
        markSupported(): boolean;
        mark(readAheadLimit: number): void;
        reset(): void;
        abstract close(): any;
    }
}
declare namespace java.io {
    /**
     * Provided for interoperability; RPC treats this interface synonymously with
     * {@link com.google.gwt.user.client.rpc.IsSerializable}.
     * The Java serialization protocol is explicitly not supported.
     * @class
     */
    interface Serializable {
    }
}
declare namespace java.io {
    /**
     * JSweet implementation.
     * @class
     */
    abstract class Writer implements java.lang.Appendable, java.io.Closeable, java.io.Flushable {
        writeBuffer: string[];
        static WRITE_BUFFER_SIZE: number;
        lock: any;
        constructor(lock?: any);
        write$int(c: number): void;
        write$char_A(cbuf: string[]): void;
        write$char_A$int$int(cbuf: string[], off: number, len: number): void;
        write$java_lang_String(str: string): void;
        write$java_lang_String$int$int(str: string, off: number, len: number): void;
        write(str?: any, off?: any, len?: any): any;
        append$java_lang_CharSequence(csq: any): Writer;
        append$java_lang_CharSequence$int$int(csq: any, start: number, end: number): Writer;
        append(csq?: any, start?: any, end?: any): any;
        append$char(c: string): Writer;
        abstract flush(): any;
        abstract close(): any;
    }
}
declare namespace java.lang {
    /**
     * A base class to share implementation between {@link StringBuffer} and {@link StringBuilder}.
     * <p>
     * Most methods will give expected performance results. Exception is {@link #setCharAt(int, char)},
     * which is O(n), and thus should not be used many times on the same <code>StringBuffer</code>.
     * @param {string} string
     * @class
     */
    abstract class AbstractStringBuilder {
        string: string;
        constructor(string: string);
        length(): number;
        setLength(newLength: number): void;
        capacity(): number;
        ensureCapacity(ignoredCapacity: number): void;
        trimToSize(): void;
        charAt(index: number): string;
        getChars(srcStart: number, srcEnd: number, dst: string[], dstStart: number): void;
        /**
         * Warning! This method is <b>much</b> slower than the JRE implementation. If you need to do
         * character level manipulation, you are strongly advised to use a char[] directly.
         * @param {number} index
         * @param {string} x
         */
        setCharAt(index: number, x: string): void;
        subSequence(start: number, end: number): any;
        substring$int(begin: number): string;
        substring$int$int(begin: number, end: number): string;
        substring(begin?: any, end?: any): any;
        indexOf$java_lang_String(x: string): number;
        indexOf$java_lang_String$int(x: string, start: number): number;
        indexOf(x?: any, start?: any): any;
        lastIndexOf$java_lang_String(s: string): number;
        lastIndexOf$java_lang_String$int(s: string, start: number): number;
        lastIndexOf(s?: any, start?: any): any;
        /**
         *
         * @return {string}
         */
        toString(): string;
        append0(x: any, start: number, end: number): void;
        appendCodePoint0(x: number): void;
        replace0(start: number, end: number, toInsert: string): void;
        reverse0(): void;
        static swap(buffer: string[], f: number, s: number): void;
    }
}
declare namespace java.lang.annotation {
    /**
     * Base interface for all annotation types <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/annotation/Annotation.html">[Sun
     * docs]</a>.
     * @class
     */
    interface Annotation {
        annotationType(): any;
    }
}
declare namespace java.lang.annotation {
    /**
     * Indicates the annotation parser determined the annotation was malformed when
     * reading from the class file <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/annotation/AnnotationFormatError.html">[Sun
     * docs]</a>.
     * @class
     * @extends java.lang.Error
     */
    class AnnotationFormatError extends Error {
        constructor();
    }
}
declare namespace java.lang.annotation {
}
declare namespace java.lang.annotation {
    /**
     * Enumerates types of declared elements in a Java program <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/annotation/ElementType.html">[Sun
     * docs]</a>.
     * @enum
     * @property {java.lang.annotation.ElementType} ANNOTATION_TYPE
     * @property {java.lang.annotation.ElementType} CONSTRUCTOR
     * @property {java.lang.annotation.ElementType} FIELD
     * @property {java.lang.annotation.ElementType} LOCAL_VARIABLE
     * @property {java.lang.annotation.ElementType} METHOD
     * @property {java.lang.annotation.ElementType} PACKAGE
     * @property {java.lang.annotation.ElementType} PARAMETER
     * @property {java.lang.annotation.ElementType} TYPE
     * @class
     */
    enum ElementType {
        ANNOTATION_TYPE = 0,
        CONSTRUCTOR = 1,
        FIELD = 2,
        LOCAL_VARIABLE = 3,
        METHOD = 4,
        PACKAGE = 5,
        PARAMETER = 6,
        TYPE = 7,
    }
}
declare namespace java.lang.annotation {
}
declare namespace java.lang.annotation {
}
declare namespace java.lang.annotation {
    /**
     * Enumerates annotation retention policies <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/annotation/RetentionPolicy.html">[Sun
     * docs]</a>.
     * @enum
     * @property {java.lang.annotation.RetentionPolicy} CLASS
     * @property {java.lang.annotation.RetentionPolicy} RUNTIME
     * @property {java.lang.annotation.RetentionPolicy} SOURCE
     * @class
     */
    enum RetentionPolicy {
        CLASS = 0,
        RUNTIME = 1,
        SOURCE = 2,
    }
}
declare namespace java.lang.annotation {
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://java.sun.com/javase/6/docs/api/java/lang/Appendable.html">the
     * official Java API doc</a> for details.
     * @class
     */
    interface Appendable {
        append(charSquence?: any, start?: any, end?: any): any;
    }
}
declare namespace java.lang {
    /**
     * Represents an error caused by an assertion failure.
     * @param {string} message
     * @param {java.lang.Throwable} cause
     * @class
     * @extends java.lang.Error
     */
    class AssertionError extends Error {
        constructor(message?: any, cause?: any);
    }
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://docs.oracle.com/javase/7/docs/api/java/lang/AutoCloseable.html">the
     * official Java API doc</a> for details.
     * @class
     */
    interface AutoCloseable {
        /**
         * Closes this resource.
         */
        close(): any;
    }
}
declare namespace java.lang {
    /**
     * Abstracts the notion of a sequence of characters.
     * @class
     */
    interface CharSequence {
        charAt(index: number): string;
        length(): number;
        subSequence(start: number, end: number): any;
    }
}
declare namespace java.lang {
    /**
     * Generally unsupported. This class is provided so that the GWT compiler can
     * choke down class literal references.
     * <p>
     * NOTE: The code in this class is very sensitive and should keep its
     * dependencies upon other classes to a minimum.
     *
     * @param <T>
     * the type of the object
     * @class
     */
    class Class<T> implements java.lang.reflect.Type {
        static constructors: Array<Function>;
        static constructors_$LI$(): Array<Function>;
        static classes: Array<any>;
        static classes_$LI$(): Array<any>;
        static getConstructorForClass(clazz: any): Function;
        static getClassForConstructor(constructor: Function): any;
        static mapConstructorToClass(constructor: Function, clazz: any): void;
        static PRIMITIVE: number;
        static INTERFACE: number;
        static ARRAY: number;
        static ENUM: number;
        /**
         * Create a Class object for an array.
         * <p>
         *
         * Arrays are not registered in the prototype table and get the class
         * literal explicitly at construction.
         * <p>
         * @param {java.lang.Class} leafClass
         * @param {number} dimensions
         * @return {java.lang.Class}
         * @private
         */
        static getClassLiteralForArray<T>(leafClass: any, dimensions: number): any;
        createClassLiteralForArray(dimensions: number): any;
        /**
         * Create a Class object for a class.
         *
         * @skip
         * @param {string} packageName
         * @param {string} compoundClassName
         * @param {string} typeId
         * @param {java.lang.Class} superclass
         * @return {java.lang.Class}
         */
        static createForClass<T>(packageName: string, compoundClassName: string, typeId: string, superclass: any): any;
        /**
         * Create a Class object for an enum.
         *
         * @skip
         * @param {string} packageName
         * @param {string} compoundClassName
         * @param {string} typeId
         * @param {java.lang.Class} superclass
         * @param {Function} enumConstantsFunc
         * @param {Function} enumValueOfFunc
         * @return {java.lang.Class}
         */
        static createForEnum<T>(packageName: string, compoundClassName: string, typeId: string, superclass: any, enumConstantsFunc: Function, enumValueOfFunc: Function): any;
        /**
         * Create a Class object for an interface.
         *
         * @skip
         * @param {string} packageName
         * @param {string} compoundClassName
         * @return {java.lang.Class}
         */
        static createForInterface<T>(packageName: string, compoundClassName: string): any;
        /**
         * Create a Class object for a primitive.
         *
         * @skip
         * @param {string} className
         * @param {string} primitiveTypeId
         * @return {java.lang.Class}
         */
        static createForPrimitive(className: string, primitiveTypeId: string): any;
        /**
         * Used by {@link WebModePayloadSink} to create uninitialized instances.
         * @param {java.lang.Class} clazz
         * @return {*}
         */
        static getPrototypeForClass(clazz: any): any;
        /**
         * Creates the class object for a type and initiliazes its fields.
         * @param {string} packageName
         * @param {string} compoundClassName
         * @param {string} typeId
         * @return {java.lang.Class}
         * @private
         */
        static createClassObject<T>(packageName: string, compoundClassName: string, typeId: string): any;
        /**
         * Initiliazes {@code clazz} names from metadata.
         * <p>
         * Written in JSNI to minimize dependencies (on String.+).
         * @param {java.lang.Class} clazz
         * @private
         */
        static initializeNames(clazz: any): void;
        /**
         * Sets the class object for primitives.
         * <p>
         * Written in JSNI to minimize dependencies (on (String)+).
         * @param {java.lang.Class} clazz
         * @param {Object} primitiveTypeId
         */
        static synthesizePrimitiveNamesFromTypeId(clazz: any, primitiveTypeId: Object): void;
        enumValueOfFunc: Function;
        modifiers: number;
        componentType: any;
        enumConstantsFunc: Function;
        enumSuperclass: any;
        superclass: any;
        simpleName: string;
        typeName: string;
        canonicalName: string;
        packageName: string;
        compoundName: string;
        typeId: string;
        arrayLiterals: any[];
        sequentialId: number;
        static nextSequentialId: number;
        constructor();
        desiredAssertionStatus(): boolean;
        ensureNamesAreInitialized(): void;
        getCanonicalName(): string;
        getComponentType(): any;
        getEnumConstants(): T[];
        getName(): string;
        getSimpleName(): string;
        getSuperclass(): any;
        isArray(): boolean;
        isEnum(): boolean;
        isInterface(): boolean;
        isPrimitive(): boolean;
        /**
         *
         * @return {string}
         */
        toString(): string;
        /**
         * Used by Enum to allow getSuperclass() to be pruned.
         * @return {java.lang.Class}
         */
        getEnumSuperclass(): any;
    }
}
declare namespace java.lang {
    /**
     * Indicates that a class implements <code>clone()</code>.
     * @class
     */
    interface Cloneable {
    }
}
declare namespace java.lang {
    /**
     * An interface used a basis for implementing custom ordering. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/Comparable.html">[Sun
     * docs]</a>
     *
     * @param <T> the type to compare to.
     * @class
     */
    interface Comparable<T> {
        compareTo(other: T): number;
    }
}
declare namespace java.lang {
}
declare namespace java.lang {
    /**
     * The first-class representation of an enumeration.
     *
     * @param <E>
     * @class
     */
    abstract class Enum<E extends java.lang.Enum<E>> implements java.lang.Comparable<E>, java.io.Serializable {
        static valueOf$java_lang_Class$java_lang_String<T extends java.lang.Enum<T>>(enumType: any, name: string): T;
        static valueOf<T extends java.lang.Enum<T>>(enumType?: any, name?: any): any;
        static createValueOfMap<T extends java.lang.Enum<T>>(enumConstants: T[]): Object;
        static valueOf$def_js_Object$java_lang_String<T extends java.lang.Enum<T>>(map: Object, name: string): T;
        static get0<T extends java.lang.Enum<T>>(map: Object, name: string): T;
        static invokeValueOf<T extends java.lang.Enum<T>>(enumValueOfFunc: Function, name: string): T;
        static put0<T extends java.lang.Enum<T>>(map: Object, name: string, value: T): void;
        __name: string;
        __ordinal: number;
        constructor(name: string, ordinal: number);
        compareTo$java_lang_Enum(other: E): number;
        /**
         *
         * @param {java.lang.Enum} other
         * @return {number}
         */
        compareTo(other?: any): any;
        getDeclaringClass(): any;
        name(): string;
        ordinal(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/Exception.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @param {java.lang.Throwable} cause
     * @class
     * @extends java.lang.Throwable
     */
    class Exception extends Error {
        constructor(message?: any, cause?: any, enableSuppression?: any, writableStackTrace?: any);
    }
}
declare namespace java.lang {
}
declare namespace java.lang {
    class IllegalAccessError extends Error {
        constructor(message?: any, cause?: any);
    }
}
declare namespace java.lang {
    /**
     * Allows an instance of a class implementing this interface to be used in the
     * foreach statement.
     * See <a href="https://docs.oracle.com/javase/8/docs/api/java/lang/Iterable.html">
     * the official Java API doc</a> for details.
     *
     * @param <T> type of returned iterator
     * @class
     */
    interface Iterable<T> {
        iterator(): java.util.Iterator<T>;
        forEach(action?: any): any;
    }
}
declare namespace java.lang {
    class NoSuchMethodError extends Error {
        constructor(message?: any, cause?: any);
    }
}
declare namespace java.lang {
}
declare namespace java.lang.ref {
    /**
     * This implements the reference API in a minimal way. In JavaScript, there is
     * no control over the reference and the GC. So this implementation's only
     * purpose is for compilation.
     * @class
     */
    abstract class Reference<T> {
        referent: T;
        constructor(referent: T);
        get(): T;
        clear(): void;
    }
}
declare namespace java.lang.reflect {
    /**
     * This interface makes {@link java.lang.reflect.Type} available to GWT clients.
     *
     * @see java.lang.reflect.Type
     * @class
     */
    interface Type {
    }
}
declare namespace java.lang {
    /**
     * Encapsulates an action for later execution. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/Runnable.html">[Sun
     * docs]</a>
     *
     * <p>
     * This interface is provided only for JRE compatibility. GWT does not support
     * multithreading.
     * </p>
     * @class
     */
    interface Runnable {
        (): any;
    }
}
declare namespace java.lang {
}
declare namespace java.lang {
    /**
     * Included for hosted mode source compatibility. Partially implemented
     *
     * @skip
     * @param {string} className
     * @param {string} methodName
     * @param {string} fileName
     * @param {number} lineNumber
     * @class
     */
    class StackTraceElement implements java.io.Serializable {
        className: string;
        fileName: string;
        lineNumber: number;
        methodName: string;
        constructor(className?: any, methodName?: any, fileName?: any, lineNumber?: any);
        getClassName(): string;
        getFileName(): string;
        getLineNumber(): number;
        getMethodName(): string;
        /**
         *
         * @param {*} other
         * @return {boolean}
         */
        equals(other: any): boolean;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
}
declare namespace java.lang {
}
declare namespace java.lang {
    /**
     * Constructs a {@code VirtualMachineError} with the specified
     * detail message and cause.  <p>Note that the detail message
     * associated with {@code cause} is <i>not</i> automatically
     * incorporated in this error's detail message.
     *
     * @param  {string} message the detail message (which is saved for later retrieval
     * by the {@link #getMessage()} method).
     * @param  {java.lang.Throwable} cause the cause (which is saved for later retrieval by the
     * {@link #getCause()} method).  (A {@code null} value is
     * permitted, and indicates that the cause is nonexistent or
     * unknown.)
     * @since  1.8
     * @class
     * @extends java.lang.Error
     * @author  Frank Yellin
     */
    abstract class VirtualMachineError extends Error {
        static serialVersionUID: number;
        constructor(message?: any, cause?: any);
    }
}
declare namespace java.lang {
    /**
     * For JRE compatibility.
     * @class
     */
    class Void {
        constructor();
    }
}
declare namespace java.net {
    class InternalJsURLFactory {
        static jsURLCtor: Function;
        static jsURLCtor_$LI$(): Function;
        static getJsURLConstructor(): Function;
        static newJsURL(...objects: any[]): URL;
        constructor();
    }
}
declare namespace java.net {
    class InternalJsURLForShell {
        href: string;
        protocol: string;
        username: string;
        password: string;
        hostname: string;
        port: number;
        pathname: string;
        search: string;
        hash: string;
        constructor(data?: any, url?: any);
    }
}
declare namespace java.net {
    class URL implements java.io.Serializable {
        jsUrl: Object;
        constructor(protocol?: any, host?: any, port?: any, file?: any);
        openStream(): java.io.InputStream;
        static createObjectURL(obj: any): string;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        getAuthority(): string;
        getContent(): any;
        getDefaultPort(): number;
        getFile(): string;
        getHost(): string;
        getPath(): string;
        getPort(): number;
        getProtocol(): string;
        getQuery(): string;
        getRef(): string;
        getUserInfo(): string;
        sameFile(other: URL): boolean;
        toExternalForm(): string;
        makeConnection(): XMLHttpRequest;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
}
declare namespace java.nio {
    abstract class Buffer {
        _capacity: number;
        readOnly: boolean;
        _position: number;
        _limit: number;
        _mark: number;
        constructor(capacity: number, readOnly: boolean);
        abstract array(): any;
        arrayOffset(): number;
        capacity(): number;
        clear(): Buffer;
        flip(): Buffer;
        hasArray(): boolean;
        hasRemaining(): boolean;
        isDirect(): boolean;
        isReadOnly(): boolean;
        limit$(): number;
        limit$int(newLimit: number): Buffer;
        limit(newLimit?: any): any;
        mark(): Buffer;
        position$(): number;
        position$int(newPosition: number): Buffer;
        position(newPosition?: any): any;
        remaining(): number;
        reset(): Buffer;
        rewind(): Buffer;
    }
}
declare namespace java.nio {
    class ByteOrder {
        static BIG_ENDIAN: ByteOrder;
        static BIG_ENDIAN_$LI$(): ByteOrder;
        static LITTLE_ENDIAN: ByteOrder;
        static LITTLE_ENDIAN_$LI$(): ByteOrder;
        constructor();
        /**
         *
         * @return {string}
         */
        toString(): string;
        static nativeOrder(): ByteOrder;
    }
    namespace ByteOrder {
        class NativeInstanceHolder {
            static INSTANCE: java.nio.ByteOrder;
            static nativeOrderTester(): java.nio.ByteOrder;
            constructor();
        }
    }
}
declare namespace java.nio.charset {
    /**
     * A minimal emulation of {@link Charset}.
     * @class
     */
    abstract class Charset implements java.lang.Comparable<Charset> {
        static availableCharsets(): java.util.SortedMap<string, Charset>;
        static forName(charsetName: string): Charset;
        static createLegalCharsetNameRegex(): RegExp;
        __name: string;
        constructor(name: string, aliasesIgnored: string[]);
        name(): string;
        compareTo$java_nio_charset_Charset(that: Charset): number;
        /**
         *
         * @param {java.nio.charset.Charset} that
         * @return {number}
         */
        compareTo(that?: any): any;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
    namespace Charset {
        class AvailableCharsets {
            static CHARSETS: java.util.SortedMap<string, java.nio.charset.Charset>;
            constructor();
        }
    }
}
declare namespace java.nio.file {
    class Paths {
        constructor();
        static get(...paths: string[]): java.nio.file.Path;
    }
}
declare namespace java.security {
    /**
     * Message Digest Service Provider Interface - <a
     * href="http://java.sun.com/j2se/1.4.2/docs/api/java/security/MessageDigestSpi.html">[Sun's
     * docs]</a>.
     * @class
     */
    abstract class MessageDigestSpi {
        engineDigest$(): number[];
        engineDigest$byte_A$int$int(buf: number[], offset: number, len: number): number;
        engineDigest(buf?: any, offset?: any, len?: any): any;
        engineGetDigestLength(): number;
        abstract engineReset(): any;
        engineUpdate$byte(input: number): void;
        engineUpdate$byte_A$int$int(input: number[], offset: number, len: number): void;
        engineUpdate(input?: any, offset?: any, len?: any): any;
    }
}
declare namespace java.text {
    /**
     * A basic implementation for Java collators.
     *
     * @author Renaud Pawlak
     * @class
     */
    class Collator  {
        static collator: Collator;
        static getInstance(): Collator;
        /**
         *
         * @param {*} a
         * @param {*} b
         * @return {number}
         */
        compare(a: any, b: any): number;
        constructor();
    }
}
declare namespace java.util {
    /**
     * Skeletal implementation of the Collection interface. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/AbstractCollection.html">[Sun
     * docs]</a>
     *
     * @param <E> the element type.
     * @class
     */
    abstract class AbstractCollection<E> implements java.util.Collection<E> {
        stream(): java.util.stream.Stream<any>;
        forEach(action: (p1: any) => void): void;
        removeIf(filter: (p1: any) => boolean): boolean;
        constructor();
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        add(o: E): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        addAll(c: java.util.Collection<any>): boolean;
        /**
         *
         */
        clear(): void;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        contains(o: any): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        containsAll(c: java.util.Collection<any>): boolean;
        /**
         *
         * @return {boolean}
         */
        isEmpty(): boolean;
        /**
         *
         * @return {*}
         */
        abstract iterator(): java.util.Iterator<E>;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        remove(o: any): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        removeAll(c: java.util.Collection<any>): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        retainAll(c: java.util.Collection<any>): boolean;
        /**
         *
         * @return {number}
         */
        abstract size(): number;
        toArray$(): any[];
        toArray$java_lang_Object_A<T>(a: T[]): T[];
        /**
         *
         * @param {Array} a
         * @return {Array}
         */
        toArray<T>(a?: any): any;
        /**
         *
         * @return {string}
         */
        toString(): string;
        advanceToFind(o: any, remove: boolean): boolean;
    }
}
declare namespace java.util {
    /**
     * Basic {@link Map.Entry} implementation that implements hashCode, equals, and
     * toString.
     * @class
     */
    abstract class AbstractMapEntry<K, V> implements java.util.Map.Entry<K, V> {
        /**
         *
         * @param {*} other
         * @return {boolean}
         */
        equals(other: any): boolean;
        /**
         * Calculate the hash code using Sun's specified algorithm.
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
        abstract setValue(value?: any): any;
        abstract getValue(): any;
        abstract getKey(): any;
        constructor();
    }
}
declare namespace java.util {
    /**
     * Incomplete and naive implementation of the BitSet utility (mainly for
     * compatibility/compilation purpose).
     *
     * @author Renaud Pawlak
     * @param {number} nbits
     * @class
     */
    class BitSet implements java.lang.Cloneable, java.io.Serializable {
        bits: boolean[];
        constructor(nbits?: any);
        static valueOf(longs: number[]): BitSet;
        flip$int(bitIndex: number): void;
        flip$int$int(fromIndex: number, toIndex: number): void;
        flip(fromIndex?: any, toIndex?: any): any;
        set$int(bitIndex: number): void;
        set$int$boolean(bitIndex: number, value: boolean): void;
        set$int$int(fromIndex: number, toIndex: number): void;
        set$int$int$boolean(fromIndex: number, toIndex: number, value: boolean): void;
        set(fromIndex?: any, toIndex?: any, value?: any): any;
        clear$int(bitIndex: number): void;
        clear$int$int(fromIndex: number, toIndex: number): void;
        clear(fromIndex?: any, toIndex?: any): any;
        clear$(): void;
        get$int(bitIndex: number): boolean;
        get$int$int(fromIndex: number, toIndex: number): BitSet;
        get(fromIndex?: any, toIndex?: any): any;
        length(): number;
        isEmpty(): boolean;
        cardinality(): number;
        and(set: BitSet): void;
        or(set: BitSet): void;
        xor(set: BitSet): void;
        andNot(set: BitSet): void;
        size(): number;
        equals(obj: any): boolean;
        clone(): any;
    }
}
declare namespace java.util {
    /**
     * General-purpose interface for storing collections of objects. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/Collection.html">[Sun
     * docs]</a>
     *
     * @param <E> element type
     * @class
     */
    interface Collection<E> extends java.lang.Iterable<E> {
        add(o: E): boolean;
        addAll(c: Collection<any>): boolean;
        clear(): any;
        contains(o: any): boolean;
        containsAll(c: Collection<any>): boolean;
        isEmpty(): boolean;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<E>;
        remove(o: any): boolean;
        removeAll(c: Collection<any>): boolean;
        removeIf(filter?: any): any;
        retainAll(c: Collection<any>): boolean;
        size(): number;
        toArray<T>(a?: any): any;
        stream(): java.util.stream.Stream<E>;
    }
}
declare namespace java.util {
    /**
     * An interface used a basis for implementing custom ordering. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/Comparator.html">[Sun
     * docs]</a>
     *
     * @param <T> the type to be compared.
     * @class
     */
    interface Comparator<T> {
        (a: T, b: T): number;
    }
}
declare namespace java.util {
    class Comparators {
        /**
         * Compares two Objects according to their <i>natural ordering</i>.
         *
         * @see java.lang.Comparable
         */
        static NATURAL: java.util.Comparator<any>;
        static NATURAL_$LI$(): java.util.Comparator<any>;
        /**
         * Returns the natural Comparator.
         * <p>
         * Example:
         *
         * <pre>Comparator&lt;String&gt; compareString = Comparators.natural()</pre>
         *
         * @return {*} the natural Comparator
         */
        static natural<T>(): java.util.Comparator<T>;
    }
    namespace Comparators {
        class NaturalComparator {
            /**
             *
             * @param {*} o1
             * @param {*} o2
             * @return {number}
             */
            compare(o1: any, o2: any): number;
            constructor();
        }
    }
}
declare namespace java.util.concurrent {
    interface Callable<V> {
        (): V;
    }
}
declare namespace java.util {
    /**
     * Represents a date and time.
     * @param {number} year
     * @param {number} month
     * @param {number} date
     * @param {number} hrs
     * @param {number} min
     * @param {number} sec
     * @class
     */
    class Date implements java.lang.Cloneable, java.lang.Comparable<Date>, java.io.Serializable {
        static parse(s: string): number;
        static UTC(year: number, month: number, date: number, hrs: number, min: number, sec: number): number;
        /**
         * Ensure a number is displayed with two digits.
         *
         * @return {string} a two-character base 10 representation of the number
         * @param {number} number
         */
        static padTwo(number: number): string;
        /**
         * JavaScript Date instance.
         */
        jsdate: Object;
        static jsdateClass(): Object;
        constructor(year?: any, month?: any, date?: any, hrs?: any, min?: any, sec?: any);
        after(when: Date): boolean;
        before(when: Date): boolean;
        clone(): any;
        compareTo$java_util_Date(other: Date): number;
        /**
         *
         * @param {java.util.Date} other
         * @return {number}
         */
        compareTo(other?: any): any;
        /**
         *
         * @param {*} obj
         * @return {boolean}
         */
        equals(obj: any): boolean;
        getDate(): number;
        getDay(): number;
        getHours(): number;
        getMinutes(): number;
        getMonth(): number;
        getSeconds(): number;
        getTime(): number;
        getTimezoneOffset(): number;
        getYear(): number;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        setDate(date: number): void;
        setHours(hours: number): void;
        setMinutes(minutes: number): void;
        setMonth(month: number): void;
        setSeconds(seconds: number): void;
        setTime(time: number): void;
        setYear(year: number): void;
        toGMTString(): string;
        toLocaleString(): string;
        /**
         *
         * @return {string}
         */
        toString(): string;
        static ONE_HOUR_IN_MILLISECONDS: number;
        /**
         * Detects if the requested time falls into a non-existent time range due to
         * local time advancing into daylight savings time or is ambiguous due to
         * going out of daylight savings. If so, adjust accordingly.
         * @param {number} requestedHours
         * @private
         */
        fixDaylightSavings(requestedHours: number): void;
    }
    namespace Date {
        /**
         * Encapsulates static data to avoid Date itself having a static
         * initializer.
         * @class
         */
        class StringData {
            static DAYS: string[];
            static DAYS_$LI$(): string[];
            static MONTHS: string[];
            static MONTHS_$LI$(): string[];
            constructor();
        }
    }
}
declare namespace java.util {
    /**
     * A collection designed for holding elements prior to processing. <a
     * href="http://docs.oracle.com/javase/6/docs/api/java/util/Deque.html">Deque</a>
     *
     * @param <E> element type.
     * @class
     */
    interface Deque<E> extends java.util.Queue<E> {
        addFirst(e: E): any;
        addLast(e: E): any;
        descendingIterator(): java.util.Iterator<E>;
        getFirst(): E;
        getLast(): E;
        offerFirst(e: E): boolean;
        offerLast(e: E): boolean;
        peekFirst(): E;
        peekLast(): E;
        pollFirst(): E;
        pollLast(): E;
        pop(): E;
        push(e: E): any;
        removeFirst(): E;
        removeFirstOccurrence(o: any): boolean;
        removeLast(): E;
        removeLastOccurrence(o: any): boolean;
    }
}
declare namespace java.util {
    interface Dictionary<K, V> {
        /**
         * Returns the number of entries (distinct keys) in this dictionary.
         *
         * @return  {number} the number of keys in this dictionary.
         */
        size(): number;
        /**
         * Tests if this dictionary maps no keys to value. The general contract
         * for the <tt>isEmpty</tt> method is that the result is true if and only
         * if this dictionary contains no entries.
         *
         * @return  {boolean} <code>true</code> if this dictionary maps no keys to values;
         * <code>false</code> otherwise.
         */
        isEmpty(): boolean;
        /**
         * Returns an enumeration of the keys in this dictionary. The general
         * contract for the keys method is that an <tt>Enumeration</tt> object
         * is returned that will generate all the keys for which this dictionary
         * contains entries.
         *
         * @return  {*} an enumeration of the keys in this dictionary.
         * @see     java.util.Dictionary#elements()
         * @see     java.util.Enumeration
         */
        keys(): java.util.Enumeration<K>;
        /**
         * Returns an enumeration of the values in this dictionary. The general
         * contract for the <tt>elements</tt> method is that an
         * <tt>Enumeration</tt> is returned that will generate all the elements
         * contained in entries in this dictionary.
         *
         * @return  {*} an enumeration of the values in this dictionary.
         * @see     java.util.Dictionary#keys()
         * @see     java.util.Enumeration
         */
        elements(): java.util.Enumeration<V>;
        /**
         * Returns the value to which the key is mapped in this dictionary.
         * The general contract for the <tt>isEmpty</tt> method is that if this
         * dictionary contains an entry for the specified key, the associated
         * value is returned; otherwise, <tt>null</tt> is returned.
         *
         * @return  {*} the value to which the key is mapped in this dictionary;
         * @param   {*} key   a key in this dictionary.
         * <code>null</code> if the key is not mapped to any value in
         * this dictionary.
         * @exception NullPointerException if the <tt>key</tt> is <tt>null</tt>.
         * @see     java.util.Dictionary#put(java.lang.Object, java.lang.Object)
         */
        get(key: any): V;
        /**
         * Maps the specified <code>key</code> to the specified
         * <code>value</code> in this dictionary. Neither the key nor the
         * value can be <code>null</code>.
         * <p>
         * If this dictionary already contains an entry for the specified
         * <tt>key</tt>, the value already in this dictionary for that
         * <tt>key</tt> is returned, after modifying the entry to contain the
         * new element. <p>If this dictionary does not already have an entry
         * for the specified <tt>key</tt>, an entry is created for the
         * specified <tt>key</tt> and <tt>value</tt>, and <tt>null</tt> is
         * returned.
         * <p>
         * The <code>value</code> can be retrieved by calling the
         * <code>get</code> method with a <code>key</code> that is equal to
         * the original <code>key</code>.
         *
         * @param      {*} key     the hashtable key.
         * @param      {*} value   the value.
         * @return     {*} the previous value to which the <code>key</code> was mapped
         * in this dictionary, or <code>null</code> if the key did not
         * have a previous mapping.
         * @exception  NullPointerException  if the <code>key</code> or
         * <code>value</code> is <code>null</code>.
         * @see        java.lang.Object#equals(java.lang.Object)
         * @see        java.util.Dictionary#get(java.lang.Object)
         */
        put(key: K, value: V): V;
        /**
         * Removes the <code>key</code> (and its corresponding
         * <code>value</code>) from this dictionary. This method does nothing
         * if the <code>key</code> is not in this dictionary.
         *
         * @param   {*} key   the key that needs to be removed.
         * @return  {*} the value to which the <code>key</code> had been mapped in this
         * dictionary, or <code>null</code> if the key did not have a
         * mapping.
         * @exception NullPointerException if <tt>key</tt> is <tt>null</tt>.
         */
        remove(key: any): V;
    }
}
declare namespace java.util {
    /**
     * An interface to generate a series of elements, one at a time. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/Enumeration.html">[Sun
     * docs]</a>
     *
     * @param <E> the type being enumerated.
     * @class
     */
    interface Enumeration<E> {
        hasMoreElements(): boolean;
        nextElement(): E;
    }
}
declare namespace java.util {
    /**
     * A tag interface that other "listener" interfaces can extend to indicate their
     * adherence to the observer pattern.
     * @class
     */
    interface EventListener {
    }
}
declare namespace java.util {
    abstract class EventListenerProxy<T extends java.util.EventListener> implements java.util.EventListener {
        listener: T;
        constructor(listener: T);
        getListener(): T;
    }
}
declare namespace java.util {
    /**
     * Available as a superclass of event objects.
     * @param {*} source
     * @class
     */
    class EventObject {
        source: any;
        constructor(source: any);
        getSource(): any;
    }
}
declare namespace java.util {
    /**
     * A simple wrapper around JavaScriptObject to provide {@link java.util.Map}-like semantics for any
     * key type.
     * <p>
     * Implementation notes:
     * <p>
     * A key's hashCode is the index in backingMap which should contain that key. Since several keys may
     * have the same hash, each value in hashCodeMap is actually an array containing all entries whose
     * keys share the same hash.
     * @param {java.util.AbstractHashMap} host
     * @class
     */
    class InternalHashCodeMap<K, V> implements java.lang.Iterable<java.util.Map.Entry<K, V>> {
        forEach(action: (p1: any) => void): void;
        backingMap: java.util.InternalJsMap<any>;
        host: java.util.AbstractHashMap<K, V>;
        __size: number;
        constructor(host: java.util.AbstractHashMap<K, V>);
        put(key: K, value: V): V;
        remove(key: any): V;
        getEntry(key: any): java.util.Map.Entry<K, V>;
        findEntryInChain(key: any, chain: java.util.Map.Entry<K, V>[]): java.util.Map.Entry<K, V>;
        size(): number;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<java.util.Map.Entry<K, V>>;
        getChainOrEmpty(hashCode: number): java.util.Map.Entry<K, V>[];
        newEntryChain(): java.util.Map.Entry<K, V>[];
        unsafeCastToArray(arr: any): java.util.Map.Entry<K, V>[];
        /**
         * Returns hash code of the key as calculated by {@link AbstractHashMap#getHashCode(Object)} but
         * also handles null keys as well.
         * @param {*} key
         * @return {number}
         * @private
         */
        hash(key: any): number;
    }
    namespace InternalHashCodeMap {
        class InternalHashCodeMap$0 implements java.util.Iterator<java.util.Map.Entry<any, any>> {
            __parent: any;
            forEachRemaining(consumer: (p1: any) => void): void;
            chains: java.util.InternalJsMap.Iterator<any>;
            itemIndex: number;
            chain: java.util.Map.Entry<any, any>[];
            lastEntry: java.util.Map.Entry<any, any>;
            /**
             *
             * @return {boolean}
             */
            hasNext(): boolean;
            /**
             *
             * @return {*}
             */
            next(): java.util.Map.Entry<any, any>;
            /**
             *
             */
            remove(): void;
            constructor(__parent: any);
        }
    }
}
declare namespace java.util {
    class InternalJsMap<V> {
        get(key: number): V;
        get(key: string): V;
        set(key: number, value: V): any;
        set(key: string, value: V): any;
        delete(key: number): any;
        delete(key: string): any;
        entries(): InternalJsMap.Iterator<V>;
    }
    namespace InternalJsMap {
        class Iterator<V> {
            next(): InternalJsMap.IteratorEntry<V>;
        }
        class IteratorEntry<V> {
            value: any[];
            done: boolean;
        }
    }
}
declare namespace java.util {
    /**
     * A factory to create JavaScript Map instances.
     * @class
     */
    class InternalJsMapFactory {
        static jsMapCtor: any;
        static jsMapCtor_$LI$(): any;
        static getJsMapConstructor(): any;
        static newJsMap<V>(): java.util.InternalJsMap<V>;
        constructor();
    }
}
declare namespace java.util {
    /**
     * See <a href="https://docs.oracle.com/javase/8/docs/api/java/util/Iterator.html">
     * the official Java API doc</a> for details.
     *
     * @param <E> element type
     * @class
     */
    interface Iterator<E> {
        hasNext(): boolean;
        next(): E;
        forEachRemaining(consumer?: any): any;
        remove(): any;
    }
}
declare namespace java.util {
    /**
     * Represents a sequence of objects. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/List.html">[Sun docs]</a>
     *
     * @param <E> element type
     * @class
     */
    interface List<E> extends java.util.Collection<E> {
        add(index?: any, element?: any): any;
        addAll(index?: any, c?: any): any;
        /**
         *
         */
        clear(): any;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        contains(o: any): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        containsAll(c: java.util.Collection<any>): boolean;
        get(index: number): E;
        indexOf(o: any): number;
        /**
         *
         * @return {boolean}
         */
        isEmpty(): boolean;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<E>;
        lastIndexOf(o: any): number;
        listIterator(from?: any): any;
        remove(index?: any): any;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        removeAll(c: java.util.Collection<any>): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        retainAll(c: java.util.Collection<any>): boolean;
        set(index: number, element: E): E;
        /**
         *
         * @return {number}
         */
        size(): number;
        subList(fromIndex: number, toIndex: number): List<E>;
        /**
         *
         * @param {Array} array
         * @return {Array}
         */
        toArray<T>(array?: any): any;
    }
}
declare namespace java.util {
    /**
     * Uses Java 1.5 ListIterator for documentation. The methods hasNext, next, and
     * remove are repeated to allow the specialized ListIterator documentation to be
     * associated with them. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/ListIterator.html">[Sun
     * docs]</a>
     *
     * @param <E> element type.
     * @class
     */
    interface ListIterator<E> extends java.util.Iterator<E> {
        add(o: E): any;
        /**
         *
         * @return {boolean}
         */
        hasNext(): boolean;
        hasPrevious(): boolean;
        /**
         *
         * @return {*}
         */
        next(): E;
        nextIndex(): number;
        previous(): E;
        previousIndex(): number;
        /**
         *
         */
        remove(): any;
        set(o: E): any;
    }
}
declare namespace java.util {
    /**
     * A very simple emulation of Locale for shared-code patterns like
     * {@code String.toUpperCase(Locale.US)}.
     * <p>
     * Note: Any changes to this class should put into account the assumption that
     * was made in rest of the JRE emulation.
     * @class
     */
    class Locale {
        static ROOT: Locale;
        static ROOT_$LI$(): Locale;
        static ENGLISH: Locale;
        static ENGLISH_$LI$(): Locale;
        static US: Locale;
        static US_$LI$(): Locale;
        static defaultLocale: Locale;
        static defaultLocale_$LI$(): Locale;
        /**
         * Returns an instance that represents the browser's default locale (not
         * necessarily the one defined by 'gwt.locale').
         * @return {java.util.Locale}
         */
        static getDefault(): Locale;
        constructor();
    }
    namespace Locale {
        class RootLocale extends java.util.Locale {
            /**
             *
             * @return {string}
             */
            toString(): string;
            constructor();
        }
        class EnglishLocale extends java.util.Locale {
            /**
             *
             * @return {string}
             */
            toString(): string;
            constructor();
        }
        class USLocale extends java.util.Locale {
            /**
             *
             * @return {string}
             */
            toString(): string;
            constructor();
        }
        class DefaultLocale extends java.util.Locale {
            /**
             *
             * @return {string}
             */
            toString(): string;
            constructor();
        }
    }
}
declare namespace java.util.logging {
    /**
     * An emulation of the java.util.logging.Formatter class. See
     * <a href="http://java.sun.com/j2se/1.4.2/docs/api/java/util/logging/Formatter.html">
     * The Java API doc for details</a>
     * @class
     */
    abstract class Formatter {
        abstract format(record: java.util.logging.LogRecord): string;
        formatMessage(record: java.util.logging.LogRecord): string;
    }
}
declare namespace java.util.logging {
    /**
     * An emulation of the java.util.logging.Handler class. See
     * <a href="http://java.sun.com/j2se/1.4.2/docs/api/java/util/logging/Handler.html">
     * The Java API doc for details</a>
     * @class
     */
    abstract class Handler {
        formatter: java.util.logging.Formatter;
        level: java.util.logging.Level;
        abstract close(): any;
        abstract flush(): any;
        getFormatter(): java.util.logging.Formatter;
        getLevel(): java.util.logging.Level;
        isLoggable(record: java.util.logging.LogRecord): boolean;
        abstract publish(record: java.util.logging.LogRecord): any;
        setFormatter(newFormatter: java.util.logging.Formatter): void;
        setLevel(newLevel: java.util.logging.Level): void;
        constructor();
    }
}
declare namespace java.util.logging {
    /**
     * An emulation of the java.util.logging.Level class. See
     * <a href="http://java.sun.com/j2se/1.4.2/docs/api/java/util/logging/Level.html">
     * The Java API doc for details</a>
     * @class
     */
    class Level implements java.io.Serializable {
        static ALL: Level;
        static ALL_$LI$(): Level;
        static CONFIG: Level;
        static CONFIG_$LI$(): Level;
        static FINE: Level;
        static FINE_$LI$(): Level;
        static FINER: Level;
        static FINER_$LI$(): Level;
        static FINEST: Level;
        static FINEST_$LI$(): Level;
        static INFO: Level;
        static INFO_$LI$(): Level;
        static OFF: Level;
        static OFF_$LI$(): Level;
        static SEVERE: Level;
        static SEVERE_$LI$(): Level;
        static WARNING: Level;
        static WARNING_$LI$(): Level;
        static parse(name: string): Level;
        constructor();
        getName(): string;
        intValue(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
    namespace Level {
        class LevelAll extends java.util.logging.Level {
            /**
             *
             * @return {string}
             */
            getName(): string;
            /**
             *
             * @return {number}
             */
            intValue(): number;
            constructor();
        }
        class LevelConfig extends java.util.logging.Level {
            /**
             *
             * @return {string}
             */
            getName(): string;
            /**
             *
             * @return {number}
             */
            intValue(): number;
            constructor();
        }
        class LevelFine extends java.util.logging.Level {
            /**
             *
             * @return {string}
             */
            getName(): string;
            /**
             *
             * @return {number}
             */
            intValue(): number;
            constructor();
        }
        class LevelFiner extends java.util.logging.Level {
            /**
             *
             * @return {string}
             */
            getName(): string;
            /**
             *
             * @return {number}
             */
            intValue(): number;
            constructor();
        }
        class LevelFinest extends java.util.logging.Level {
            /**
             *
             * @return {string}
             */
            getName(): string;
            /**
             *
             * @return {number}
             */
            intValue(): number;
            constructor();
        }
        class LevelInfo extends java.util.logging.Level {
            /**
             *
             * @return {string}
             */
            getName(): string;
            /**
             *
             * @return {number}
             */
            intValue(): number;
            constructor();
        }
        class LevelOff extends java.util.logging.Level {
            /**
             *
             * @return {string}
             */
            getName(): string;
            /**
             *
             * @return {number}
             */
            intValue(): number;
            constructor();
        }
        class LevelSevere extends java.util.logging.Level {
            /**
             *
             * @return {string}
             */
            getName(): string;
            /**
             *
             * @return {number}
             */
            intValue(): number;
            constructor();
        }
        class LevelWarning extends java.util.logging.Level {
            /**
             *
             * @return {string}
             */
            getName(): string;
            /**
             *
             * @return {number}
             */
            intValue(): number;
            constructor();
        }
    }
}
declare namespace java.util.logging {
    /**
     * An emulation of the java.util.logging.LogManager class. See
     * <a href="http://java.sun.com/j2se/1.4.2/docs/api/java/util/logging/LogManger.html">
     * The Java API doc for details</a>
     * @class
     */
    class LogManager {
        static singleton: LogManager;
        static getLogManager(): LogManager;
        loggerMap: java.util.HashMap<string, java.util.logging.Logger>;
        constructor();
        addLogger(logger: java.util.logging.Logger): boolean;
        getLogger(name: string): java.util.logging.Logger;
        getLoggerNames(): java.util.Enumeration<string>;
        /**
         * Helper function to add a logger when we have already determined that it
         * does not exist.  When we add a logger, we recursively add all of it's
         * ancestors. Since loggers do not get removed, logger creation is cheap,
         * and there are not usually too many loggers in an ancestry chain,
         * this is a simple way to ensure that the parent/child relationships are
         * always correctly set up.
         * @param {java.util.logging.Logger} logger
         * @private
         */
        addLoggerAndEnsureParents(logger: java.util.logging.Logger): void;
        addLoggerImpl(logger: java.util.logging.Logger): void;
        /**
         * Helper function to create a logger if it does not exist since the public
         * APIs for getLogger and addLogger make it difficult to use those functions
         * for this.
         * @param {string} name
         * @return {java.util.logging.Logger}
         */
        ensureLogger(name: string): java.util.logging.Logger;
    }
}
declare namespace java.util.logging {
    /**
     * An emulation of the java.util.logging.LogRecord class. See
     * <a href="http://java.sun.com/j2se/1.4.2/docs/api/java/util/logging/LogRecord.html">
     * The Java API doc for details</a>
     * @param {java.util.logging.Level} level
     * @param {string} msg
     * @class
     */
    class LogRecord implements java.io.Serializable {
        level: java.util.logging.Level;
        loggerName: string;
        msg: string;
        thrown: Error;
        millis: number;
        constructor(level?: any, msg?: any);
        getLevel(): java.util.logging.Level;
        getLoggerName(): string;
        getMessage(): string;
        getMillis(): number;
        getThrown(): Error;
        setLevel(newLevel: java.util.logging.Level): void;
        setLoggerName(newName: string): void;
        setMessage(newMessage: string): void;
        setMillis(newMillis: number): void;
        setThrown(newThrown: Error): void;
    }
}
declare namespace java.util {
    /**
     * Abstract interface for maps.
     *
     * @param <K> key type.
     * @param <V> value type.
     * @class
     */
    interface Map<K, V> {
        clear(): any;
        containsKey(key: any): boolean;
        containsValue(value: any): boolean;
        entrySet(): java.util.Set<Map.Entry<K, V>>;
        get(key: any): V;
        isEmpty(): boolean;
        keySet(): java.util.Set<K>;
        put(key: K, value: V): V;
        putAll(t: Map<any, any>): any;
        remove(key: any): V;
        size(): number;
        values(): java.util.Collection<V>;
        merge(key?: any, value?: any, map?: any): any;
        computeIfAbsent(key?: any, mappingFunction?: any): any;
    }
    namespace Map {
        /**
         * Represents an individual map entry.
         * @class
         */
        interface Entry<K, V> {
            getKey(): K;
            getValue(): V;
            setValue(value: V): V;
        }
    }
}
declare namespace java.util {
    /**
     * Sorted map providing additional query operations and views.
     *
     * @param <K> key type.
     * @param <V> value type.
     * @class
     */
    interface NavigableMap<K, V> extends java.util.SortedMap<K, V> {
        ceilingEntry(key: K): java.util.Map.Entry<K, V>;
        ceilingKey(key: K): K;
        descendingKeySet(): java.util.NavigableSet<K>;
        descendingMap(): NavigableMap<K, V>;
        firstEntry(): java.util.Map.Entry<K, V>;
        floorEntry(key: K): java.util.Map.Entry<K, V>;
        floorKey(key: K): K;
        headMap(toKey?: any, inclusive?: any): any;
        higherEntry(key: K): java.util.Map.Entry<K, V>;
        higherKey(key: K): K;
        lastEntry(): java.util.Map.Entry<K, V>;
        lowerEntry(key: K): java.util.Map.Entry<K, V>;
        lowerKey(key: K): K;
        navigableKeySet(): java.util.NavigableSet<K>;
        pollFirstEntry(): java.util.Map.Entry<K, V>;
        pollLastEntry(): java.util.Map.Entry<K, V>;
        subMap(fromKey?: any, fromInclusive?: any, toKey?: any, toInclusive?: any): any;
        tailMap(fromKey?: any, inclusive?: any): any;
    }
}
declare namespace java.util {
    /**
     * A {@code SortedSet} with more flexible queries.
     *
     * @param <E> element type.
     * @class
     */
    interface NavigableSet<E> extends java.util.SortedSet<E> {
        ceiling(e: E): E;
        descendingIterator(): java.util.Iterator<E>;
        descendingSet(): NavigableSet<E>;
        floor(e: E): E;
        headSet(toElement?: any, inclusive?: any): any;
        higher(e: E): E;
        lower(e: E): E;
        pollFirst(): E;
        pollLast(): E;
        subSet(fromElement?: any, fromInclusive?: any, toElement?: any, toInclusive?: any): any;
        tailSet(fromElement?: any, inclusive?: any): any;
    }
}
declare namespace java.util {
    /**
     * See <a
     * href="http://docs.oracle.com/javase/7/docs/api/java/util/Objects.html">the
     * official Java API doc</a> for details.
     * @class
     */
    class Objects {
        constructor();
        static compare<T>(a: T, b: T, c: java.util.Comparator<any>): number;
        static deepEquals(a: any, b: any): boolean;
        static equals(a: any, b: any): boolean;
        static hashCode(o: any): number;
        static hash(...values: any[]): number;
        static isNull(obj: any): boolean;
        static nonNull(obj: any): boolean;
        static requireNonNull$java_lang_Object<T>(obj: T): T;
        static requireNonNull$java_lang_Object$java_lang_String<T>(obj: T, message: string): T;
        static requireNonNull<T>(obj?: any, message?: any): any;
        static requireNonNull$java_lang_Object$java_util_function_Supplier<T>(obj: T, messageSupplier: () => string): T;
        static toString$java_lang_Object(o: any): string;
        static toString$java_lang_Object$java_lang_String(o: any, nullDefault: string): string;
        static toString(o?: any, nullDefault?: any): any;
    }
}
declare namespace java.util {
    /**
     * Construct an Observable with zero Observers.
     * @class
     */
    class Observable {
        changed: boolean;
        obs: java.util.Vector<java.util.Observer>;
        constructor();
        /**
         * Adds an observer to the set of observers for this object, provided that
         * it is not the same as some observer already in the set. The order in
         * which notifications will be delivered to multiple observers is not
         * specified. See the class comment.
         *
         * @param {*} o
         * an observer to be added.
         * @throws NullPointerException
         * if the parameter o is null.
         */
        addObserver(o: java.util.Observer): void;
        /**
         * Deletes an observer from the set of observers of this object. Passing
         * <CODE>null</CODE> to this method will have no effect.
         *
         * @param {*} o
         * the observer to be deleted.
         */
        deleteObserver(o: java.util.Observer): void;
        notifyObservers$(): void;
        notifyObservers$java_lang_Object(arg: any): void;
        /**
         * If this object has changed, as indicated by the <code>hasChanged</code>
         * method, then notify all of its observers and then call the
         * <code>clearChanged</code> method to indicate that this object has no
         * longer changed.
         * <p>
         * Each observer has its <code>update</code> method called with two
         * arguments: this observable object and the <code>arg</code> argument.
         *
         * @param {*} arg
         * any object.
         * @see java.util.Observable#clearChanged()
         * @see java.util.Observable#hasChanged()
         * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
         */
        notifyObservers(arg?: any): any;
        /**
         * Clears the observer list so that this object no longer has any observers.
         */
        deleteObservers(): void;
        /**
         * Marks this <tt>Observable</tt> object as having been changed; the
         * <tt>hasChanged</tt> method will now return <tt>true</tt>.
         */
        setChanged(): void;
        /**
         * Indicates that this object has no longer changed, or that it has already
         * notified all of its observers of its most recent change, so that the
         * <tt>hasChanged</tt> method will now return <tt>false</tt>. This method is
         * called automatically by the <code>notifyObservers</code> methods.
         *
         * @see java.util.Observable#notifyObservers()
         * @see java.util.Observable#notifyObservers(java.lang.Object)
         */
        clearChanged(): void;
        /**
         * Tests if this object has changed.
         *
         * @return {boolean} <code>true</code> if and only if the <code>setChanged</code>
         * method has been called more recently than the
         * <code>clearChanged</code> method on this object;
         * <code>false</code> otherwise.
         * @see java.util.Observable#clearChanged()
         * @see java.util.Observable#setChanged()
         */
        hasChanged(): boolean;
        /**
         * Returns the number of observers of this <tt>Observable</tt> object.
         *
         * @return {number} the number of observers of this object.
         */
        countObservers(): number;
    }
}
declare namespace java.util {
    /**
     * Implementation of the observer interface
     * @class
     */
    interface Observer {
        /**
         * This method is called whenever the observed object is changed. An
         * application calls an <tt>Observable</tt> object's
         * <code>notifyObservers</code> method to have all the object's
         * observers notified of the change.
         *
         * @param   {java.util.Observable} o     the observable object.
         * @param   {*} arg   an argument passed to the <code>notifyObservers</code>
         * method.
         */
        update(o: java.util.Observable, arg: any): any;
    }
}
declare namespace java.util {
    /**
     * See <a href="https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html">
     * the official Java API doc</a> for details.
     *
     * @param <T> type of the wrapped reference
     * @class
     */
    class Optional<T> {
        static empty<T>(): Optional<T>;
        static of<T>(value: T): Optional<T>;
        static ofNullable<T>(value: T): Optional<T>;
        static EMPTY: Optional<any>;
        static EMPTY_$LI$(): Optional<any>;
        ref: T;
        constructor(ref?: any);
        isPresent(): boolean;
        get(): T;
        ifPresent(consumer: (p1: any) => void): void;
        filter(predicate: (p1: any) => boolean): Optional<T>;
        map<U>(mapper: (p1: any) => any): Optional<U>;
        flatMap<U>(mapper: (p1: any) => Optional<U>): Optional<U>;
        orElse(other: T): T;
        orElseGet(other: () => any): T;
        orElseThrow<X extends Error>(exceptionSupplier: () => any): T;
        /**
         *
         * @param {*} obj
         * @return {boolean}
         */
        equals(obj: any): boolean;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
}
declare namespace java.util {
    /**
     * See <a href="https://docs.oracle.com/javase/8/docs/api/java/util/OptionalDouble.html">
     * the official Java API doc</a> for details.
     * @class
     */
    class OptionalDouble {
        static empty(): OptionalDouble;
        static of(value: number): OptionalDouble;
        static EMPTY: OptionalDouble;
        static EMPTY_$LI$(): OptionalDouble;
        ref: number;
        present: boolean;
        constructor(value?: any);
        isPresent(): boolean;
        getAsDouble(): number;
        ifPresent(consumer: any): void;
        orElse(other: number): number;
        orElseGet(other: any): number;
        orElseThrow<X extends Error>(exceptionSupplier: () => X): number;
        /**
         *
         * @param {*} obj
         * @return {boolean}
         */
        equals(obj: any): boolean;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
}
declare namespace java.util {
    /**
     * See <a href="https://docs.oracle.com/javase/8/docs/api/java/util/OptionalInt.html">
     * the official Java API doc</a> for details.
     * @class
     */
    class OptionalInt {
        static empty(): OptionalInt;
        static of(value: number): OptionalInt;
        static EMPTY: OptionalInt;
        static EMPTY_$LI$(): OptionalInt;
        ref: number;
        present: boolean;
        constructor(value?: any);
        isPresent(): boolean;
        getAsInt(): number;
        ifPresent(consumer: any): void;
        orElse(other: number): number;
        orElseGet(other: any): number;
        orElseThrow<X extends Error>(exceptionSupplier: () => X): number;
        /**
         *
         * @param {*} obj
         * @return {boolean}
         */
        equals(obj: any): boolean;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
}
declare namespace java.util {
    /**
     * See <a href="https://docs.oracle.com/javase/8/docs/api/java/util/OptionalLong.html">
     * the official Java API doc</a> for details.
     * @class
     */
    class OptionalLong {
        static empty(): OptionalLong;
        static of(value: number): OptionalLong;
        static EMPTY: OptionalLong;
        static EMPTY_$LI$(): OptionalLong;
        ref: number;
        present: boolean;
        constructor(value?: any);
        isPresent(): boolean;
        getAsLong(): number;
        ifPresent(consumer: any): void;
        orElse(other: number): number;
        orElseGet(other: any): number;
        orElseThrow<X extends Error>(exceptionSupplier: () => X): number;
        /**
         *
         * @param {*} obj
         * @return {boolean}
         */
        equals(obj: any): boolean;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
}
declare namespace java.util {
    /**
     * See <a href="https://docs.oracle.com/javase/8/docs/api/java/util/PrimitiveIterator.html">
     * the official Java API doc</a> for details.
     *
     * @param <T> element type
     * @param <C> consumer type
     * @class
     */
    interface PrimitiveIterator<T, C> extends java.util.Iterator<T> {
        forEachRemaining(consumer?: any): any;
    }
    namespace PrimitiveIterator {
        /**
         * See <a href="https://docs.oracle.com/javase/8/docs/api/java/util/PrimitiveIterator.OfDouble.html">
         * the official Java API doc</a> for details.
         * @class
         */
        interface OfDouble extends java.util.PrimitiveIterator<number, any> {
            nextDouble(): number;
            /**
             *
             * @return {number}
             */
            next(): any;
            /**
             *
             * @param {(number) => void} consumer
             */
            forEachRemaining(consumer?: any): any;
        }
        /**
         * See <a href="https://docs.oracle.com/javase/8/docs/api/java/util/PrimitiveIterator.OfInt.html">
         * the official Java API doc</a> for details.
         * @class
         */
        interface OfInt extends java.util.PrimitiveIterator<number, any> {
            nextInt(): number;
            /**
             *
             * @return {number}
             */
            next(): any;
            /**
             *
             * @param {(number) => void} consumer
             */
            forEachRemaining(consumer?: any): any;
        }
        /**
         * See <a href="https://docs.oracle.com/javase/8/docs/api/java/util/PrimitiveIterator.OfLong.html">
         * the official Java API doc</a> for details.
         * @class
         */
        interface OfLong extends java.util.PrimitiveIterator<number, any> {
            nextLong(): number;
            /**
             *
             * @return {number}
             */
            next(): any;
            /**
             *
             * @param {(number) => void} consumer
             */
            forEachRemaining(consumer?: any): any;
        }
    }
}
declare namespace java.util {
    /**
     * A collection designed for holding elements prior to processing. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/Queue.html">[Sun
     * docs]</a>
     *
     * @param <E> element type.
     * @class
     */
    interface Queue<E> extends java.util.Collection<E> {
        element(): E;
        offer(o: E): boolean;
        peek(): E;
        poll(): E;
        remove(o?: any): any;
    }
}
declare namespace java.util {
    /**
     * Construct a random generator with the given {@code seed} as the initial
     * state.
     *
     * @param {number} seed the seed that will determine the initial state of this random
     * number generator.
     * @see #setSeed
     * @class
     */
    class Random {
        static __static_initialized: boolean;
        static __static_initialize(): void;
        static multiplierHi: number;
        static multiplierLo: number;
        static twoToThe24: number;
        static twoToThe31: number;
        static twoToThe32: number;
        static twoToTheMinus24: number;
        static twoToTheMinus26: number;
        static twoToTheMinus31: number;
        static twoToTheMinus53: number;
        static twoToTheXMinus24: number[];
        static twoToTheXMinus24_$LI$(): number[];
        static twoToTheXMinus48: number[];
        static twoToTheXMinus48_$LI$(): number[];
        /**
         * A value used to avoid two random number generators produced at the same
         * time having the same seed.
         */
        static uniqueSeed: number;
        static __static_initializer_0(): void;
        /**
         * The boolean value indicating if the second Gaussian number is available.
         */
        haveNextNextGaussian: boolean;
        /**
         * The second Gaussian generated number.
         */
        nextNextGaussian: number;
        /**
         * The high 24 bits of the 48=bit seed value.
         */
        seedhi: number;
        /**
         * The low 24 bits of the 48=bit seed value.
         */
        seedlo: number;
        constructor(seed?: any);
        /**
         * Returns the next pseudo-random, uniformly distributed {@code boolean} value
         * generated by this generator.
         *
         * @return {boolean} a pseudo-random, uniformly distributed boolean value.
         */
        nextBoolean(): boolean;
        /**
         * Modifies the {@code byte} array by a random sequence of {@code byte}s
         * generated by this random number generator.
         *
         * @param {Array} buf non-null array to contain the new random {@code byte}s.
         * @see #next
         */
        nextBytes(buf: number[]): void;
        /**
         * Generates a normally distributed random {@code double} number between 0.0
         * inclusively and 1.0 exclusively.
         *
         * @return {number} a random {@code double} in the range [0.0 - 1.0)
         * @see #nextFloat
         */
        nextDouble(): number;
        /**
         * Generates a normally distributed random {@code float} number between 0.0
         * inclusively and 1.0 exclusively.
         *
         * @return {number} float a random {@code float} number between [0.0 and 1.0)
         * @see #nextDouble
         */
        nextFloat(): number;
        /**
         * Pseudo-randomly generates (approximately) a normally distributed {@code
         * double} value with mean 0.0 and a standard deviation value of {@code 1.0}
         * using the <i>polar method<i> of G. E. P. Box, M. E. Muller, and G.
         * Marsaglia, as described by Donald E. Knuth in <i>The Art of Computer
         * Programming, Volume 2: Seminumerical Algorithms</i>, section 3.4.1,
         * subsection C, algorithm P.
         *
         * @return {number} a random {@code double}
         * @see #nextDouble
         */
        nextGaussian(): number;
        nextInt$(): number;
        nextInt$int(n: number): number;
        /**
         * Returns a new pseudo-random {@code int} value which is uniformly
         * distributed between 0 (inclusively) and the value of {@code n}
         * (exclusively).
         *
         * @param {number} n the exclusive upper border of the range [0 - n).
         * @return {number} a random {@code int}.
         */
        nextInt(n?: any): any;
        /**
         * Generates a uniformly distributed 64-bit integer value from the random
         * number sequence.
         *
         * @return {number} 64-bit random integer.
         * @see java.lang.Integer#MAX_VALUE
         * @see java.lang.Integer#MIN_VALUE
         * @see #next
         * @see #nextInt()
         * @see #nextInt(int)
         */
        nextLong(): number;
        setSeed$long(seed: number): void;
        /**
         * Returns a pseudo-random uniformly distributed {@code int} value of the
         * number of bits specified by the argument {@code bits} as described by
         * Donald E. Knuth in <i>The Art of Computer Programming, Volume 2:
         * Seminumerical Algorithms</i>, section 3.2.1.
         *
         * @param {number} bits number of bits of the returned value.
         * @return {number} a pseudo-random generated int number.
         * @see #nextBytes
         * @see #nextDouble
         * @see #nextFloat
         * @see #nextInt()
         * @see #nextInt(int)
         * @see #nextGaussian
         * @see #nextLong
         */
        next(bits: number): number;
        nextInternal(bits: number): number;
        setSeed$int$int(seedhi: number, seedlo: number): void;
        setSeed(seedhi?: any, seedlo?: any): any;
    }
}
declare namespace java.util {
    /**
     * Indicates that a data structure supports constant-time random access to its
     * contained objects.
     * @class
     */
    interface RandomAccess {
    }
}
declare namespace java.util.regex {
    class Matcher implements java.util.regex.MatchResult {
        _pattern: java.util.regex.Pattern;
        text: string;
        starts: number[];
        ends: number[];
        groups: string[];
        constructor(_pattern: java.util.regex.Pattern, text: string);
        hasGroups(): void;
        searchWith(regExp: RegExp): boolean;
        end$(): number;
        end$int(i: number): number;
        end$java_lang_String(string: string): number;
        end(string?: any): any;
        find$(): boolean;
        find$int(start: number): boolean;
        find(start?: any): any;
        group$(): string;
        group$int(i: number): string;
        group$java_lang_String(string: string): string;
        group(string?: any): any;
        /**
         *
         * @return {number}
         */
        groupCount(): number;
        hitEnd(): boolean;
        lookingAt(): boolean;
        matches(): boolean;
        pattern(): java.util.regex.Pattern;
        regionEnd(): number;
        regionStart(): number;
        replaceAll(replacement: string): string;
        replaceFirst(replacement: string): string;
        reset$(): Matcher;
        reset$java_lang_CharSequence(input: any): Matcher;
        reset(input?: any): any;
        start$(): number;
        start$int(i: number): number;
        start$java_lang_String(string: string): number;
        start(string?: any): any;
        toMatchResult(): java.util.regex.MatchResult;
        usePattern(newPattern: java.util.regex.Pattern): Matcher;
    }
    namespace Matcher {
        class IndexGetter {
            regexString: string;
            parenthesisStart: number[];
            parenthesisEnd: number[];
            starts: number[];
            ends: number[];
            startLastIndex: number;
            constructor(regexString: string, parenthesisStart: number[], parenthesisEnd: number[], starts: number[], ends: number[], startLastIndex: number);
            /**
             *
             * @param {Array} args
             * @return {string}
             */
            apply(args: string[]): string;
        }
        class NonCapturesToCaptures {
            start: number[];
            ends: number[];
            constructor(start: number[], ends: number[]);
            /**
             *
             * @param {Array} args
             * @return {string}
             */
            apply(args: string[]): string;
        }
        class FirstReplacer {
            replacement: string;
            first: boolean;
            constructor(replacement: string);
            /**
             *
             * @param {Array} args
             * @return {string}
             */
            apply(args: string[]): string;
        }
    }
}
declare namespace java.util.regex {
    interface MatchResult {
        start(i?: any): number;
        end(i?: any): number;
        group(i?: any): string;
        groupCount(): number;
    }
}
declare namespace java.util.regex {
    class Pattern implements java.io.Serializable {
        static CASE_INSENSITIVE: number;
        static MULTILINE: number;
        static UNICODE_CASE: number;
        static UNICODE_CHARACTER_CLASS: number;
        regexp: RegExp;
        _flags: number;
        namedGroupsNames: java.util.Map<string, number>;
        constructor(regexp: RegExp, _flags: number, namedGroupsNames: java.util.Map<string, number>);
        static compile$java_lang_String(regexp: string): Pattern;
        static compile$java_lang_String$int(regexpString: string, flags: number): Pattern;
        static compile(regexpString?: any, flags?: any): any;
        flags(): number;
        matcher(sequence: any): java.util.regex.Matcher;
        static matches(regex: string, input: any): boolean;
        pattern(): string;
        static quote(s: string): string;
        split(input: any, limit?: number): string[];
        splitAsStream(input: any): java.util.stream.Stream<string>;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
    namespace Pattern {
        class GroupNameRemover {
            namedGroupsNames: java.util.Map<string, number>;
            count: number;
            inBrackets: boolean;
            constructor(namedGroupsNames: java.util.Map<string, number>);
            /**
             *
             * @param {Array} args
             * @return {string}
             */
            apply(args: string[]): string;
        }
    }
}
declare namespace java.util {
    /**
     * Represents a set of unique objects. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/Set.html">[Sun docs]</a>
     *
     * @param <E> element type.
     * @class
     */
    interface Set<E> extends java.util.Collection<E> {
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        add(o: E): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        addAll(c: java.util.Collection<any>): boolean;
        /**
         *
         */
        clear(): any;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        contains(o: any): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        containsAll(c: java.util.Collection<any>): boolean;
        /**
         *
         * @return {boolean}
         */
        isEmpty(): boolean;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<E>;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        remove(o: any): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        removeAll(c: java.util.Collection<any>): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        retainAll(c: java.util.Collection<any>): boolean;
        /**
         *
         * @return {number}
         */
        size(): number;
        /**
         *
         * @param {Array} a
         * @return {Array}
         */
        toArray<T>(a?: any): any;
    }
}
declare namespace java.util {
    /**
     * A map with ordering. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/SortedMap.html">[Sun
     * docs]</a>
     *
     * @param <K> key type.
     * @param <V> value type.
     * @class
     */
    interface SortedMap<K, V> extends java.util.Map<K, V> {
        comparator(): java.util.Comparator<any>;
        firstKey(): K;
        headMap(toKey: K): SortedMap<K, V>;
        lastKey(): K;
        subMap(fromKey: K, toKey: K): SortedMap<K, V>;
        tailMap(fromKey: K): SortedMap<K, V>;
    }
}
declare namespace java.util {
    /**
     * A set known to be in ascending order. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/SortedSet.html">[Sun
     * docs]</a>
     *
     * @param <E> element type.
     * @class
     */
    interface SortedSet<E> extends java.util.Set<E> {
        comparator(): java.util.Comparator<any>;
        first(): E;
        headSet(toElement: E): SortedSet<E>;
        last(): E;
        subSet(fromElement: E, toElement: E): SortedSet<E>;
        tailSet(fromElement: E): SortedSet<E>;
    }
}
declare namespace java.util {
    interface Spliterator<T> {
    }
}
declare namespace java.util.stream {
    interface Collector<T, A, R> {
        supplier(): () => A;
        accumulator(): (p1: A, p2: T) => void;
    }
    namespace Collector {
        enum Characteristics {
        }
    }
}
declare namespace java.util.stream {
    class Collectors {
        static throwingMerger<T>(): (p1: T, p2: T) => T;
        static toList<T>(): java.util.stream.Collector<T, any, java.util.List<T>>;
        static toSet<T>(): java.util.stream.Collector<T, any, java.util.Set<T>>;
        static toMap$java_util_function_Function$java_util_function_Function<T, K, U>(keyMapper: (p1: any) => any, valueMapper: (p1: any) => any): java.util.stream.Collector<T, any, java.util.Map<K, U>>;
        static toMap$java_util_function_Function$java_util_function_Function$java_util_function_BinaryOperator<T, K, U>(keyMapper: (p1: any) => any, valueMapper: (p1: any) => any, mergeFunction: (p1: U, p2: U) => U): java.util.stream.Collector<T, any, java.util.Map<K, U>>;
        static toMap$java_util_function_Function$java_util_function_Function$java_util_function_BinaryOperator$java_util_function_Supplier<T, K, U, M extends java.util.Map<K, U>>(keyMapper: (p1: any) => any, valueMapper: (p1: any) => any, mergeFunction: (p1: U, p2: U) => U, mapSupplier: () => M): java.util.stream.Collector<T, any, M>;
        static toMap<T, K, U, M extends java.util.Map<K, U> = any>(keyMapper?: any, valueMapper?: any, mergeFunction?: any, mapSupplier?: any): any;
        static mapMerger<K, V, M extends java.util.Map<K, V> = any>(mergeFunction: (p1: V, p2: V) => V): (p1: M, p2: M) => M;
    }
    namespace Collectors {
        class CollectorImpl<T, A, R> implements java.util.stream.Collector<T, A, R> {
            __supplier: () => A;
            __accumulator: (p1: A, p2: T) => void;
            __combiner: (p1: A, p2: A) => A;
            constructor(supplier: () => A, accumulator: (p1: A, p2: T) => void, combiner: (p1: A, p2: A) => A);
            /**
             *
             * @return {*}
             */
            accumulator(): (p1: A, p2: T) => void;
            /**
             *
             * @return {*}
             */
            supplier(): () => A;
            combiner(): (p1: A, p2: A) => A;
            finisher(): (p1: A) => R;
            characteristics(): java.util.Set<Collector.Characteristics>;
        }
    }
}
declare namespace java.util.stream {
    interface DoubleStream {
    }
}
declare namespace java.util.stream {
    interface IntStream {
    }
    namespace IntStream {
        function range(startInclusive: number, endExclusive: number): java.util.stream.Stream<number>;
    }
}
declare namespace java.util.stream {
    interface LongStream {
    }
}
declare namespace java.util.stream {
    interface Stream<T> {
        filter(predicate: (p1: T) => boolean): Stream<T>;
        map<R>(mapper: (p1: T) => R): Stream<R>;
        flatMap<R>(mapper: (p1: T) => any): Stream<R>;
        distinct(): Stream<T>;
        sorted(comparator?: java.util.Comparator<T>): Stream<T>;
        peek(action: (p1: T) => void): Stream<T>;
        limit(maxSize: number): Stream<T>;
        skip(n: number): Stream<T>;
        forEach(action: (p1: T) => void): void;
        forEachOrdered(action: (p1: T) => void): void;
        toArray<A = any>(generator?: any): T[];
        reduce(identity?: T, accumulator?: any): T;
        collect<R, A>(collector: java.util.stream.Collector<any, A, R>): R;
        min(comparator: java.util.Comparator<any>): java.util.Optional<T>;
        max(comparator: java.util.Comparator<any>): java.util.Optional<T>;
        count(): number;
        anyMatch(predicate: (p1: any) => boolean): boolean;
        allMatch(predicate: (p1: any) => boolean): boolean;
        noneMatch(predicate: (p1: any) => boolean): boolean;
        findFirst(): java.util.Optional<T>;
        findAny(): java.util.Optional<T>;
        iterator(): java.util.Iterator<T>;
        isParallel(): boolean;
        sequential(): Stream<T>;
        parallel(): Stream<T>;
        unordered(): Stream<T>;
        onClose(closeHandler: () => void): Stream<T>;
        close(): any;
        mapToObj<U>(mapper: (p0: number) => any): Stream<U>;
    }
    namespace Stream {
        function of<T>(...values: T[]): Stream<T>;
    }
}
declare namespace java.util {
    /**
     * See <a href="https://docs.oracle.com/javase/8/docs/api/java/util/StringJoiner.html">
     * the official Java API doc</a> for details.
     * @param {*} delimiter
     * @param {*} prefix
     * @param {*} suffix
     * @class
     */
    class StringJoiner {
        delimiter: string;
        prefix: string;
        suffix: string;
        builder: java.lang.StringBuilder;
        emptyValue: string;
        constructor(delimiter?: any, prefix?: any, suffix?: any);
        add(newElement: any): StringJoiner;
        length(): number;
        merge(other: StringJoiner): StringJoiner;
        setEmptyValue(emptyValue: any): StringJoiner;
        /**
         *
         * @return {string}
         */
        toString(): string;
        initBuilderOrAddDelimiter(): void;
    }
}
declare namespace java.util {
    abstract class TimerTask {
        static VIRGIN: number;
        static SCHEDULED: number;
        static EXECUTED: number;
        static CANCELLED: number;
        state: number;
        nextExecutionTime: number;
        period: number;
        handle: number;
        constructor();
        abstract run(): any;
        cancel(): boolean;
        scheduledExecutionTime(): number;
    }
}
declare namespace javaemul.internal.annotations {
}
declare namespace javaemul.internal.annotations {
}
declare namespace javaemul.internal.annotations {
}
declare namespace javaemul.internal.annotations {
}
declare namespace javaemul.internal.annotations {
}
declare namespace javaemul.internal.annotations {
}
declare namespace javaemul.internal {
    /**
     * Provides utilities to perform operations on Arrays.
     * @class
     */
    class ArrayHelper {
        static ARRAY_PROCESS_BATCH_SIZE: number;
        static clone<T>(array: T[], fromIndex: number, toIndex: number): T[];
        /**
         * Unlike clone, this method returns a copy of the array that is not type
         * marked. This is only safe for temp arrays as returned array will not do
         * any type checks.
         * @param {*} array
         * @param {number} fromIndex
         * @param {number} toIndex
         * @return {Array}
         */
        static unsafeClone(array: any, fromIndex: number, toIndex: number): any[];
        static createFrom<T>(array: T[], length: number): T[];
        static createNativeArray(length: number): any;
        static getLength(array: any): number;
        static setLength(array: any, length: number): void;
        static removeFrom(array: any, index: number, deleteCount: number): void;
        static insertTo$java_lang_Object$int$java_lang_Object(array: any, index: number, value: any): void;
        static insertTo$java_lang_Object$int$java_lang_Object_A(array: any, index: number, values: any[]): void;
        static insertTo(array?: any, index?: any, values?: any): any;
        /**
         * This version of insertTo is specified only for arrays.
         * Same implementation (and arguments) as "public static void insertTo(Object array, int index, Object[] values)"
         * @param {*} array
         * @param {number} index
         * @param {Array} values
         */
        static insertValuesToArray(array: any, index: number, values: any[]): void;
        static copy$java_lang_Object$int$java_lang_Object$int$int(array: any, srcOfs: number, dest: any, destOfs: number, len: number): void;
        static copy$java_lang_Object$int$java_lang_Object$int$int$boolean(src: any, srcOfs: number, dest: any, destOfs: number, len: number, overwrite: boolean): void;
        static copy(src?: any, srcOfs?: any, dest?: any, destOfs?: any, len?: any, overwrite?: any): any;
        static applySplice(arrayObject: any, index: number, deleteCount: number, arrayToAdd: any): void;
    }
}
declare namespace javaemul.internal {
    /**
     * A utility to provide array stamping. Provided as a separate class to simplify
     * super-source.
     * @class
     */
    class ArrayStamper {
        static stampJavaTypeInfo<T>(array: any, referenceType: T[]): T[];
    }
}
declare namespace javaemul.internal {
    /**
     * Wraps native <code>boolean</code> as an object.
     * @param {boolean} value
     * @class
     */
    class BooleanHelper implements java.lang.Comparable<BooleanHelper>, java.io.Serializable {
        static FALSE: boolean;
        static TRUE: boolean;
        static TYPE: any;
        static TYPE_$LI$(): any;
        static compare(x: boolean, y: boolean): number;
        static hashCode(value: boolean): number;
        static logicalAnd(a: boolean, b: boolean): boolean;
        static logicalOr(a: boolean, b: boolean): boolean;
        static logicalXor(a: boolean, b: boolean): boolean;
        static parseBoolean(s: string): boolean;
        static toString(x: boolean): string;
        static valueOf$boolean(b: boolean): boolean;
        static valueOf$java_lang_String(s: string): boolean;
        static valueOf(s?: any): any;
        booleanValue(): boolean;
        static unsafeCast(value: any): boolean;
        compareTo$javaemul_internal_BooleanHelper(b: BooleanHelper): number;
        /**
         *
         * @param {javaemul.internal.BooleanHelper} b
         * @return {number}
         */
        compareTo(b?: any): any;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
        constructor();
    }
}
declare namespace javaemul.internal {
    /**
     * Wraps a native <code>char</code> as an object.
     *
     * TODO(jat): many of the classification methods implemented here are not
     * correct in that they only handle ASCII characters, and many other methods are
     * not currently implemented. I think the proper approach is to introduce * a
     * deferred binding parameter which substitutes an implementation using a
     * fully-correct Unicode character database, at the expense of additional data
     * being downloaded. That way developers that need the functionality can get it
     * without those who don't need it paying for it.
     *
     * <pre>
     * The following methods are still not implemented -- most would require Unicode
     * character db to be useful:
     * - digit / is* / to*(int codePoint)
     * - isDefined(char)
     * - isIdentifierIgnorable(char)
     * - isJavaIdentifierPart(char)
     * - isJavaIdentifierStart(char)
     * - isJavaLetter(char) -- deprecated, so probably not
     * - isJavaLetterOrDigit(char) -- deprecated, so probably not
     * - isISOControl(char)
     * - isMirrored(char)
     * - isSpaceChar(char)
     * - isTitleCase(char)
     * - isUnicodeIdentifierPart(char)
     * - isUnicodeIdentifierStart(char)
     * - getDirectionality(*)
     * - getNumericValue(*)
     * - getType(*)
     * - reverseBytes(char) -- any use for this at all in the browser?
     * - toTitleCase(*)
     * - all the category constants for classification
     *
     * The following do not properly handle characters outside of ASCII:
     * - digit(char c, int radix)
     * - isDigit(char c)
     * - isLetter(char c)
     * - isLetterOrDigit(char c)
     * - isLowerCase(char c)
     * - isUpperCase(char c)
     * </pre>
     * @param {string} value
     * @class
     */
    class CharacterHelper implements java.lang.Comparable<CharacterHelper>, java.io.Serializable {
        static TYPE: any;
        static TYPE_$LI$(): any;
        static MIN_RADIX: number;
        static MAX_RADIX: number;
        static MIN_VALUE: string;
        static MAX_VALUE: string;
        static MIN_SURROGATE: string;
        static MAX_SURROGATE: string;
        static MIN_LOW_SURROGATE: string;
        static MAX_LOW_SURROGATE: string;
        static MIN_HIGH_SURROGATE: string;
        static MAX_HIGH_SURROGATE: string;
        static MIN_SUPPLEMENTARY_CODE_POINT: number;
        static MIN_CODE_POINT: number;
        static MAX_CODE_POINT: number;
        static SIZE: number;
        static charCount(codePoint: number): number;
        static codePointAt$char_A$int(a: string[], index: number): number;
        static codePointAt$char_A$int$int(a: string[], index: number, limit: number): number;
        static codePointAt(a?: any, index?: any, limit?: any): any;
        static codePointAt$java_lang_CharSequence$int(seq: any, index: number): number;
        static codePointBefore$char_A$int(a: string[], index: number): number;
        static codePointBefore$char_A$int$int(a: string[], index: number, start: number): number;
        static codePointBefore(a?: any, index?: any, start?: any): any;
        static codePointBefore$java_lang_CharSequence$int(cs: any, index: number): number;
        static codePointCount$char_A$int$int(a: string[], offset: number, count: number): number;
        static codePointCount(a?: any, offset?: any, count?: any): any;
        static codePointCount$java_lang_CharSequence$int$int(seq: any, beginIndex: number, endIndex: number): number;
        static compare(x: string, y: string): number;
        static digit(c: string, radix: number): number;
        static getNumericValue(ch: string): number;
        static forDigit$int$int(digit: number, radix: number): string;
        static forDigit(digit?: any, radix?: any): any;
        /**
         * @skip
         *
         * public for shared implementation with Arrays.hashCode
         * @param {string} c
         * @return {number}
         */
        static hashCode(c: string): number;
        static isDigit(c: string): boolean;
        static digitRegex(): RegExp;
        static isHighSurrogate(ch: string): boolean;
        static isLetter(c: string): boolean;
        static leterRegex(): RegExp;
        static isLetterOrDigit(c: string): boolean;
        static leterOrDigitRegex(): RegExp;
        static isLowerCase(c: string): boolean;
        static isLowSurrogate(ch: string): boolean;
        /**
         * Deprecated - see isWhitespace(char).
         * @param {string} c
         * @return {boolean}
         */
        static isSpace(c: string): boolean;
        static isWhitespace$char(ch: string): boolean;
        static isWhitespace(ch?: any): any;
        static isWhitespace$int(codePoint: number): boolean;
        static whitespaceRegex(): RegExp;
        static isSupplementaryCodePoint(codePoint: number): boolean;
        static isSurrogatePair(highSurrogate: string, lowSurrogate: string): boolean;
        static isUpperCase(c: string): boolean;
        static isValidCodePoint(codePoint: number): boolean;
        static offsetByCodePoints$char_A$int$int$int$int(a: string[], start: number, count: number, index: number, codePointOffset: number): number;
        static offsetByCodePoints(a?: any, start?: any, count?: any, index?: any, codePointOffset?: any): any;
        static offsetByCodePoints$java_lang_CharSequence$int$int(seq: any, index: number, codePointOffset: number): number;
        static toChars$int(codePoint: number): string[];
        static toChars$int$char_A$int(codePoint: number, dst: string[], dstIndex: number): number;
        static toChars(codePoint?: any, dst?: any, dstIndex?: any): any;
        static toCodePoint(highSurrogate: string, lowSurrogate: string): number;
        static toLowerCase$char(c: string): string;
        static toLowerCase(c?: any): any;
        static toLowerCase$int(c: number): number;
        static toString(x: string): string;
        static toUpperCase$char(c: string): string;
        static toUpperCase(c?: any): any;
        static toUpperCase$int(c: number): string;
        static valueOf(c: string): string;
        static codePointAt$java_lang_CharSequence$int$int(cs: any, index: number, limit: number): number;
        static codePointBefore$java_lang_CharSequence$int$int(cs: any, index: number, start: number): number;
        static forDigit$int(digit: number): string;
        /**
         * Computes the high surrogate character of the UTF16 representation of a
         * non-BMP code point. See {@link getLowSurrogate}.
         *
         * @param {number} codePoint
         * requested codePoint, required to be >=
         * MIN_SUPPLEMENTARY_CODE_POINT
         * @return {string} high surrogate character
         */
        static getHighSurrogate(codePoint: number): string;
        /**
         * Computes the low surrogate character of the UTF16 representation of a
         * non-BMP code point. See {@link getHighSurrogate}.
         *
         * @param {number} codePoint
         * requested codePoint, required to be >=
         * MIN_SUPPLEMENTARY_CODE_POINT
         * @return {string} low surrogate character
         */
        static getLowSurrogate(codePoint: number): string;
        value: string;
        constructor(value: string);
        charValue(): string;
        compareTo$javaemul_internal_CharacterHelper(c: CharacterHelper): number;
        /**
         *
         * @param {javaemul.internal.CharacterHelper} c
         * @return {number}
         */
        compareTo(c?: any): any;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
    namespace CharacterHelper {
        /**
         * Use nested class to avoid clinit on outer.
         * @class
         */
        class BoxedValues {
            static boxedValues: string[];
            static boxedValues_$LI$(): string[];
            constructor();
        }
    }
}
declare namespace javaemul.internal {
    /**
     * Private implementation class for GWT. This API should not be
     * considered public or stable.
     * @class
     */
    class Coercions {
        /**
         * Coerce js int to 32 bits.
         * Trick related to JS and lack of integer rollover.
         * {@see com.google.gwt.lang.Cast#narrow_int}
         * @param {number} value
         * @return {number}
         */
        static ensureInt(value: number): number;
        constructor();
    }
}
declare namespace javaemul.internal {
    /**
     * Simple Helper class to return Date.now.
     * @class
     */
    class DateUtil {
        /**
         * Returns the numeric value corresponding to the current time - the number
         * of milliseconds elapsed since 1 January 1970 00:00:00 UTC.
         * @return {number}
         */
        static now(): number;
    }
}
declare namespace javaemul.internal {
    /**
     * Contains logics for calculating hash codes in JavaScript.
     * @class
     */
    class HashCodes {
        static sNextHashId: number;
        static HASH_CODE_PROPERTY: string;
        static hashCodeForString(s: string): number;
        static getIdentityHashCode(o: any): number;
        static getObjectIdentityHashCode(o: any): number;
        /**
         * Called from JSNI. Do not change this implementation without updating:
         * <ul>
         * <li>{@link com.google.gwt.user.client.rpc.impl.SerializerBase}</li>
         * </ul>
         * @return {number}
         * @private
         */
        static getNextHashId(): number;
    }
}
declare namespace javaemul.internal {
    class JreHelper {
        static LOG10E: number;
        static LOG10E_$LI$(): number;
    }
}
declare namespace javaemul.internal {
    /**
     * Provides an interface for simple JavaScript idioms that can not be expressed in Java.
     * @class
     */
    class JsUtils {
        static getInfinity(): number;
        static isUndefined(value: any): boolean;
        static unsafeCastToString(string: any): string;
        static setPropertySafe(map: any, key: string, value: any): void;
        static getIntProperty(map: any, key: string): number;
        static setIntProperty(map: any, key: string, value: number): void;
        static typeOf(o: any): string;
    }
}
declare namespace javaemul.internal {
    /**
     * A helper class for long comparison.
     * @class
     */
    class LongCompareHolder {
        static getLongComparator(): any;
    }
}
declare namespace javaemul.internal {
    /**
     * Math utility methods and constants.
     * @class
     */
    class MathHelper {
        static EPSILON: number;
        static EPSILON_$LI$(): number;
        static MAX_VALUE: number;
        static MAX_VALUE_$LI$(): number;
        static MIN_VALUE: number;
        static MIN_VALUE_$LI$(): number;
        static nextDown(x: number): number;
        static ulp(x: number): number;
        static nextUp(x: number): number;
        static E: number;
        static PI: number;
        static PI_OVER_180: number;
        static PI_OVER_180_$LI$(): number;
        static PI_UNDER_180: number;
        static PI_UNDER_180_$LI$(): number;
        static abs$double(x: number): number;
        static abs$float(x: number): number;
        static abs$int(x: number): number;
        static abs(x?: any): any;
        static abs$long(x: number): number;
        static acos(x: number): number;
        static asin(x: number): number;
        static atan(x: number): number;
        static atan2(y: number, x: number): number;
        static cbrt(x: number): number;
        static ceil(x: number): number;
        static copySign$double$double(magnitude: number, sign: number): number;
        static copySign$float$float(magnitude: number, sign: number): number;
        static copySign(magnitude?: any, sign?: any): any;
        static cos(x: number): number;
        static cosh(x: number): number;
        static exp(x: number): number;
        static expm1(d: number): number;
        static floor(x: number): number;
        static hypot(x: number, y: number): number;
        static log(x: number): number;
        static log10(x: number): number;
        static log1p(x: number): number;
        static max$double$double(x: number, y: number): number;
        static max$float$float(x: number, y: number): number;
        static max$int$int(x: number, y: number): number;
        static max(x?: any, y?: any): any;
        static max$long$long(x: number, y: number): number;
        static min$double$double(x: number, y: number): number;
        static min$float$float(x: number, y: number): number;
        static min$int$int(x: number, y: number): number;
        static min(x?: any, y?: any): any;
        static min$long$long(x: number, y: number): number;
        static pow(x: number, exp: number): number;
        static random(): number;
        static rint(d: number): number;
        static round$double(x: number): number;
        static round$float(x: number): number;
        static round(x?: any): any;
        static unsafeCastToInt(d: number): number;
        static scalb$double$int(d: number, scaleFactor: number): number;
        static scalb$float$int(f: number, scaleFactor: number): number;
        static scalb(f?: any, scaleFactor?: any): any;
        static signum$double(d: number): number;
        static signum$float(f: number): number;
        static signum(f?: any): any;
        static sin(x: number): number;
        static sinh(x: number): number;
        static sqrt(x: number): number;
        static tan(x: number): number;
        static tanh(x: number): number;
        static toDegrees(x: number): number;
        static toRadians(x: number): number;
        static IEEEremainder(f1: number, f2: number): number;
    }
}
declare namespace javaemul.internal {
    /**
     * Abstract base class for numeric wrapper classes.
     * @class
     */
    abstract class NumberHelper implements java.io.Serializable {
        /**
         * Stores a regular expression object to verify the format of float values.
         */
        static floatRegex: RegExp;
        /**
         * @skip
         *
         * This function will determine the radix that the string is expressed
         * in based on the parsing rules defined in the Javadocs for
         * Integer.decode() and invoke __parseAndValidateInt.
         * @param {string} s
         * @param {number} lowerBound
         * @param {number} upperBound
         * @return {number}
         */
        static __decodeAndValidateInt(s: string, lowerBound: number, upperBound: number): number;
        static __decodeNumberString(s: string): NumberHelper.__Decode;
        /**
         * @skip
         *
         * This function contains common logic for parsing a String as a
         * floating- point number and validating the range.
         * @param {string} s
         * @return {number}
         */
        static __parseAndValidateDouble(s: string): number;
        /**
         * @skip
         *
         * This function contains common logic for parsing a String in a given
         * radix and validating the result.
         * @param {string} s
         * @param {number} radix
         * @param {number} lowerBound
         * @param {number} upperBound
         * @return {number}
         */
        static __parseAndValidateInt(s: string, radix: number, lowerBound: number, upperBound: number): number;
        /**
         * @skip
         *
         * This function contains common logic for parsing a String in a given
         * radix and validating the result.
         * @param {string} s
         * @param {number} radix
         * @return {number}
         */
        static __parseAndValidateLong(s: string, radix: number): number;
        /**
         * @skip
         *
         * @param {string} str
         * @return {boolean} {@code true} if the string matches the float format,
         * {@code false} otherwise
         * @private
         */
        static __isValidDouble(str: string): boolean;
        static createFloatRegex(): RegExp;
        byteValue(): number;
        abstract doubleValue(): number;
        abstract floatValue(): number;
        abstract intValue(): number;
        abstract longValue(): number;
        shortValue(): number;
        constructor();
    }
    namespace NumberHelper {
        class __Decode {
            payload: string;
            radix: number;
            constructor(radix: number, payload: string);
        }
        /**
         * Use nested class to avoid clinit on outer.
         * @class
         */
        class __ParseLong {
            static __static_initialized: boolean;
            static __static_initialize(): void;
            /**
             * The number of digits (excluding minus sign and leading zeros) to
             * process at a time. The largest value expressible in maxDigits digits
             * as well as the factor radix^maxDigits must be strictly less than
             * 2^31.
             */
            static maxDigitsForRadix: number[];
            static maxDigitsForRadix_$LI$(): number[];
            /**
             * A table of values radix*maxDigitsForRadix[radix].
             */
            static maxDigitsRadixPower: number[];
            static maxDigitsRadixPower_$LI$(): number[];
            /**
             * The largest number of digits (excluding minus sign and leading zeros)
             * that can fit into a long for a given radix between 2 and 36,
             * inclusive.
             */
            static maxLengthForRadix: number[];
            static maxLengthForRadix_$LI$(): number[];
            /**
             * A table of floor(MAX_VALUE / maxDigitsRadixPower).
             */
            static maxValueForRadix: number[];
            static maxValueForRadix_$LI$(): number[];
            static __static_initializer_0(): void;
            constructor();
        }
    }
}
declare namespace javaemul.internal {
    class ObjectHelper {
        static clone(obj: any): any;
    }
}
declare namespace javaemul.internal.stream {
    class ChooseSmallest<T> {
        comparator: java.util.Comparator<T>;
        constructor(comparator: java.util.Comparator<T>);
        apply(t1: T, t2: T): T;
    }
}
declare namespace javaemul.internal.stream {
    class ConsumingFunction<T> {
        consumer: (p1: T) => void;
        constructor(consumer: (p1: T) => void);
        /**
         *
         * @param {*} t
         * @return {*}
         */
        apply(t: T): T;
    }
}
declare namespace javaemul.internal.stream {
    class CountingPredicate<T> {
        countDown: number;
        constructor(n: number);
        /**
         *
         * @param {*} t
         * @return {boolean}
         */
        test(t: T): boolean;
    }
}
declare namespace javaemul.internal.stream {
    class QuiteRunnable {
        loudRunnable: () => void;
        constructor(loudRunnable: () => void);
        /**
         *
         */
        run(): void;
    }
}
declare namespace javaemul.internal.stream {
    class RunnableChain {
        run: () => void;
        next: RunnableChain;
        constructor(run: () => void);
        chain(next: RunnableChain): void;
        runChain(): void;
    }
}
declare namespace javaemul.internal.stream {
    class StreamHelper<T> implements java.util.stream.Stream<T> {
        onCloseChain: javaemul.internal.stream.RunnableChain;
        head: javaemul.internal.stream.StreamRow;
        end: javaemul.internal.stream.StreamRow;
        data: java.util.Collection<T>;
        chain(streamRow: javaemul.internal.stream.StreamRow): java.util.stream.Stream<any>;
        play(): void;
        foldRight(identity: java.util.Optional<T>, accumulator: any): java.util.Optional<any>;
        constructor(data: java.util.Collection<T>);
        filter(predicate: (p1: any) => boolean): java.util.stream.Stream<T>;
        map<R>(mapper: (p1: any) => any): java.util.stream.Stream<R>;
        mapToObj<U>(mapper: (p0: number) => any): java.util.stream.Stream<U>;
        flatMap<R>(mapper: (p1: any) => any): java.util.stream.Stream<R>;
        distinct(): java.util.stream.Stream<T>;
        sorted$(): java.util.stream.Stream<T>;
        sorted$java_util_Comparator(comparator: java.util.Comparator<any>): java.util.stream.Stream<T>;
        sorted(comparator?: any): any;
        peek(action: (p1: any) => void): java.util.stream.Stream<T>;
        limit(maxSize: number): java.util.stream.Stream<T>;
        skip(n: number): java.util.stream.Stream<T>;
        forEach(action: (p1: any) => void): void;
        forEachOrdered(action: (p1: any) => void): void;
        toArray$(): any[];
        toArray$java_util_function_IntFunction<A>(generator: (p0: number) => A[]): A[];
        toArray<A>(generator?: any): any;
        reduce$java_lang_Object$java_util_function_BinaryOperator(identity: T, accumulator: (p1: T, p2: T) => T): T;
        reduce$java_util_function_BinaryOperator(accumulator: (p1: T, p2: T) => T): java.util.Optional<T>;
        collect$java_util_stream_Collector<R, A>(collector: java.util.stream.Collector<any, A, R>): R;
        min(comparator: java.util.Comparator<any>): java.util.Optional<T>;
        max(comparator: java.util.Comparator<any>): java.util.Optional<T>;
        count(): number;
        anyMatch(predicate: (p1: any) => boolean): boolean;
        allMatch(predicate: (p1: any) => boolean): boolean;
        noneMatch(predicate: (p1: any) => boolean): boolean;
        findFirst(): java.util.Optional<T>;
        findAny(): java.util.Optional<T>;
        iterator(): java.util.Iterator<T>;
        isParallel(): boolean;
        sequential(): java.util.stream.Stream<T>;
        parallel(): java.util.stream.Stream<T>;
        unordered(): java.util.stream.Stream<T>;
        onClose(closeHandler: () => void): java.util.stream.Stream<T>;
        close(): void;
        mapToInt(mapper: () => any): java.util.stream.IntStream;
        mapToLong(mapper: () => any): java.util.stream.LongStream;
        mapToDouble(mapper: () => any): java.util.stream.DoubleStream;
        flatMapToInt(mapper: (p1: any) => any): java.util.stream.IntStream;
        flatMapToLong(mapper: (p1: any) => any): java.util.stream.LongStream;
        flatMapToDouble(mapper: (p1: any) => any): java.util.stream.DoubleStream;
        spliterator(): java.util.Spliterator<T>;
        reduce$java_lang_Object$java_util_function_BiFunction$java_util_function_BinaryOperator<U>(identity: U, accumulator: (p1: U, p2: any) => U, combiner: (p1: U, p2: U) => U): U;
        reduce<U>(identity?: any, accumulator?: any, combiner?: any): any;
        collect$java_util_function_Supplier$java_util_function_BiConsumer$java_util_function_BiConsumer<R>(supplier: () => R, accumulator: (p1: R, p2: any) => void, combiner: (p1: R, p2: R) => void): R;
        collect<R>(supplier?: any, accumulator?: any, combiner?: any): any;
    }
}
declare namespace javaemul.internal.stream {
    interface StreamRow {
        chain(next: StreamRow): any;
        item(a: any): boolean;
        end(): any;
    }
}
declare namespace javaemul.internal.stream {
    abstract class TerminalStreamRow implements javaemul.internal.stream.StreamRow {
        chain(next: javaemul.internal.stream.StreamRow): void;
        end(): void;
        abstract item(a?: any): any;
        constructor();
    }
}
declare namespace javaemul.internal.stream {
    abstract class TransientStreamRow implements javaemul.internal.stream.StreamRow {
        next: javaemul.internal.stream.StreamRow;
        chain(next: javaemul.internal.stream.StreamRow): void;
        abstract item(a?: any): any;
        abstract end(): any;
        constructor();
    }
}
declare namespace javaemul.internal.stream {
    class VoidRunnable {
        static dryRun: VoidRunnable;
        static dryRun_$LI$(): VoidRunnable;
        run(): void;
        constructor();
    }
}
declare namespace javaemul.internal {
    /**
     * Hashcode caching for strings.
     * @class
     */
    class StringHashCache {
        /**
         * The "old" cache; it will be dumped when front is full.
         */
        static back: any;
        static back_$LI$(): any;
        /**
         * Tracks the number of entries in front.
         */
        static count: number;
        /**
         * The "new" cache; it will become back when it becomes full.
         */
        static front: any;
        static front_$LI$(): any;
        /**
         * Pulled this number out of thin air.
         */
        static MAX_CACHE: number;
        static getHashCode(str: string): number;
        static compute(str: string): number;
        static increment(): void;
        static getProperty(map: any, key: string): any;
        static createNativeObject(): any;
        static unsafeCastToInt(o: any): number;
    }
}
declare namespace java.io {
    /**
     * Constructs a new {@code ByteArrayInputStream} on the byte array
     * {@code buf} with the initial position set to {@code offset} and the
     * number of bytes available set to {@code offset} + {@code length}.
     *
     * @param {Array} buf
     * the byte array to stream over.
     * @param {number} offset
     * the initial position in {@code buf} to start streaming from.
     * @param {number} length
     * the number of bytes available for streaming.
     * @class
     * @extends java.io.InputStream
     */
    class ByteArrayInputStream extends java.io.InputStream {
        /**
         * The {@code byte} array containing the bytes to stream over.
         */
        buf: number[];
        /**
         * The current position within the byte array.
         */
        pos: number;
        /**
         * The current mark position. Initially set to 0 or the <code>offset</code>
         * parameter within the constructor.
         */
        _mark: number;
        /**
         * The total number of bytes initially available in the byte array
         * {@code buf}.
         */
        count: number;
        constructor(buf?: any, offset?: any, length?: any);
        /**
         * Returns the number of remaining bytes.
         *
         * @return {number} {@code count - pos}
         */
        available(): number;
        /**
         * Closes this stream and frees resources associated with this stream.
         *
         * @throws IOException
         * if an I/O error occurs while closing this stream.
         */
        close(): void;
        /**
         * Sets a mark position in this ByteArrayInputStream. The parameter
         * {@code readlimit} is ignored. Sending {@code reset()} will reposition the
         * stream back to the marked position.
         *
         * @param {number} readlimit
         * ignored.
         * @see #markSupported()
         * @see #reset()
         */
        mark(readlimit: number): void;
        /**
         * Indicates whether this stream supports the {@code mark()} and
         * {@code reset()} methods. Returns {@code true} since this class supports
         * these methods.
         *
         * @return {boolean} always {@code true}.
         * @see #mark(int)
         * @see #reset()
         */
        markSupported(): boolean;
        read$(): number;
        read$byte_A$int$int(buffer: number[], byteOffset: number, byteCount: number): number;
        /**
         *
         * @param {Array} buffer
         * @param {number} byteOffset
         * @param {number} byteCount
         * @return {number}
         */
        read(buffer?: any, byteOffset?: any, byteCount?: any): any;
        /**
         * Resets this stream to the last marked location. This implementation
         * resets the position to either the marked position, the start position
         * supplied in the constructor or 0 if neither has been provided.
         *
         * @see #mark(int)
         */
        reset(): void;
        /**
         * Skips {@code byteCount} bytes in this InputStream. Subsequent calls to
         * {@code read} will not return these bytes unless {@code reset} is used.
         * This implementation skips {@code byteCount} number of bytes in the target
         * stream. It does nothing and returns 0 if {@code byteCount} is negative.
         *
         * @return {number} the number of bytes actually skipped.
         * @param {number} byteCount
         */
        skip(byteCount: number): number;
    }
}
declare namespace java.io {
    /**
     * Wraps an existing {@link InputStream} and performs some transformation on
     * the input data while it is being read. Transformations can be anything from a
     * simple byte-wise filtering input data to an on-the-fly compression or
     * decompression of the underlying stream. Input streams that wrap another input
     * stream and provide some additional functionality on top of it usually inherit
     * from this class.
     *
     * @see FilterOutputStream
     * @extends java.io.InputStream
     * @class
     */
    class FilterInputStream extends java.io.InputStream {
        /**
         * The source input stream that is filtered.
         */
        in: java.io.InputStream;
        constructor(__in: java.io.InputStream);
        /**
         *
         * @return {number}
         */
        available(): number;
        /**
         * Closes this stream. This implementation closes the filtered stream.
         *
         * @throws IOException
         * if an error occurs while closing this stream.
         */
        close(): void;
        /**
         * Sets a mark position in this stream. The parameter {@code readlimit}
         * indicates how many bytes can be read before the mark is invalidated.
         * Sending {@code reset()} will reposition this stream back to the marked
         * position, provided that {@code readlimit} has not been surpassed.
         * <p>
         * This implementation sets a mark in the filtered stream.
         *
         * @param {number} readlimit
         * the number of bytes that can be read from this stream before
         * the mark is invalidated.
         * @see #markSupported()
         * @see #reset()
         */
        mark(readlimit: number): void;
        /**
         * Indicates whether this stream supports {@code mark()} and {@code reset()}.
         * This implementation returns whether or not the filtered stream supports
         * marking.
         *
         * @return {boolean} {@code true} if {@code mark()} and {@code reset()} are supported,
         * {@code false} otherwise.
         * @see #mark(int)
         * @see #reset()
         * @see #skip(long)
         */
        markSupported(): boolean;
        read(buffer?: any, byteOffset?: any, byteCount?: any): any;
        read$(): number;
        /**
         * Resets this stream to the last marked location. This implementation
         * resets the target stream.
         *
         * @throws IOException
         * if this stream is already closed, no mark has been set or the
         * mark is no longer valid because more than {@code readlimit}
         * bytes have been read since setting the mark.
         * @see #mark(int)
         * @see #markSupported()
         */
        reset(): void;
        /**
         * Skips {@code byteCount} bytes in this stream. Subsequent
         * calls to {@code read} will not return these bytes unless {@code reset} is
         * used. This implementation skips {@code byteCount} bytes in the
         * filtered stream.
         *
         * @return {number} the number of bytes actually skipped.
         * @throws IOException
         * if this stream is closed or another IOException occurs.
         * @see #mark(int)
         * @see #reset()
         * @param {number} byteCount
         */
        skip(byteCount: number): number;
    }
}
declare namespace java.io {
    /**
     * Constructs a new {@code ByteArrayOutputStream} with a default size of
     * {@code size} bytes. If more than {@code size} bytes are written to this
     * instance, the underlying byte array will expand.
     *
     * @param {number} size
     * initial size for the underlying byte array, must be
     * non-negative.
     * @throws IllegalArgumentException
     * if {@code size} < 0.
     * @class
     * @extends java.io.OutputStream
     */
    class ByteArrayOutputStream extends java.io.OutputStream {
        /**
         * The byte array containing the bytes written.
         */
        buf: number[];
        /**
         * The number of bytes written.
         */
        count: number;
        constructor(size?: any);
        /**
         * Closes this stream. This releases system resources used for this stream.
         *
         * @throws IOException
         * if an error occurs while attempting to close this stream.
         */
        close(): void;
        expand(i: number): void;
        /**
         * Resets this stream to the beginning of the underlying byte array. All
         * subsequent writes will overwrite any bytes previously stored in this
         * stream.
         */
        reset(): void;
        /**
         * Returns the total number of bytes written to this stream so far.
         *
         * @return {number} the number of bytes written to this stream.
         */
        size(): number;
        /**
         * Returns the contents of this ByteArrayOutputStream as a byte array. Any
         * changes made to the receiver after returning will not be reflected in the
         * byte array returned to the caller.
         *
         * @return {Array} this stream's current contents as a byte array.
         */
        toByteArray(): number[];
        toString$(): string;
        toString$int(hibyte: number): string;
        toString$java_lang_String(charsetName: string): string;
        /**
         * Returns the contents of this ByteArrayOutputStream as a string converted
         * according to the encoding declared in {@code charsetName}.
         *
         * @param {string} charsetName
         * a string representing the encoding to use when translating
         * this stream to a string.
         * @return {string} this stream's current contents as an encoded string.
         * @throws UnsupportedEncodingException
         * if the provided encoding is not supported.
         */
        toString(charsetName?: any): any;
        write$byte_A$int$int(buffer: number[], offset: number, len: number): void;
        /**
         * Writes {@code count} bytes from the byte array {@code buffer} starting at
         * offset {@code index} to this stream.
         *
         * @param {Array} buffer
         * the buffer to be written.
         * @param {number} offset
         * the initial position in {@code buffer} to retrieve bytes.
         * @param {number} len
         * the number of bytes of {@code buffer} to write.
         * @throws NullPointerException
         * if {@code buffer} is {@code null}.
         * @throws IndexOutOfBoundsException
         * if {@code offset < 0} or {@code len < 0}, or if
         * {@code offset + len} is greater than the length of
         * {@code buffer}.
         */
        write(buffer?: any, offset?: any, len?: any): any;
        write$int(oneByte: number): void;
        /**
         * Takes the contents of this stream and writes it to the output stream
         * {@code out}.
         *
         * @param {java.io.OutputStream} out
         * an OutputStream on which to write the contents of this stream.
         * @throws IOException
         * if an error occurs while writing to {@code out}.
         */
        writeTo(out: java.io.OutputStream): void;
    }
}
declare namespace java.io {
    /**
     * Constructs a new {@code FilterOutputStream} with {@code out} as its
     * target stream.
     *
     * @param {java.io.OutputStream} out
     * the target stream that this stream writes to.
     * @class
     * @extends java.io.OutputStream
     */
    class FilterOutputStream extends java.io.OutputStream {
        /**
         * The target output stream for this filter stream.
         */
        out: java.io.OutputStream;
        constructor(out: java.io.OutputStream);
        /**
         * Closes this stream. This implementation closes the target stream.
         *
         * @throws IOException
         * if an error occurs attempting to close this stream.
         */
        close(): void;
        /**
         * Ensures that all pending data is sent out to the target stream. This
         * implementation flushes the target stream.
         *
         * @throws IOException
         * if an error occurs attempting to flush this stream.
         */
        flush(): void;
        write(buffer?: any, offset?: any, count?: any): any;
        write$int(oneByte: number): void;
    }
}
declare namespace java.io {
    /**
     * JSweet implementation.
     * @param {java.io.Reader} in
     * @param {number} sz
     * @class
     * @extends java.io.Reader
     */
    class BufferedReader extends java.io.Reader {
        in: java.io.Reader;
        cb: string[];
        nChars: number;
        nextChar: number;
        static INVALIDATED: number;
        static UNMARKED: number;
        markedChar: number;
        readAheadLimit: number;
        skipLF: boolean;
        markedSkipLF: boolean;
        static defaultCharBufferSize: number;
        static defaultExpectedLineLength: number;
        constructor(__in?: any, sz?: any);
        ensureOpen(): void;
        fill(): void;
        read$(): number;
        read1(cbuf: string[], off: number, len: number): number;
        read$char_A$int$int(cbuf: string[], off: number, len: number): number;
        read(cbuf?: any, off?: any, len?: any): any;
        readLine$boolean(ignoreLF: boolean): string;
        readLine(ignoreLF?: any): any;
        readLine$(): string;
        skip(n: number): number;
        ready(): boolean;
        markSupported(): boolean;
        mark(readAheadLimit: number): void;
        reset(): void;
        close(): void;
    }
}
declare namespace java.io {
    /**
     * JSweet implementation.
     * @param {java.io.InputStream} in
     * @param {string} charsetName
     * @class
     * @extends java.io.Reader
     */
    class InputStreamReader extends java.io.Reader {
        in: java.io.InputStream;
        constructor(__in?: any, charsetName?: any);
        read$char_A$int$int(cbuf: string[], offset: number, length: number): number;
        read(cbuf?: any, offset?: any, length?: any): any;
        ready(): boolean;
        close(): void;
    }
}
declare namespace java.io {
    class StringReader extends java.io.Reader {
        charArray: string[];
        where: number;
        marked: number;
        markedLen: number;
        constructor(start: string);
        read$char_A$int$int(cbuf: string[], off: number, len: number): number;
        /**
         *
         * @param {Array} cbuf
         * @param {number} off
         * @param {number} len
         * @return {number}
         */
        read(cbuf?: any, off?: any, len?: any): any;
        /**
         *
         */
        close(): void;
        /**
         *
         * @return {boolean}
         */
        ready(): boolean;
        /**
         *
         * @return {boolean}
         */
        markSupported(): boolean;
        /**
         *
         * @param {number} readAheadLimit
         */
        mark(readAheadLimit: number): void;
        /**
         *
         */
        reset(): void;
    }
}
declare namespace java.io {
    /**
     * JSweet implementation (partial).
     *
     * TODO: actual support of charsets.
     * @param {java.io.OutputStream} out
     * @param {string} charsetName
     * @class
     * @extends java.io.Writer
     */
    class OutputStreamWriter extends java.io.Writer {
        out: java.io.OutputStream;
        constructor(out?: any, charsetName?: any);
        flushBuffer(): void;
        write$int(c: number): void;
        write$char_A$int$int(cbuf: string[], off: number, len: number): void;
        write(cbuf?: any, off?: any, len?: any): any;
        write$java_lang_String$int$int(str: string, off: number, len: number): void;
        flush(): void;
        close(): void;
    }
}
declare namespace java.lang {
    /**
     * A fast way to create strings using multiple appends.
     *
     * This class is an exact clone of {@link StringBuilder} except for the name.
     * Any change made to one should be mirrored in the other.
     * @param {*} s
     * @class
     * @extends java.lang.AbstractStringBuilder
     */
    class StringBuffer extends java.lang.AbstractStringBuilder implements java.lang.CharSequence, java.lang.Appendable {
        constructor(s?: any);
        append$boolean(x: boolean): java.lang.StringBuffer;
        append$char(x: string): java.lang.StringBuffer;
        append$char_A(x: string[]): java.lang.StringBuffer;
        append$char_A$int$int(x: string[], start: number, len: number): java.lang.StringBuffer;
        append(x?: any, start?: any, len?: any): any;
        append$java_lang_CharSequence(x: any): java.lang.StringBuffer;
        append$java_lang_CharSequence$int$int(x: any, start: number, end: number): java.lang.StringBuffer;
        append$double(x: number): java.lang.StringBuffer;
        append$float(x: number): java.lang.StringBuffer;
        append$int(x: number): java.lang.StringBuffer;
        append$long(x: number): java.lang.StringBuffer;
        append$java_lang_Object(x: any): java.lang.StringBuffer;
        append$java_lang_String(x: string): java.lang.StringBuffer;
        append$java_lang_StringBuffer(x: java.lang.StringBuffer): java.lang.StringBuffer;
        appendCodePoint(x: number): java.lang.StringBuffer;
        delete(start: number, end: number): java.lang.StringBuffer;
        deleteCharAt(start: number): java.lang.StringBuffer;
        insert$int$boolean(index: number, x: boolean): java.lang.StringBuffer;
        insert$int$char(index: number, x: string): java.lang.StringBuffer;
        insert$int$char_A(index: number, x: string[]): java.lang.StringBuffer;
        insert$int$char_A$int$int(index: number, x: string[], offset: number, len: number): java.lang.StringBuffer;
        insert(index?: any, x?: any, offset?: any, len?: any): any;
        insert$int$java_lang_CharSequence(index: number, chars: any): java.lang.StringBuffer;
        insert$int$java_lang_CharSequence$int$int(index: number, chars: any, start: number, end: number): java.lang.StringBuffer;
        insert$int$double(index: number, x: number): java.lang.StringBuffer;
        insert$int$float(index: number, x: number): java.lang.StringBuffer;
        insert$int$int(index: number, x: number): java.lang.StringBuffer;
        insert$int$long(index: number, x: number): java.lang.StringBuffer;
        insert$int$java_lang_Object(index: number, x: any): java.lang.StringBuffer;
        insert$int$java_lang_String(index: number, x: string): java.lang.StringBuffer;
        replace(start: number, end: number, toInsert: string): java.lang.StringBuffer;
        reverse(): java.lang.StringBuffer;
    }
}
declare namespace java.lang {
    /**
     * A fast way to create strings using multiple appends.
     *
     * This class is an exact clone of {@link StringBuffer} except for the name. Any
     * change made to one should be mirrored in the other.
     * @param {*} s
     * @class
     * @extends java.lang.AbstractStringBuilder
     */
    class StringBuilder extends java.lang.AbstractStringBuilder implements java.lang.CharSequence, java.lang.Appendable {
        constructor(s?: any);
        append$boolean(x: boolean): java.lang.StringBuilder;
        append$char(x: string): java.lang.StringBuilder;
        append$char_A(x: string[]): java.lang.StringBuilder;
        append$char_A$int$int(x: string[], start: number, len: number): java.lang.StringBuilder;
        append(x?: any, start?: any, len?: any): any;
        append$java_lang_CharSequence(x: any): java.lang.StringBuilder;
        append$java_lang_CharSequence$int$int(x: any, start: number, end: number): java.lang.StringBuilder;
        append$double(x: number): java.lang.StringBuilder;
        append$float(x: number): java.lang.StringBuilder;
        append$int(x: number): java.lang.StringBuilder;
        append$long(x: number): java.lang.StringBuilder;
        append$java_lang_Object(x: any): java.lang.StringBuilder;
        append$java_lang_String(x: string): java.lang.StringBuilder;
        append$java_lang_StringBuffer(x: java.lang.StringBuffer): java.lang.StringBuilder;
        appendCodePoint(x: number): java.lang.StringBuilder;
        delete(start: number, end: number): java.lang.StringBuilder;
        deleteCharAt(start: number): java.lang.StringBuilder;
        insert$int$boolean(index: number, x: boolean): java.lang.StringBuilder;
        insert$int$char(index: number, x: string): java.lang.StringBuilder;
        insert$int$char_A(index: number, x: string[]): java.lang.StringBuilder;
        insert$int$char_A$int$int(index: number, x: string[], offset: number, len: number): java.lang.StringBuilder;
        insert(index?: any, x?: any, offset?: any, len?: any): any;
        insert$int$java_lang_CharSequence(index: number, chars: any): java.lang.StringBuilder;
        insert$int$java_lang_CharSequence$int$int(index: number, chars: any, start: number, end: number): java.lang.StringBuilder;
        insert$int$double(index: number, x: number): java.lang.StringBuilder;
        insert$int$float(index: number, x: number): java.lang.StringBuilder;
        insert$int$int(index: number, x: number): java.lang.StringBuilder;
        insert$int$long(index: number, x: number): java.lang.StringBuilder;
        insert$int$java_lang_Object(index: number, x: any): java.lang.StringBuilder;
        insert$int$java_lang_String(index: number, x: string): java.lang.StringBuilder;
        replace(start: number, end: number, toInsert: string): java.lang.StringBuilder;
        reverse(): java.lang.StringBuilder;
    }
}
declare namespace java.io {
    /**
     * See <a
     * href="http://java.sun.com/javase/6/docs/api/java/io/IOException.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @param {java.lang.Throwable} throwable
     * @class
     * @extends java.lang.Exception
     */
    class IOException extends Error {
        constructor(message?: any, throwable?: any);
    }
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/CloneNotSupportedException.html">
     * the official Java API doc</a> for details.
     * @param {string} msg
     * @class
     * @extends java.lang.Exception
     */
    class CloneNotSupportedException extends Error {
        constructor(msg?: any);
    }
}
declare namespace java.lang {
    /**
     * See <a href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/NoSuchMethodException.html">the
     * official Java API doc</a> for details.
     *
     * This exception is never thrown by GWT or GWT's libraries, as GWT does not support reflection. It
     * is provided in GWT only for compatibility with user code that explicitly throws or catches it for
     * non-reflection purposes.
     * @param {string} message
     * @class
     * @extends java.lang.Exception
     */
    class NoSuchMethodException extends Error {
        constructor(message?: any);
    }
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/RuntimeException.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @param {java.lang.Throwable} cause
     * @class
     * @extends java.lang.Exception
     */
    class RuntimeException extends Error {
        constructor(message?: any, cause?: any, enableSuppression?: any, writableStackTrace?: any);
    }
}
declare namespace java.security {
    /**
     * A generic security exception type - <a
     * href="http://java.sun.com/j2se/1.4.2/docs/api/java/security/GeneralSecurityException.html">[Sun's
     * docs]</a>.
     * @param {string} msg
     * @class
     * @extends java.lang.Exception
     */
    class GeneralSecurityException extends Error {
        constructor(msg?: any);
    }
}
declare namespace java.text {
    /**
     * Emulation of {@code java.text.ParseException}.
     * @param {string} s
     * @param {number} errorOffset
     * @class
     * @extends java.lang.Exception
     */
    class ParseException extends Error {
        errorOffset: number;
        constructor(s: string, errorOffset: number);
        getErrorOffset(): number;
    }
}
declare namespace java.util {
    /**
     * Thrown when the subject of an observer cannot support additional observers.
     *
     * @param {string} message
     * @class
     * @extends java.lang.Exception
     */
    class TooManyListenersException extends Error {
        constructor(message?: any);
    }
}
declare namespace java.lang.ref {
    /**
     * This implements the reference API in a minimal way. In JavaScript, there is
     * no control over the reference and the GC. So this implementation's only
     * purpose is for compilation.
     * @param {*} referent
     * @class
     * @extends java.lang.ref.Reference
     */
    class WeakReference<T> extends java.lang.ref.Reference<T> {
        constructor(referent: T);
    }
}
declare namespace java.lang {
    /**
     * Constructs an {@code InternalError} with the specified detail
     * message and cause.  <p>Note that the detail message associated
     * with {@code cause} is <i>not</i> automatically incorporated in
     * this error's detail message.
     *
     * @param  {string} message the detail message (which is saved for later retrieval
     * by the {@link #getMessage()} method).
     * @param  {java.lang.Throwable} cause the cause (which is saved for later retrieval by the
     * {@link #getCause()} method).  (A {@code null} value is
     * permitted, and indicates that the cause is nonexistent or
     * unknown.)
     * @since  1.8
     * @class
     * @extends java.lang.VirtualMachineError
     * @author  unascribed
     */
    class InternalError extends java.lang.VirtualMachineError {
        static __java_lang_InternalError_serialVersionUID: number;
        constructor(message?: any, cause?: any);
    }
}
declare namespace java.nio {
    class ByteBuffer extends java.nio.Buffer implements java.lang.Comparable<ByteBuffer> {
        _buffer: ArrayBuffer;
        _array: Int8Array;
        _data: DataView;
        _order: java.nio.ByteOrder;
        constructor(_buffer: ArrayBuffer, readOnly: boolean);
        static allocate(capacity: number): ByteBuffer;
        /**
         *
         * @return {Array}
         */
        array(): number[];
        asReadOnlyBuffer(): ByteBuffer;
        compact(): ByteBuffer;
        compareTo$java_nio_ByteBuffer(byteBuffer: ByteBuffer): number;
        /**
         *
         * @param {java.nio.ByteBuffer} byteBuffer
         * @return {number}
         */
        compareTo(byteBuffer?: any): any;
        duplicate(): ByteBuffer;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        get$(): number;
        get$byte_A(dest: number[]): ByteBuffer;
        get$byte_A$int$int(dest: number[], offset: number, length: number): ByteBuffer;
        get(dest?: any, offset?: any, length?: any): any;
        get$int(from: number): number;
        getChar$(): string;
        getChar$int(from: number): string;
        getChar(from?: any): any;
        getDouble$(): number;
        getDouble$int(from: number): number;
        getDouble(from?: any): any;
        getFloat$(): number;
        getFloat$int(from: number): number;
        getFloat(from?: any): any;
        getInt$(): number;
        getInt$int(from: number): number;
        getInt(from?: any): any;
        getLong$(): number;
        getLong$int(from: number): number;
        getLong(from?: any): any;
        getShort$(): number;
        getShort$int(from: number): number;
        getShort(from?: any): any;
        order$(): java.nio.ByteOrder;
        order$java_nio_ByteOrder(newOrder: java.nio.ByteOrder): ByteBuffer;
        order(newOrder?: any): any;
        put$byte(b: number): ByteBuffer;
        put$byte_A(src: number[]): ByteBuffer;
        put$byte_A$int$int(src: number[], offset: number, length: number): ByteBuffer;
        put(src?: any, offset?: any, length?: any): any;
        put$int$byte(to: number, b: number): ByteBuffer;
        putChar$char(value: string): ByteBuffer;
        putChar$int$char(to: number, value: string): ByteBuffer;
        putChar(to?: any, value?: any): any;
        putDouble$double(value: number): ByteBuffer;
        putDouble$int$double(to: number, value: number): ByteBuffer;
        putDouble(to?: any, value?: any): any;
        putFloat$float(value: number): ByteBuffer;
        putFloat$int$float(to: number, value: number): ByteBuffer;
        putFloat(to?: any, value?: any): any;
        putInt$int(value: number): ByteBuffer;
        putInt$int$int(to: number, value: number): ByteBuffer;
        putInt(to?: any, value?: any): any;
        putLong$long(value: number): ByteBuffer;
        putLong$int$long(to: number, value: number): ByteBuffer;
        putLong(to?: any, value?: any): any;
        putShort$short(value: number): ByteBuffer;
        putShort$int$short(to: number, value: number): ByteBuffer;
        putShort(to?: any, value?: any): any;
        slice(): ByteBuffer;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        static wrap$byte_A(array: number[]): ByteBuffer;
        static wrap$byte_A$int$int(array: number[], offset: number, length: number): ByteBuffer;
        static wrap(array?: any, offset?: any, length?: any): any;
        static wrap$def_js_ArrayBuffer(array: ArrayBuffer): ByteBuffer;
        static wrap$def_js_ArrayBuffer$int$double(array: ArrayBuffer, offset: number, length: number): ByteBuffer;
    }
}
declare namespace javaemul.internal {
    /**
     * Provides Charset implementations.
     * @param {string} name
     * @class
     * @extends java.nio.charset.Charset
     */
    abstract class EmulatedCharset extends java.nio.charset.Charset {
        static UTF_8: EmulatedCharset;
        static UTF_8_$LI$(): EmulatedCharset;
        static ISO_LATIN_1: EmulatedCharset;
        static ISO_LATIN_1_$LI$(): EmulatedCharset;
        static ISO_8859_1: EmulatedCharset;
        static ISO_8859_1_$LI$(): EmulatedCharset;
        constructor(name: string);
        abstract getBytes(string: string): number[];
        abstract decodeString(bytes: number[], ofs: number, len: number): string[];
    }
    namespace EmulatedCharset {
        class LatinCharset extends javaemul.internal.EmulatedCharset {
            constructor(name: string);
            /**
             *
             * @param {string} str
             * @return {Array}
             */
            getBytes(str: string): number[];
            /**
             *
             * @param {Array} bytes
             * @param {number} ofs
             * @param {number} len
             * @return {Array}
             */
            decodeString(bytes: number[], ofs: number, len: number): string[];
        }
        class UtfCharset extends javaemul.internal.EmulatedCharset {
            constructor(name: string);
            /**
             *
             * @param {Array} bytes
             * @param {number} ofs
             * @param {number} len
             * @return {Array}
             */
            decodeString(bytes: number[], ofs: number, len: number): string[];
            /**
             *
             * @param {string} str
             * @return {Array}
             */
            getBytes(str: string): number[];
            /**
             * Encode a single character in UTF8.
             *
             * @param {Array} bytes byte array to store character in
             * @param {number} ofs offset into byte array to store first byte
             * @param {number} codePoint character to encode
             * @return {number} number of bytes consumed by encoding the character
             * @throws IllegalArgumentException if codepoint >= 2^26
             * @private
             */
            encodeUtf8(bytes: number[], ofs: number, codePoint: number): number;
        }
    }
}
declare namespace java.security {
    /**
     * Message Digest algorithm - <a href=
     * "http://java.sun.com/j2se/1.4.2/docs/api/java/security/MessageDigest.html"
     * >[Sun's docs]</a>.
     * @extends java.security.MessageDigestSpi
     * @class
     */
    abstract class MessageDigest extends java.security.MessageDigestSpi {
        static getInstance(algorithm: string): MessageDigest;
        static isEqual(digestA: number[], digestB: number[]): boolean;
        algorithm: string;
        constructor(algorithm: string);
        digest$(): number[];
        digest$byte_A(input: number[]): number[];
        digest$byte_A$int$int(buf: number[], offset: number, len: number): number;
        digest(buf?: any, offset?: any, len?: any): any;
        getAlgorithm(): string;
        getDigestLength(): number;
        reset(): void;
        update$byte(input: number): void;
        update$byte_A(input: number[]): void;
        update$byte_A$int$int(input: number[], offset: number, len: number): void;
        update(input?: any, offset?: any, len?: any): any;
    }
    namespace MessageDigest {
        class Md5Digest extends java.security.MessageDigest {
            static padding: number[];
            static padding_$LI$(): number[];
            /**
             * Converts a long to a 8-byte array using low order first.
             *
             * @param {number} n A long.
             * @return {Array} A byte[].
             */
            static toBytes(n: number): number[];
            /**
             * Converts a 64-byte array into a 16-int array.
             *
             * @param {Array} in A byte[].
             * @param {Array} out An int[].
             * @private
             */
            static byte2int(__in: number[], out: number[]): void;
            static f(x: number, y: number, z: number): number;
            static ff(a: number, b: number, c: number, d: number, x: number, s: number, ac: number): number;
            static g(x: number, y: number, z: number): number;
            static gg(a: number, b: number, c: number, d: number, x: number, s: number, ac: number): number;
            static h(x: number, y: number, z: number): number;
            static hh(a: number, b: number, c: number, d: number, x: number, s: number, ac: number): number;
            static i(x: number, y: number, z: number): number;
            static ii(a: number, b: number, c: number, d: number, x: number, s: number, ac: number): number;
            /**
             * Converts a 4-int array into a 16-byte array.
             *
             * @param {Array} in An int[].
             * @param {Array} out A byte[].
             * @private
             */
            static int2byte(__in: number[], out: number[]): void;
            buffer: number[];
            counter: number;
            oneByte: number[];
            remainder: number;
            state: number[];
            x: number[];
            constructor();
            engineDigest(buf?: any, offset?: any, len?: any): any;
            engineDigest$(): number[];
            /**
             *
             * @return {number}
             */
            engineGetDigestLength(): number;
            /**
             *
             */
            engineReset(): void;
            engineUpdate$byte(input: number): void;
            engineUpdate$byte_A$int$int(input: number[], offset: number, len: number): void;
            /**
             *
             * @param {Array} input
             * @param {number} offset
             * @param {number} len
             */
            engineUpdate(input?: any, offset?: any, len?: any): any;
            transform(buffer: number[]): void;
        }
    }
}
declare namespace java.util {
    /**
     * Skeletal implementation of the List interface. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/AbstractList.html">[Sun
     * docs]</a>
     *
     * @param <E> the element type.
     * @extends java.util.AbstractCollection
     * @class
     */
    abstract class AbstractList<E> extends java.util.AbstractCollection<E> implements java.util.List<E> {
        stream(): java.util.stream.Stream<any>;
        forEach(action: (p1: any) => void): void;
        removeIf(filter: (p1: any) => boolean): boolean;
        modCount: number;
        constructor();
        add$java_lang_Object(obj: E): boolean;
        add$int$java_lang_Object(index: number, element: E): void;
        /**
         *
         * @param {number} index
         * @param {*} element
         */
        add(index?: any, element?: any): any;
        addAll$int$java_util_Collection(index: number, c: java.util.Collection<any>): boolean;
        /**
         *
         * @param {number} index
         * @param {*} c
         * @return {boolean}
         */
        addAll(index?: any, c?: any): any;
        /**
         *
         */
        clear(): void;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        /**
         *
         * @param {number} index
         * @return {*}
         */
        abstract get(index: number): E;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @param {*} toFind
         * @return {number}
         */
        indexOf(toFind: any): number;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<E>;
        /**
         *
         * @param {*} toFind
         * @return {number}
         */
        lastIndexOf(toFind: any): number;
        listIterator$(): java.util.ListIterator<E>;
        listIterator$int(from: number): java.util.ListIterator<E>;
        /**
         *
         * @param {number} from
         * @return {*}
         */
        listIterator(from?: any): any;
        remove$int(index: number): E;
        /**
         *
         * @param {number} index
         * @return {*}
         */
        remove(index?: any): any;
        /**
         *
         * @param {number} index
         * @param {*} o
         * @return {*}
         */
        set(index: number, o: E): E;
        /**
         *
         * @param {number} fromIndex
         * @param {number} toIndex
         * @return {*}
         */
        subList(fromIndex: number, toIndex: number): java.util.List<E>;
        removeRange(fromIndex: number, endIndex: number): void;
        abstract size(): any;
    }
    namespace AbstractList {
        class IteratorImpl implements java.util.Iterator<any> {
            __parent: any;
            forEachRemaining(consumer: (p1: any) => void): void;
            i: number;
            last: number;
            constructor(__parent: any);
            /**
             *
             * @return {boolean}
             */
            hasNext(): boolean;
            /**
             *
             * @return {*}
             */
            next(): any;
            /**
             *
             */
            remove(): void;
        }
        class SubList<E> extends java.util.AbstractList<E> {
            wrapped: java.util.List<E>;
            fromIndex: number;
            __size: number;
            constructor(wrapped: java.util.List<E>, fromIndex: number, toIndex: number);
            add$int$java_lang_Object(index: number, element: E): void;
            /**
             *
             * @param {number} index
             * @param {*} element
             */
            add(index?: any, element?: any): any;
            /**
             *
             * @param {number} index
             * @return {*}
             */
            get(index: number): E;
            remove$int(index: number): E;
            /**
             *
             * @param {number} index
             * @return {*}
             */
            remove(index?: any): any;
            /**
             *
             * @param {number} index
             * @param {*} element
             * @return {*}
             */
            set(index: number, element: E): E;
            /**
             *
             * @return {number}
             */
            size(): number;
        }
        /**
         * Implementation of <code>ListIterator</code> for abstract lists.
         * @extends java.util.AbstractList.IteratorImpl
         * @class
         */
        class ListIteratorImpl extends AbstractList.IteratorImpl implements java.util.ListIterator<any> {
            __parent: any;
            forEachRemaining(consumer: (p1: any) => void): void;
            constructor(__parent: any, start?: any);
            /**
             *
             * @param {*} o
             */
            add(o: any): void;
            /**
             *
             * @return {boolean}
             */
            hasPrevious(): boolean;
            /**
             *
             * @return {number}
             */
            nextIndex(): number;
            /**
             *
             * @return {*}
             */
            previous(): any;
            /**
             *
             * @return {number}
             */
            previousIndex(): number;
            /**
             *
             * @param {*} o
             */
            set(o: any): void;
        }
    }
}
declare namespace java.util {
    /**
     * Skeletal implementation of the Queue interface. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/AbstractQueue.html">[Sun
     * docs]</a>
     *
     * @param <E> element type.
     * @extends java.util.AbstractCollection
     * @class
     */
    abstract class AbstractQueue<E> extends java.util.AbstractCollection<E> implements java.util.Queue<E> {
        stream(): java.util.stream.Stream<any>;
        forEach(action: (p1: any) => void): void;
        removeIf(filter: (p1: any) => boolean): boolean;
        constructor();
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        add(o: E): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        addAll(c: java.util.Collection<any>): boolean;
        /**
         *
         */
        clear(): void;
        /**
         *
         * @return {*}
         */
        element(): E;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        abstract offer(o: E): boolean;
        /**
         *
         * @return {*}
         */
        abstract peek(): E;
        /**
         *
         * @return {*}
         */
        abstract poll(): E;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        remove(o?: any): any;
        remove$(): E;
        abstract size(): any;
        abstract iterator(): any;
    }
}
declare namespace java.util {
    /**
     * Skeletal implementation of the Set interface. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/AbstractSet.html">[Sun
     * docs]</a>
     *
     * @param <E> the element type.
     * @class
     * @extends java.util.AbstractCollection
     */
    abstract class AbstractSet<E> extends java.util.AbstractCollection<E> implements java.util.Set<E> {
        stream(): java.util.stream.Stream<any>;
        forEach(action: (p1: any) => void): void;
        removeIf(filter: (p1: any) => boolean): boolean;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        removeAll(c: java.util.Collection<any>): boolean;
        abstract size(): any;
        abstract iterator(): any;
        constructor();
    }
}
declare namespace java.util {
    /**
     * A simple wrapper around JavaScript Map for key type is string.
     * @param {java.util.AbstractHashMap} host
     * @class
     */
    class InternalStringMap<K, V> implements java.lang.Iterable<java.util.Map.Entry<K, V>> {
        forEach(action: (p1: any) => void): void;
        backingMap: java.util.InternalJsMap<V>;
        host: java.util.AbstractHashMap<K, V>;
        size: number;
        /**
         * A mod count to track 'value' replacements in map to ensure that the
         * 'value' that we have in the iterator entry is guaranteed to be still
         * correct. This is to optimize for the common scenario where the values are
         * not modified during iterations where the entries are never stale.
         */
        valueMod: number;
        constructor(host: java.util.AbstractHashMap<K, V>);
        contains(key: string): boolean;
        get(key: string): V;
        put(key: string, value: V): V;
        remove(key: string): V;
        getSize(): number;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<java.util.Map.Entry<K, V>>;
        newMapEntry(entry: java.util.InternalJsMap.IteratorEntry<V>, lastValueMod: number): java.util.Map.Entry<K, V>;
        static toNullIfUndefined<T>(value: T): T;
    }
    namespace InternalStringMap {
        class InternalStringMap$0 implements java.util.Iterator<java.util.Map.Entry<any, any>> {
            __parent: any;
            forEachRemaining(consumer: (p1: any) => void): void;
            entries: java.util.InternalJsMap.Iterator<any>;
            current: java.util.InternalJsMap.IteratorEntry<any>;
            last: java.util.InternalJsMap.IteratorEntry<any>;
            /**
             *
             * @return {boolean}
             */
            hasNext(): boolean;
            /**
             *
             * @return {*}
             */
            next(): java.util.Map.Entry<any, any>;
            /**
             *
             */
            remove(): void;
            constructor(__parent: any);
        }
        class InternalStringMap$1 extends java.util.AbstractMapEntry<any, any> {
            private entry;
            private lastValueMod;
            __parent: any;
            /**
             *
             * @return {*}
             */
            getKey(): any;
            /**
             *
             * @return {*}
             */
            getValue(): any;
            /**
             *
             * @param {*} object
             * @return {*}
             */
            setValue(object: any): any;
            constructor(__parent: any, entry: any, lastValueMod: any);
        }
    }
}
declare namespace javaemul.internal {
    /**
     * Intrinsic string class.
     * @class
     */
    class StringHelper {
        static CASE_INSENSITIVE_ORDER: java.util.Comparator<string>;
        static CASE_INSENSITIVE_ORDER_$LI$(): java.util.Comparator<string>;
        static copyValueOf$char_A(v: string[]): string;
        static copyValueOf$char_A$int$int(v: string[], offset: number, count: number): string;
        static copyValueOf(v?: any, offset?: any, count?: any): any;
        static valueOf$boolean(x: boolean): string;
        static valueOf$char(x: string): string;
        static valueOf$char_A$int$int(x: string[], offset: number, count: number): string;
        static valueOf(x?: any, offset?: any, count?: any): any;
        static fromCharCode(array: any[]): string;
        static valueOf$char_A(x: string[]): string;
        static valueOf$double(x: number): string;
        static valueOf$float(x: number): string;
        static valueOf$int(x: number): string;
        static valueOf$long(x: number): string;
        static valueOf$java_lang_Object(x: any): string;
        /**
         * This method converts Java-escaped dollar signs "\$" into
         * JavaScript-escaped dollar signs "$$", and removes all other lone
         * backslashes, which serve as escapes in Java but are passed through
         * literally in JavaScript.
         *
         * @skip
         * @param {string} replaceStr
         * @return {string}
         * @private
         */
        static translateReplaceString(replaceStr: string): string;
        static compareTo(thisStr: string, otherStr: string): number;
        static getCharset(charsetName: string): java.nio.charset.Charset;
        static fromCodePoint(codePoint: number): string;
        static format(formatString: string, ...args: any[]): string;
        constructor();
    }
    namespace StringHelper {
        class StringHelper$0 {
            compare$java_lang_String$java_lang_String(a: string, b: string): number;
            /**
             *
             * @param {string} a
             * @param {string} b
             * @return {number}
             */
            compare(a?: any, b?: any): any;
            constructor();
        }
    }
}
declare namespace java.sql {
    /**
     * An implementation of java.sql.Date. Derived from
     * http://java.sun.com/j2se/1.5.0/docs/api/java/sql/Date.html
     * @param {number} year
     * @param {number} month
     * @param {number} day
     * @class
     * @extends java.util.Date
     */
    class Date extends java.util.Date {
        static valueOf(s: string): Date;
        constructor(year?: any, month?: any, day?: any);
        /**
         *
         * @return {number}
         */
        getHours(): number;
        /**
         *
         * @return {number}
         */
        getMinutes(): number;
        /**
         *
         * @return {number}
         */
        getSeconds(): number;
        /**
         *
         * @param {number} i
         */
        setHours(i: number): void;
        /**
         *
         * @param {number} i
         */
        setMinutes(i: number): void;
        /**
         *
         * @param {number} i
         */
        setSeconds(i: number): void;
    }
}
declare namespace java.sql {
    /**
     * An implementation of java.sql.Time. Derived from
     * http://java.sun.com/j2se/1.5.0/docs/api/java/sql/Time.html
     * @param {number} hour
     * @param {number} minute
     * @param {number} second
     * @class
     * @extends java.util.Date
     */
    class Time extends java.util.Date {
        static valueOf(s: string): Time;
        constructor(hour?: any, minute?: any, second?: any);
        /**
         *
         * @return {number}
         */
        getDate(): number;
        /**
         *
         * @return {number}
         */
        getDay(): number;
        /**
         *
         * @return {number}
         */
        getMonth(): number;
        /**
         *
         * @return {number}
         */
        getYear(): number;
        /**
         *
         * @param {number} i
         */
        setDate(i: number): void;
        /**
         *
         * @param {number} i
         */
        setMonth(i: number): void;
        /**
         *
         * @param {number} i
         */
        setYear(i: number): void;
    }
}
declare namespace java.sql {
    /**
     * An implementation of java.sql.Timestame. Derived from
     * http://java.sun.com/j2se/1.5.0/docs/api/java/sql/Timestamp.html. This is
     * basically just regular Date decorated with a nanoseconds field.
     * @param {number} year
     * @param {number} month
     * @param {number} date
     * @param {number} hour
     * @param {number} minute
     * @param {number} second
     * @param {number} nano
     * @class
     * @extends java.util.Date
     */
    class Timestamp extends java.util.Date {
        static valueOf(s: string): Timestamp;
        static padNine(value: number): string;
        /**
         * Stores the nanosecond resolution of the timestamp; must be kept in sync
         * with the sub-second part of Date.millis.
         */
        nanos: number;
        constructor(year?: any, month?: any, date?: any, hour?: any, minute?: any, second?: any, nano?: any);
        after$java_sql_Timestamp(ts: Timestamp): boolean;
        after(ts?: any): any;
        before$java_sql_Timestamp(ts: Timestamp): boolean;
        before(ts?: any): any;
        compareTo$java_util_Date(o: java.util.Date): number;
        compareTo$java_sql_Timestamp(o: Timestamp): number;
        compareTo(o?: any): any;
        equals$java_lang_Object(ts: any): boolean;
        equals$java_sql_Timestamp(ts: Timestamp): boolean;
        equals(ts?: any): any;
        getNanos(): number;
        /**
         *
         * @return {number}
         */
        getTime(): number;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        setNanos(n: number): void;
        /**
         *
         * @param {number} time
         */
        setTime(time: number): void;
    }
}
declare namespace java.util.logging {
    /**
     * A simple console logger used in super dev mode.
     * @extends java.util.logging.Handler
     * @class
     */
    class SimpleConsoleLogHandler extends java.util.logging.Handler {
        /**
         *
         * @param {java.util.logging.LogRecord} record
         */
        publish(record: java.util.logging.LogRecord): void;
        toConsoleLogLevel(level: java.util.logging.Level): string;
        /**
         *
         */
        close(): void;
        /**
         *
         */
        flush(): void;
    }
}
declare namespace java.util {
    class Scanner implements java.util.Iterator<string>, java.io.Closeable {
        forEachRemaining(consumer: (p1: any) => void): void;
        remove(): void;
        static digit: string;
        static decimalSeparator: string;
        static numeral: string;
        static numeral_$LI$(): string;
        static decimalNumeral: string;
        static decimalNumeral_$LI$(): string;
        static exponent: string;
        static exponent_$LI$(): string;
        static decimal: string;
        static decimal_$LI$(): string;
        static hexFloat: string;
        static nonNumber: string;
        static signedNonNumber: string;
        static signedNonNumber_$LI$(): string;
        static booleanPattern: java.util.regex.Pattern;
        static booleanPattern_$LI$(): java.util.regex.Pattern;
        static integerPattern: java.util.regex.Pattern;
        static integerPattern_$LI$(): java.util.regex.Pattern;
        static floatPattern: java.util.regex.Pattern;
        static floatPattern_$LI$(): java.util.regex.Pattern;
        static endLinePattern: java.util.regex.Pattern;
        static endLinePattern_$LI$(): java.util.regex.Pattern;
        static whiteSpacePattern: java.util.regex.Pattern;
        static whiteSpacePattern_$LI$(): java.util.regex.Pattern;
        reader: java.io.Reader;
        currentDelimiter: java.util.regex.Pattern;
        matcher: java.util.regex.Matcher;
        buf: string[];
        bufferFilledLength: number;
        currentPosition: number;
        nextTokenStart: number;
        nextDelimiterStart: number;
        nextDelimiterEnd: number;
        nextDelimiterWithPattern: java.util.regex.Pattern;
        defaultRadix: number;
        closed: boolean;
        constructor(string?: any);
        /**
         *
         */
        close(): void;
        delimiter(): java.util.regex.Pattern;
        useDelimiter$java_lang_String(currentDelimiter: string): Scanner;
        useDelimiter(currentDelimiter?: any): any;
        useDelimiter$java_util_regex_Pattern(currentDelimiter: java.util.regex.Pattern): Scanner;
        hasNext$(): boolean;
        searchNextTo$java_util_regex_Pattern(pattern: java.util.regex.Pattern): void;
        searchNextTo$java_util_regex_Pattern$boolean(pattern: java.util.regex.Pattern, canBeEmpty: boolean): void;
        searchNextTo(pattern?: any, canBeEmpty?: any): any;
        /**
         *
         * @return {string}
         */
        next(): string;
        hasNext$java_util_regex_Pattern(pattern: java.util.regex.Pattern): boolean;
        hasNext(pattern?: any): any;
        hasNext$java_lang_String(pattern: string): boolean;
        radix(): number;
        hasNextBoolean(): boolean;
        nextBoolean(): boolean;
        hasNextByte(): boolean;
        nextByte(): number;
        hasNextDouble(): boolean;
        nextDouble(): number;
        hasNextFloat(): boolean;
        nextFloat(): number;
        hasNextInt(): boolean;
        nextInt(): number;
        hasNextLine(): boolean;
        nextLine(): string;
        hasNextLong(): boolean;
        nextLong(): number;
        hasNextShort(): boolean;
        nextShort(): number;
        reset(): Scanner;
        skip$java_lang_String(pattern: string): Scanner;
        skip(pattern?: any): any;
        skip$java_util_regex_Pattern(pattern: java.util.regex.Pattern): Scanner;
        findInLine$java_lang_String(pattern: string): string;
        findInLine(pattern?: any): any;
        findInLine$java_util_regex_Pattern(pattern: java.util.regex.Pattern): string;
        match(): java.util.regex.MatchResult;
    }
}
declare namespace java.util {
    class Timer {
        static nextSerialNumber: number;
        name: string;
        timeouts: Array<java.util.TimerTask>;
        intervals: Array<java.util.TimerTask>;
        constructor(name?: any, daemon?: any);
        schedule$java_util_TimerTask$long(task: java.util.TimerTask, delay: number): void;
        schedule$java_util_TimerTask$java_util_Date(task: java.util.TimerTask, time: Date): void;
        schedule$java_util_TimerTask$long$long(task: java.util.TimerTask, delay: number, period: number): void;
        schedule$java_util_TimerTask$java_util_Date$long(task: java.util.TimerTask, time: Date, period: number): void;
        schedule(task?: any, time?: any, period?: any): any;
        scheduleAtFixedRate$java_util_TimerTask$long$long(task: java.util.TimerTask, delay: number, period: number): void;
        scheduleAtFixedRate$java_util_TimerTask$java_util_Date$long(task: java.util.TimerTask, time: Date, period: number): void;
        scheduleAtFixedRate(task?: any, time?: any, period?: any): any;
        cancel(): void;
        purge(): number;
    }
    namespace Timer {
        class Timer$0 extends java.util.TimerTask {
            private task;
            __parent: any;
            /**
             *
             */
            run(): void;
            constructor(__parent: any, task: any);
        }
    }
}
declare namespace javaemul.internal {
    /**
     * Wraps native <code>byte</code> as an object.
     * @param {number} value
     * @class
     * @extends javaemul.internal.NumberHelper
     */
    class ByteHelper extends javaemul.internal.NumberHelper implements java.lang.Comparable<ByteHelper> {
        static MIN_VALUE: number;
        static MIN_VALUE_$LI$(): number;
        static MAX_VALUE: number;
        static MAX_VALUE_$LI$(): number;
        static SIZE: number;
        static TYPE: any;
        static TYPE_$LI$(): any;
        static compare(x: number, y: number): number;
        static decode(s: string): number;
        /**
         * @skip
         *
         * Here for shared implementation with Arrays.hashCode
         * @param {number} b
         * @return {number}
         */
        static hashCode(b: number): number;
        static parseByte(s: string, radix?: number): number;
        static toString(b: number): string;
        static valueOf$byte(b: number): number;
        static valueOf$java_lang_String(s: string): number;
        static valueOf$java_lang_String$int(s: string, radix: number): number;
        static valueOf(s?: any, radix?: any): any;
        value: number;
        constructor(s?: any);
        /**
         *
         * @return {number}
         */
        byteValue(): number;
        compareTo$javaemul_internal_ByteHelper(b: ByteHelper): number;
        /**
         *
         * @param {javaemul.internal.ByteHelper} b
         * @return {number}
         */
        compareTo(b?: any): any;
        /**
         *
         * @return {number}
         */
        doubleValue(): number;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        /**
         *
         * @return {number}
         */
        floatValue(): number;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {number}
         */
        intValue(): number;
        /**
         *
         * @return {number}
         */
        longValue(): number;
        /**
         *
         * @return {number}
         */
        shortValue(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
    namespace ByteHelper {
        /**
         * Use nested class to avoid clinit on outer.
         * @class
         */
        class BoxedValues {
            static boxedValues: number[];
            static boxedValues_$LI$(): number[];
            constructor();
        }
    }
}
declare namespace javaemul.internal {
    /**
     * Wraps a primitive <code>double</code> as an object.
     * @param {number} value
     * @class
     * @extends javaemul.internal.NumberHelper
     */
    class DoubleHelper extends javaemul.internal.NumberHelper implements java.lang.Comparable<DoubleHelper> {
        static MAX_VALUE: number;
        static MIN_VALUE: number;
        static MIN_NORMAL: number;
        static MAX_EXPONENT: number;
        static MIN_EXPONENT: number;
        static NaN: number;
        static NEGATIVE_INFINITY: number;
        static POSITIVE_INFINITY: number;
        static SIZE: number;
        static POWER_512: number;
        static POWER_MINUS_512: number;
        static POWER_256: number;
        static POWER_MINUS_256: number;
        static POWER_128: number;
        static POWER_MINUS_128: number;
        static POWER_64: number;
        static POWER_MINUS_64: number;
        static POWER_52: number;
        static POWER_MINUS_52: number;
        static POWER_32: number;
        static POWER_MINUS_32: number;
        static POWER_31: number;
        static POWER_20: number;
        static POWER_MINUS_20: number;
        static POWER_16: number;
        static POWER_MINUS_16: number;
        static POWER_8: number;
        static POWER_MINUS_8: number;
        static POWER_4: number;
        static POWER_MINUS_4: number;
        static POWER_2: number;
        static POWER_MINUS_2: number;
        static POWER_1: number;
        static POWER_MINUS_1: number;
        static POWER_MINUS_1022: number;
        static compare(x: number, y: number): number;
        static doubleToLongBits(value: number): number;
        /**
         * @skip Here for shared implementation with Arrays.hashCode
         * @param {number} d
         * @return {number}
         */
        static hashCode(d: number): number;
        static isInfinite(x: number): boolean;
        static isNaN(x: number): boolean;
        static longBitsToDouble(bits: number): number;
        static parseDouble(s: string): number;
        static toString(b: number): string;
        static valueOf$double(d: number): number;
        static valueOf$java_lang_String(s: string): number;
        static valueOf(s?: any): any;
        constructor(s?: any);
        /**
         *
         * @return {number}
         */
        byteValue(): number;
        compareTo$javaemul_internal_DoubleHelper(b: DoubleHelper): number;
        /**
         *
         * @param {javaemul.internal.DoubleHelper} b
         * @return {number}
         */
        compareTo(b?: any): any;
        /**
         *
         * @return {number}
         */
        doubleValue(): number;
        static unsafeCast(instance: any): number;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        /**
         *
         * @return {number}
         */
        floatValue(): number;
        /**
         * Performance caution: using Double objects as map keys is not recommended.
         * Using double values as keys is generally a bad idea due to difficulty
         * determining exact equality. In addition, there is no efficient JavaScript
         * equivalent of <code>doubleToIntBits</code>. As a result, this method
         * computes a hash code by truncating the whole number portion of the
         * double, which may lead to poor performance for certain value sets if
         * Doubles are used as keys in a {@link java.util.HashMap}.
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {number}
         */
        intValue(): number;
        isInfinite(): boolean;
        isNaN(): boolean;
        /**
         *
         * @return {number}
         */
        longValue(): number;
        /**
         *
         * @return {number}
         */
        shortValue(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
    namespace DoubleHelper {
        class PowersTable {
            static powers: number[];
            static powers_$LI$(): number[];
            static invPowers: number[];
            static invPowers_$LI$(): number[];
            constructor();
        }
    }
}
declare namespace javaemul.internal {
    /**
     * Wraps a primitive <code>float</code> as an object.
     * @param {number} value
     * @class
     * @extends javaemul.internal.NumberHelper
     */
    class FloatHelper extends javaemul.internal.NumberHelper implements java.lang.Comparable<FloatHelper> {
        static MAX_VALUE: number;
        static MIN_VALUE: number;
        static MAX_EXPONENT: number;
        static MIN_EXPONENT: number;
        static MIN_NORMAL: number;
        static NaN: number;
        static NEGATIVE_INFINITY: number;
        static POSITIVE_INFINITY: number;
        static SIZE: number;
        static POWER_31_INT: number;
        static compare(x: number, y: number): number;
        static floatToIntBits(value: number): number;
        /**
         * @skip Here for shared implementation with Arrays.hashCode.
         * @param {number} f
         * @return {number} hash value of float (currently just truncated to int)
         */
        static hashCode(f: number): number;
        static intBitsToFloat(bits: number): number;
        static isInfinite(x: number): boolean;
        static isNaN(x: number): boolean;
        static parseFloat(s: string): number;
        static toString(b: number): string;
        static valueOf$float(f: number): number;
        static valueOf$java_lang_String(s: string): number;
        static valueOf(s?: any): any;
        value: number;
        constructor(s?: any);
        /**
         *
         * @return {number}
         */
        byteValue(): number;
        compareTo$javaemul_internal_FloatHelper(b: FloatHelper): number;
        /**
         *
         * @param {javaemul.internal.FloatHelper} b
         * @return {number}
         */
        compareTo(b?: any): any;
        /**
         *
         * @return {number}
         */
        doubleValue(): number;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        /**
         *
         * @return {number}
         */
        floatValue(): number;
        /**
         * Performance caution: using Float objects as map keys is not recommended.
         * Using floating point values as keys is generally a bad idea due to
         * difficulty determining exact equality. In addition, there is no efficient
         * JavaScript equivalent of <code>floatToIntBits</code>. As a result, this
         * method computes a hash code by truncating the whole number portion of the
         * float, which may lead to poor performance for certain value sets if
         * Floats are used as keys in a {@link java.util.HashMap}.
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {number}
         */
        intValue(): number;
        isInfinite(): boolean;
        isNaN(): boolean;
        /**
         *
         * @return {number}
         */
        longValue(): number;
        /**
         *
         * @return {number}
         */
        shortValue(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
}
declare namespace javaemul.internal {
    /**
     * Wraps a primitive <code>int</code> as an object.
     * @param {number} value
     * @class
     * @extends javaemul.internal.NumberHelper
     */
    class IntegerHelper extends javaemul.internal.NumberHelper implements java.lang.Comparable<IntegerHelper> {
        static MAX_VALUE: number;
        static MIN_VALUE: number;
        static SIZE: number;
        static bitCount(x: number): number;
        static compare(x: number, y: number): number;
        static decode(s: string): number;
        /**
         * @skip
         *
         * Here for shared implementation with Arrays.hashCode
         * @param {number} i
         * @return {number}
         */
        static hashCode(i: number): number;
        static highestOneBit(i: number): number;
        static lowestOneBit(i: number): number;
        static numberOfLeadingZeros(i: number): number;
        static numberOfTrailingZeros(i: number): number;
        static parseInt(s: string, radix?: number): number;
        static reverse(i: number): number;
        static reverseBytes(i: number): number;
        static rotateLeft(i: number, distance: number): number;
        static rotateRight(i: number, distance: number): number;
        static signum(i: number): number;
        static toBinaryString(value: number): string;
        static toHexString(value: number): string;
        static toOctalString(value: number): string;
        static toString$int(value: number): string;
        static toString$int$int(value: number, radix: number): string;
        static toString(value?: any, radix?: any): any;
        static valueOf$int(i: number): number;
        static valueOf$java_lang_String(s: string): number;
        static valueOf$java_lang_String$int(s: string, radix: number): number;
        static valueOf(s?: any, radix?: any): any;
        static toRadixString(value: number, radix: number): string;
        static toUnsignedRadixString(value: number, radix: number): string;
        value: number;
        constructor(s?: any);
        /**
         *
         * @return {number}
         */
        byteValue(): number;
        compareTo$javaemul_internal_IntegerHelper(b: IntegerHelper): number;
        /**
         *
         * @param {javaemul.internal.IntegerHelper} b
         * @return {number}
         */
        compareTo(b?: any): any;
        /**
         *
         * @return {number}
         */
        doubleValue(): number;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        /**
         *
         * @return {number}
         */
        floatValue(): number;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {number}
         */
        intValue(): number;
        /**
         *
         * @return {number}
         */
        longValue(): number;
        /**
         *
         * @return {number}
         */
        shortValue(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
        static getInteger(nm: string): number;
    }
    namespace IntegerHelper {
        /**
         * Use nested class to avoid clinit on outer.
         * @class
         */
        class BoxedValues {
            static boxedValues: number[];
            static boxedValues_$LI$(): number[];
            constructor();
        }
        /**
         * Use nested class to avoid clinit on outer.
         * @class
         */
        class ReverseNibbles {
            /**
             * A fast-lookup of the reversed bits of all the nibbles 0-15. Used to
             * implement {@link #reverse(int)}.
             */
            static reverseNibbles: number[];
            static reverseNibbles_$LI$(): number[];
            constructor();
        }
    }
}
declare namespace javaemul.internal {
    /**
     * Wraps a primitive <code>long</code> as an object.
     * @param {number} value
     * @class
     * @extends javaemul.internal.NumberHelper
     */
    class LongHelper extends javaemul.internal.NumberHelper implements java.lang.Comparable<LongHelper> {
        static MAX_VALUE: number;
        static MIN_VALUE: number;
        static SIZE: number;
        static bitCount(i: number): number;
        static compare(x: number, y: number): number;
        static decode(s: string): number;
        /**
         * @skip Here for shared implementation with Arrays.hashCode
         * @param {number} l
         * @return {number}
         */
        static hashCode(l: number): number;
        static highestOneBit(i: number): number;
        static lowestOneBit(i: number): number;
        static numberOfLeadingZeros(i: number): number;
        static numberOfTrailingZeros(i: number): number;
        static parseLong(s: string, radix?: number): number;
        static reverse(i: number): number;
        static reverseBytes(i: number): number;
        static rotateLeft(i: number, distance: number): number;
        static rotateRight(i: number, distance: number): number;
        static signum(i: number): number;
        static toBinaryString(value: number): string;
        static toHexString(value: number): string;
        static toOctalString(value: number): string;
        static toString$long(value: number): string;
        static toString$long$int(value: number, intRadix: number): string;
        static toString(value?: any, intRadix?: any): any;
        static valueOf$long(i: number): number;
        static valueOf$java_lang_String(s: string): number;
        static valueOf$java_lang_String$int(s: string, radix: number): number;
        static valueOf(s?: any, radix?: any): any;
        static toPowerOfTwoUnsignedString(value: number, shift: number): string;
        value: number;
        constructor(s?: any);
        /**
         *
         * @return {number}
         */
        byteValue(): number;
        compareTo$javaemul_internal_LongHelper(b: LongHelper): number;
        /**
         *
         * @param {javaemul.internal.LongHelper} b
         * @return {number}
         */
        compareTo(b?: any): any;
        /**
         *
         * @return {number}
         */
        doubleValue(): number;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        /**
         *
         * @return {number}
         */
        floatValue(): number;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {number}
         */
        intValue(): number;
        /**
         *
         * @return {number}
         */
        longValue(): number;
        /**
         *
         * @return {number}
         */
        shortValue(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
    namespace LongHelper {
        /**
         * Use nested class to avoid clinit on outer.
         * @class
         */
        class BoxedValues {
            static boxedValues: number[];
            static boxedValues_$LI$(): number[];
            constructor();
        }
    }
}
declare namespace javaemul.internal {
    /**
     * Wraps a primitive <code>short</code> as an object.
     * @param {number} value
     * @class
     * @extends javaemul.internal.NumberHelper
     */
    class ShortHelper extends javaemul.internal.NumberHelper implements java.lang.Comparable<ShortHelper> {
        static MIN_VALUE: number;
        static MIN_VALUE_$LI$(): number;
        static MAX_VALUE: number;
        static MAX_VALUE_$LI$(): number;
        static SIZE: number;
        static TYPE: any;
        static TYPE_$LI$(): any;
        static compare(x: number, y: number): number;
        static decode(s: string): number;
        /**
         * @skip Here for shared implementation with Arrays.hashCode
         * @param {number} s
         * @return {number}
         */
        static hashCode(s: number): number;
        static parseShort(s: string, radix?: number): number;
        static reverseBytes(s: number): number;
        static toString(b: number): string;
        static valueOf$short(s: number): number;
        static valueOf$java_lang_String(s: string): number;
        static valueOf$java_lang_String$int(s: string, radix: number): number;
        static valueOf(s?: any, radix?: any): any;
        value: number;
        constructor(s?: any);
        /**
         *
         * @return {number}
         */
        byteValue(): number;
        compareTo$javaemul_internal_ShortHelper(b: ShortHelper): number;
        /**
         *
         * @param {javaemul.internal.ShortHelper} b
         * @return {number}
         */
        compareTo(b?: any): any;
        /**
         *
         * @return {number}
         */
        doubleValue(): number;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        equals(o: any): boolean;
        /**
         *
         * @return {number}
         */
        floatValue(): number;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {number}
         */
        intValue(): number;
        /**
         *
         * @return {number}
         */
        longValue(): number;
        /**
         *
         * @return {number}
         */
        shortValue(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
    namespace ShortHelper {
        /**
         * Use nested class to avoid clinit on outer.
         * @class
         */
        class BoxedValues {
            static boxedValues: number[];
            static boxedValues_$LI$(): number[];
            constructor();
        }
    }
}
declare namespace javaemul.internal.stream {
    class StreamRowAllFilter extends javaemul.internal.stream.TerminalStreamRow {
        predicate: any;
        predicateValue: boolean;
        attempts: number;
        getPredicateValue(): boolean;
        constructor(predicate: any);
        item(a: any): boolean;
    }
}
declare namespace javaemul.internal.stream {
    class StreamRowCount extends javaemul.internal.stream.TerminalStreamRow {
        count: number;
        getCount(): number;
        item(a: any): boolean;
        constructor();
    }
}
declare namespace javaemul.internal.stream {
    /**
     * @author Władysław Kargul
     * @param {*} previous
     * @class
     * @extends javaemul.internal.stream.TerminalStreamRow
     */
    class StreamRowEnd extends javaemul.internal.stream.TerminalStreamRow {
        previous: javaemul.internal.stream.StreamRow;
        constructor(previous: javaemul.internal.stream.StreamRow);
        chain(next: javaemul.internal.stream.StreamRow): void;
        item(a: any): boolean;
    }
}
declare namespace javaemul.internal.stream {
    class StreamRowOnceFilter extends javaemul.internal.stream.TerminalStreamRow {
        predicate: any;
        predicateValue: boolean;
        firstMatch: java.util.Optional<any>;
        getFirstMatch(): java.util.Optional<any>;
        getPredicateValue(): boolean;
        constructor(predicate: any);
        item(a: any): boolean;
    }
}
declare namespace javaemul.internal.stream {
    class StreamRowReduce extends javaemul.internal.stream.TerminalStreamRow {
        result: java.util.Optional<any>;
        operator: any;
        constructor(result: java.util.Optional<any>, operator: any);
        getResult(): java.util.Optional<any>;
        item(a: any): boolean;
    }
}
declare namespace javaemul.internal.stream {
    class StreamRowCollector extends javaemul.internal.stream.TransientStreamRow {
        collection: java.util.Collection<any>;
        constructor(collection: java.util.Collection<any>);
        item(a: any): boolean;
        end(): void;
    }
}
declare namespace javaemul.internal.stream {
    class StreamRowFilter extends javaemul.internal.stream.TransientStreamRow {
        predicate: any;
        constructor(predicate: any);
        item(a: any): boolean;
        end(): void;
    }
}
declare namespace javaemul.internal.stream {
    class StreamRowFilterFlop extends javaemul.internal.stream.TransientStreamRow {
        predicate: any;
        constructor(predicate: any);
        item(a: any): boolean;
        end(): void;
    }
}
declare namespace javaemul.internal.stream {
    class StreamRowFlatMap extends javaemul.internal.stream.TransientStreamRow {
        flatMap: (p1: any) => java.util.stream.Stream<any>;
        constructor(flatMap: any);
        item(a: any): boolean;
        end(): void;
    }
}
declare namespace javaemul.internal.stream {
    class StreamRowMap extends javaemul.internal.stream.TransientStreamRow {
        map: any;
        constructor(map: any);
        item(a: any): boolean;
        end(): void;
    }
}
declare namespace java.io {
    /**
     * @skip
     * @param {java.io.OutputStream} out
     * @class
     * @extends java.io.FilterOutputStream
     */
    class PrintStream extends java.io.FilterOutputStream {
        constructor(out: java.io.OutputStream);
        print$java_lang_String(s: string): void;
        print(s?: any): any;
        println$java_lang_String(s: string): void;
        println(s?: any): any;
        print$boolean(x: boolean): void;
        print$char(x: string): void;
        print$char_A(x: string[]): void;
        print$double(x: number): void;
        print$float(x: number): void;
        print$int(x: number): void;
        print$long(x: number): void;
        print$java_lang_Object(x: any): void;
        println$(): void;
        println$boolean(x: boolean): void;
        println$char(x: string): void;
        println$char_A(x: string[]): void;
        println$double(x: number): void;
        println$float(x: number): void;
        println$int(x: number): void;
        println$long(x: number): void;
        println$java_lang_Object(x: any): void;
    }
}
declare namespace java.io {
    /**
     * A character encoding is not supported - <a
     * href="http://java.sun.com/javase/6/docs/api/java/io/UnsupportedEncodingException.html">[Sun's
     * docs]</a>.
     * @param {string} msg
     * @class
     * @extends java.io.IOException
     */
    class UnsupportedEncodingException extends java.io.IOException {
        constructor(msg?: any);
    }
}
declare namespace java.io {
    /**
     * See <a
     * href="https://docs.oracle.com/javase/8/docs/api/java/io/UncheckedIOException.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @param {java.io.IOException} cause
     * @class
     * @extends java.lang.RuntimeException
     */
    class UncheckedIOException extends java.lang.RuntimeException {
        constructor(message?: any, cause?: any);
        /**
         *
         * @return {java.io.IOException}
         */
        getCause(): java.io.IOException;
    }
}
declare namespace java.lang.annotation {
    /**
     * Indicates an attempt to access an element of an annotation that has changed
     * since it was compiled or serialized <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/annotation/AnnotationTypeMismatchException.html">[Sun
     * docs]</a>.
     * @class
     * @extends java.lang.RuntimeException
     */
    class AnnotationTypeMismatchException extends java.lang.RuntimeException {
        constructor();
    }
}
declare namespace java.lang.annotation {
    /**
     * Indicates an attempt to access an element of an annotation that was added
     * since it was compiled or serialized <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/annotation/IncompleteAnnotationException.html">[Sun
     * docs]</a>.
     * @param {java.lang.Class} annotationType
     * @param {string} elementName
     * @class
     * @extends java.lang.RuntimeException
     */
    class IncompleteAnnotationException extends java.lang.RuntimeException {
        __annotationType: any;
        __elementName: string;
        constructor(annotationType: any, elementName: string);
        annotationType(): any;
        elementName(): string;
    }
}
declare namespace java.lang {
    /**
     * NOTE: in GWT this is only thrown for division by zero on longs and
     * BigInteger/BigDecimal.
     * <p>
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/ArithmeticException.html">the
     * official Java API doc</a> for details.
     * @param {string} explanation
     * @class
     * @extends java.lang.RuntimeException
     */
    class ArithmeticException extends java.lang.RuntimeException {
        constructor(explanation?: any);
    }
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/ArrayStoreException.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @class
     * @extends java.lang.RuntimeException
     */
    class ArrayStoreException extends java.lang.RuntimeException {
        constructor(message?: any);
    }
}
declare namespace java.lang {
    /**
     * Indicates failure to cast one type into another.
     * @param {string} message
     * @class
     * @extends java.lang.RuntimeException
     */
    class ClassCastException extends java.lang.RuntimeException {
        constructor(message?: any);
    }
}
declare namespace java.lang {
    class IllegalAccessException extends java.lang.RuntimeException {
        constructor(message?: any, cause?: any);
    }
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/IllegalArgumentException.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @param {java.lang.Throwable} cause
     * @class
     * @extends java.lang.RuntimeException
     */
    class IllegalArgumentException extends java.lang.RuntimeException {
        constructor(message?: any, cause?: any);
    }
}
declare namespace java.lang {
    /**
     * Indicates that an objet was in an invalid state during an attempted
     * operation.
     * @param {string} message
     * @param {java.lang.Throwable} cause
     * @class
     * @extends java.lang.RuntimeException
     */
    class IllegalStateException extends java.lang.RuntimeException {
        constructor(message?: any, cause?: any);
    }
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/IndexOutOfBoundsException.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @class
     * @extends java.lang.RuntimeException
     */
    class IndexOutOfBoundsException extends java.lang.RuntimeException {
        constructor(message?: any);
    }
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/NegativeArraySizeException.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @class
     * @extends java.lang.RuntimeException
     */
    class NegativeArraySizeException extends java.lang.RuntimeException {
        constructor(message?: any);
    }
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/NullPointerException.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @class
     * @extends java.lang.RuntimeException
     */
    class NullPointerException extends java.lang.RuntimeException {
        constructor(message?: any);
        createError(msg: string): any;
    }
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/UnsupportedOperationException.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @param {java.lang.Throwable} cause
     * @class
     * @extends java.lang.RuntimeException
     */
    class UnsupportedOperationException extends java.lang.RuntimeException {
        constructor(message?: any, cause?: any);
    }
}
declare namespace java.nio {
    class BufferOverflowException extends java.lang.RuntimeException {
        constructor();
    }
}
declare namespace java.nio {
    class BufferUnderflowException extends java.lang.RuntimeException {
        constructor();
    }
}
declare namespace java.util {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/ConcurrentModificationException.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @class
     * @extends java.lang.RuntimeException
     */
    class ConcurrentModificationException extends java.lang.RuntimeException {
        constructor(message?: any);
    }
}
declare namespace java.util {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/EmptyStackException.html">the
     * official Java API doc</a> for details.
     * @class
     * @extends java.lang.RuntimeException
     */
    class EmptyStackException extends java.lang.RuntimeException {
        constructor();
    }
}
declare namespace java.util {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/MissingResourceException.html">the
     * official Java API doc</a> for details.
     * @param {string} s
     * @param {string} className
     * @param {string} key
     * @class
     * @extends java.lang.RuntimeException
     */
    class MissingResourceException extends java.lang.RuntimeException {
        className: string;
        key: string;
        constructor(s: string, className: string, key: string);
        getClassName(): string;
        getKey(): string;
    }
}
declare namespace java.util {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/NoSuchElementException.html">the
     * official Java API doc</a> for details.
     * @param {string} s
     * @class
     * @extends java.lang.RuntimeException
     */
    class NoSuchElementException extends java.lang.RuntimeException {
        constructor(s?: any);
    }
}
declare namespace javaemul.internal.stream {
    class StopException extends java.lang.RuntimeException {
        constructor();
    }
}
declare namespace java.security {
    /**
     * A generic security exception type - <a
     * href="http://java.sun.com/j2se/1.4.2/docs/api/java/security/DigestException.html">[Sun's
     * docs]</a>.
     * @param {string} msg
     * @class
     * @extends java.security.GeneralSecurityException
     */
    class DigestException extends java.security.GeneralSecurityException {
        constructor(msg?: any);
    }
}
declare namespace java.security {
    /**
     * A generic security exception type - <a
     * href="http://java.sun.com/j2se/1.4.2/docs/api/java/security/NoSuchAlgorithmException.html">[Sun's
     * docs]</a>.
     * @param {string} msg
     * @class
     * @extends java.security.GeneralSecurityException
     */
    class NoSuchAlgorithmException extends java.security.GeneralSecurityException {
        constructor(msg?: any);
    }
}
declare namespace java.nio.charset {
    /**
     * Constant definitions for the standard Charsets.
     * @class
     */
    class StandardCharsets {
        static ISO_8859_1: java.nio.charset.Charset;
        static ISO_8859_1_$LI$(): java.nio.charset.Charset;
        static UTF_8: java.nio.charset.Charset;
        static UTF_8_$LI$(): java.nio.charset.Charset;
        constructor();
    }
}
declare namespace java.util {
    /**
     * Skeletal implementation of the List interface. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/AbstractSequentialList.html">[Sun
     * docs]</a>
     *
     * @param <E> element type.
     * @extends java.util.AbstractList
     * @class
     */
    abstract class AbstractSequentialList<E> extends java.util.AbstractList<E> {
        constructor();
        add$int$java_lang_Object(index: number, element: E): void;
        /**
         *
         * @param {number} index
         * @param {*} element
         */
        add(index?: any, element?: any): any;
        addAll$int$java_util_Collection(index: number, c: java.util.Collection<any>): boolean;
        /**
         *
         * @param {number} index
         * @param {*} c
         * @return {boolean}
         */
        addAll(index?: any, c?: any): any;
        /**
         *
         * @param {number} index
         * @return {*}
         */
        get(index: number): E;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<E>;
        listIterator$int(index: number): java.util.ListIterator<E>;
        /**
         *
         * @param {number} index
         * @return {*}
         */
        listIterator(index?: any): any;
        remove$int(index: number): E;
        /**
         *
         * @param {number} index
         * @return {*}
         */
        remove(index?: any): any;
        /**
         *
         * @param {number} index
         * @param {*} element
         * @return {*}
         */
        set(index: number, element: E): E;
        /**
         *
         * @return {number}
         */
        abstract size(): number;
    }
}
declare namespace java.util {
    /**
     * Resizeable array implementation of the List interface. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/ArrayList.html">[Sun
     * docs]</a>
     *
     * <p>
     * This implementation differs from JDK 1.5 <code>ArrayList</code> in terms of
     * capacity management. There is no speed advantage to pre-allocating array
     * sizes in JavaScript, so this implementation does not include any of the
     * capacity and "growth increment" concepts in the standard ArrayList class.
     * Although <code>ArrayList(int)</code> accepts a value for the initial
     * capacity of the array, this constructor simply delegates to
     * <code>ArrayList()</code>. It is only present for compatibility with JDK
     * 1.5's API.
     * </p>
     *
     * @param <E> the element type.
     * @param {*} c
     * @class
     * @extends java.util.AbstractList
     */
    class ArrayList<E> extends java.util.AbstractList<E> implements java.util.List<E>, java.lang.Cloneable, java.util.RandomAccess, java.io.Serializable {
        stream(): java.util.stream.Stream<any>;
        forEach(action: (p1: any) => void): void;
        removeIf(filter: (p1: any) => boolean): boolean;
        /**
         * This field holds a JavaScript array.
         */
        array: E[];
        /**
         * Ensures that RPC will consider type parameter E to be exposed. It will be
         * pruned by dead code elimination.
         */
        exposeElement: E;
        constructor(c?: any);
        add$java_lang_Object(o: E): boolean;
        add$int$java_lang_Object(index: number, o: E): void;
        /**
         *
         * @param {number} index
         * @param {*} o
         */
        add(index?: any, o?: any): any;
        addAll$java_util_Collection(c: java.util.Collection<any>): boolean;
        addAll$int$java_util_Collection(index: number, c: java.util.Collection<any>): boolean;
        /**
         *
         * @param {number} index
         * @param {*} c
         * @return {boolean}
         */
        addAll(index?: any, c?: any): any;
        /**
         *
         */
        clear(): void;
        clone(): any;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        contains(o: any): boolean;
        ensureCapacity(ignored: number): void;
        /**
         *
         * @param {number} index
         * @return {*}
         */
        get(index: number): E;
        indexOf$java_lang_Object(o: any): number;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<E>;
        /**
         *
         * @return {boolean}
         */
        isEmpty(): boolean;
        lastIndexOf$java_lang_Object(o: any): number;
        remove$int(index: number): E;
        /**
         *
         * @param {number} index
         * @return {*}
         */
        remove(index?: any): any;
        remove$java_lang_Object(o: any): boolean;
        /**
         *
         * @param {number} index
         * @param {*} o
         * @return {*}
         */
        set(index: number, o: E): E;
        /**
         *
         * @return {number}
         */
        size(): number;
        toArray$(): any[];
        toArray$java_lang_Object_A<T>(out: T[]): T[];
        /**
         *
         * @param {Array} out
         * @return {Array}
         */
        toArray<T>(out?: any): any;
        trimToSize(): void;
        /**
         *
         * @param {number} fromIndex
         * @param {number} endIndex
         */
        removeRange(fromIndex: number, endIndex: number): void;
        indexOf$java_lang_Object$int(o: any, index: number): number;
        /**
         * Used by Vector.
         * @param {*} o
         * @param {number} index
         * @return {number}
         */
        indexOf(o?: any, index?: any): any;
        lastIndexOf$java_lang_Object$int(o: any, index: number): number;
        /**
         * Used by Vector.
         * @param {*} o
         * @param {number} index
         * @return {number}
         */
        lastIndexOf(o?: any, index?: any): any;
        setSize(newSize: number): void;
    }
    namespace ArrayList {
        class ArrayList$0 implements java.util.Iterator<any> {
            __parent: any;
            forEachRemaining(consumer: (p1: any) => void): void;
            i: number;
            last: number;
            /**
             *
             * @return {boolean}
             */
            hasNext(): boolean;
            /**
             *
             * @return {*}
             */
            next(): any;
            /**
             *
             */
            remove(): void;
            constructor(__parent: any);
        }
    }
}
declare namespace java.util {
    /**
     * Utility methods related to native arrays. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/Arrays.html">[Sun
     * docs]</a>
     * @class
     */
    class Arrays {
        static asList<T>(...array: T[]): java.util.List<T>;
        static stream<T>(...array: T[]): java.util.stream.Stream<T>;
        static binarySearch$byte_A$byte(sortedArray: number[], key: number): number;
        static binarySearch$char_A$char(a: string[], key: string): number;
        static binarySearch$double_A$double(sortedArray: number[], key: number): number;
        static binarySearch$float_A$float(sortedArray: number[], key: number): number;
        static binarySearch$int_A$int(sortedArray: number[], key: number): number;
        static binarySearch$long_A$long(sortedArray: number[], key: number): number;
        static binarySearch$java_lang_Object_A$java_lang_Object(sortedArray: any[], key: any): number;
        static binarySearch$short_A$short(sortedArray: number[], key: number): number;
        static binarySearch$java_lang_Object_A$java_lang_Object$java_util_Comparator<T>(sortedArray: T[], key: T, comparator: java.util.Comparator<any>): number;
        /**
         * Perform a binary search on a sorted object array, using a user-specified
         * comparison function.
         *
         * @param {Array} sortedArray object array to search
         * @param {*} key value to search for
         * @param {*} comparator comparision function, <code>null</code> indicates
         * <i>natural ordering</i> should be used.
         * @return {number} the index of an element with a matching value, or a negative number
         * which is the index of the next larger value (or just past the end
         * of the array if the searched value is larger than all elements in
         * the array) minus 1 (to ensure error returns are negative)
         * @throws ClassCastException if <code>key</code> and
         * <code>sortedArray</code>'s elements cannot be compared by
         * <code>comparator</code>.
         */
        static binarySearch<T>(sortedArray?: any, key?: any, comparator?: any): any;
        static copyOf$boolean_A$int(original: boolean[], newLength: number): boolean[];
        static copyOf<T = any>(original?: any, newLength?: any): any;
        static copyOf$byte_A$int(original: number[], newLength: number): number[];
        static copyOf$char_A$int(original: string[], newLength: number): string[];
        static copyOf$double_A$int(original: number[], newLength: number): number[];
        static copyOf$float_A$int(original: number[], newLength: number): number[];
        static copyOf$int_A$int(original: number[], newLength: number): number[];
        static copyOf$long_A$int(original: number[], newLength: number): number[];
        static copyOf$short_A$int(original: number[], newLength: number): number[];
        static copyOf$java_lang_Object_A$int<T>(original: T[], newLength: number): T[];
        static copyOfRange$boolean_A$int$int(original: boolean[], from: number, to: number): boolean[];
        static copyOfRange(original?: any, from?: any, to?: any): any;
        static copyOfRange$byte_A$int$int(original: number[], from: number, to: number): number[];
        static copyOfRange$char_A$int$int(original: string[], from: number, to: number): string[];
        static copyOfRange$double_A$int$int(original: number[], from: number, to: number): number[];
        static copyOfRange$float_A$int$int(original: number[], from: number, to: number): number[];
        static copyOfRange$int_A$int$int(original: number[], from: number, to: number): number[];
        static copyOfRange$long_A$int$int(original: number[], from: number, to: number): number[];
        static copyOfRange$short_A$int$int(original: number[], from: number, to: number): number[];
        static copyOfRange$java_lang_Object_A$int$int<T>(original: T[], from: number, to: number): T[];
        static deepEquals(a1: any[], a2: any[]): boolean;
        static deepHashCode(a: any[]): number;
        static deepToString$java_lang_Object_A(a: any[]): string;
        static equals$boolean_A$boolean_A(array1: boolean[], array2: boolean[]): boolean;
        static equals(array1?: any, array2?: any): any;
        static equals$byte_A$byte_A(array1: number[], array2: number[]): boolean;
        static equals$char_A$char_A(array1: string[], array2: string[]): boolean;
        static equals$double_A$double_A(array1: number[], array2: number[]): boolean;
        static equals$float_A$float_A(array1: number[], array2: number[]): boolean;
        static equals$int_A$int_A(array1: number[], array2: number[]): boolean;
        static equals$long_A$long_A(array1: number[], array2: number[]): boolean;
        static equals$java_lang_Object_A$java_lang_Object_A(array1: any[], array2: any[]): boolean;
        static equals$short_A$short_A(array1: number[], array2: number[]): boolean;
        static fill$boolean_A$boolean(a: boolean[], val: boolean): void;
        static fill$boolean_A$int$int$boolean(a: boolean[], fromIndex: number, toIndex: number, val: boolean): void;
        static fill(a?: any, fromIndex?: any, toIndex?: any, val?: any): any;
        static fill$byte_A$byte(a: number[], val: number): void;
        static fill$byte_A$int$int$byte(a: number[], fromIndex: number, toIndex: number, val: number): void;
        static fill$char_A$char(a: string[], val: string): void;
        static fill$char_A$int$int$char(a: string[], fromIndex: number, toIndex: number, val: string): void;
        static fill$double_A$double(a: number[], val: number): void;
        static fill$double_A$int$int$double(a: number[], fromIndex: number, toIndex: number, val: number): void;
        static fill$float_A$float(a: number[], val: number): void;
        static fill$float_A$int$int$float(a: number[], fromIndex: number, toIndex: number, val: number): void;
        static fill$int_A$int(a: number[], val: number): void;
        static fill$int_A$int$int$int(a: number[], fromIndex: number, toIndex: number, val: number): void;
        static fill$long_A$int$int$long(a: number[], fromIndex: number, toIndex: number, val: number): void;
        static fill$long_A$long(a: number[], val: number): void;
        static fill$java_lang_Object_A$int$int$java_lang_Object(a: any[], fromIndex: number, toIndex: number, val: any): void;
        static fill$java_lang_Object_A$java_lang_Object(a: any[], val: any): void;
        static fill$short_A$int$int$short(a: number[], fromIndex: number, toIndex: number, val: number): void;
        static fill$short_A$short(a: number[], val: number): void;
        static hashCode$boolean_A(a: boolean[]): number;
        static hashCode(a?: any): any;
        static hashCode$byte_A(a: number[]): number;
        static hashCode$char_A(a: string[]): number;
        static hashCode$double_A(a: number[]): number;
        static hashCode$float_A(a: number[]): number;
        static hashCode$int_A(a: number[]): number;
        static hashCode$long_A(a: number[]): number;
        static hashCode$java_lang_Object_A(a: any[]): number;
        static hashCode$short_A(a: number[]): number;
        static sort$byte_A(array: number[]): void;
        static sort$byte_A$int$int(array: number[], fromIndex: number, toIndex: number): void;
        static sort$char_A(array: string[]): void;
        static sort$char_A$int$int(array: string[], fromIndex: number, toIndex: number): void;
        static sort$double_A(array: number[]): void;
        static sort$double_A$int$int(array: number[], fromIndex: number, toIndex: number): void;
        static sort$float_A(array: number[]): void;
        static sort$float_A$int$int(array: number[], fromIndex: number, toIndex: number): void;
        static sort$int_A(array: number[]): void;
        static sort$int_A$int$int(array: number[], fromIndex: number, toIndex: number): void;
        static sort$long_A(array: number[]): void;
        static sort$long_A$int$int(array: number[], fromIndex: number, toIndex: number): void;
        static sort$java_lang_Object_A(array: any[]): void;
        static sort$java_lang_Object_A$int$int(x: any[], fromIndex: number, toIndex: number): void;
        static sort$short_A(array: number[]): void;
        static sort$short_A$int$int(array: number[], fromIndex: number, toIndex: number): void;
        static sort$java_lang_Object_A$java_util_Comparator<T>(x: T[], c: java.util.Comparator<any>): void;
        static sort$java_lang_Object_A$int$int$java_util_Comparator<T>(x: T[], fromIndex: number, toIndex: number, c: java.util.Comparator<any>): void;
        static sort<T>(x?: any, fromIndex?: any, toIndex?: any, c?: any): any;
        static toString$boolean_A(a: boolean[]): string;
        static toString(a?: any): any;
        static toString$byte_A(a: number[]): string;
        static toString$char_A(a: string[]): string;
        static toString$double_A(a: number[]): string;
        static toString$float_A(a: number[]): string;
        static toString$int_A(a: number[]): string;
        static toString$long_A(a: number[]): string;
        static toString$java_lang_Object_A(x: any[]): string;
        static toString$short_A(a: number[]): string;
        static deepToString$java_lang_Object_A$java_util_Set(a: any[], arraysIveSeen: java.util.Set<any[]>): string;
        /**
         * Recursive helper function for {@link Arrays#deepToString(Object[])}.
         * @param {Array} a
         * @param {*} arraysIveSeen
         * @return {string}
         * @private
         */
        static deepToString(a?: any, arraysIveSeen?: any): any;
        static getCopyLength(array: any, from: number, to: number): number;
        /**
         * Sort a small subsection of an array by insertion sort.
         *
         * @param {Array} array array to sort
         * @param {number} low lower bound of range to sort
         * @param {number} high upper bound of range to sort
         * @param {*} comp comparator to use
         * @private
         */
        static insertionSort(array: any[], low: number, high: number, comp: java.util.Comparator<any>): void;
        /**
         * Merge the two sorted subarrays (srcLow,srcMid] and (srcMid,srcHigh] into
         * dest.
         *
         * @param {Array} src source array for merge
         * @param {number} srcLow lower bound of bottom sorted half
         * @param {number} srcMid upper bound of bottom sorted half & lower bound of top sorted
         * half
         * @param {number} srcHigh upper bound of top sorted half
         * @param {Array} dest destination array for merge
         * @param {number} destLow lower bound of destination
         * @param {number} destHigh upper bound of destination
         * @param {*} comp comparator to use
         * @private
         */
        static merge(src: any[], srcLow: number, srcMid: number, srcHigh: number, dest: any[], destLow: number, destHigh: number, comp: java.util.Comparator<any>): void;
        static mergeSort$java_lang_Object_A$int$int$java_util_Comparator(x: any[], fromIndex: number, toIndex: number, comp: java.util.Comparator<any>): void;
        static mergeSort$java_lang_Object_A$java_lang_Object_A$int$int$int$java_util_Comparator(temp: any[], array: any[], low: number, high: number, ofs: number, comp: java.util.Comparator<any>): void;
        /**
         * Recursive helper function for
         * {@link Arrays#mergeSort(Object[], int, int, Comparator)}.
         *
         * @param {Array} temp temporary space, as large as the range of elements being
         * sorted. On entry, temp should contain a copy of the sort range
         * from array.
         * @param {Array} array array to sort
         * @param {number} low lower bound of range to sort
         * @param {number} high upper bound of range to sort
         * @param {number} ofs offset to convert an array index into a temp index
         * @param {*} comp comparison function
         * @private
         */
        static mergeSort(temp?: any, array?: any, low?: any, high?: any, ofs?: any, comp?: any): any;
        static nativeLongSort$java_lang_Object$java_lang_Object(array: any, compareFunction: any): void;
        static nativeLongSort$java_lang_Object$int$int(array: any, fromIndex: number, toIndex: number): void;
        /**
         * Sort a subset of an array of number primitives.
         * @param {*} array
         * @param {number} fromIndex
         * @param {number} toIndex
         * @private
         */
        static nativeLongSort(array?: any, fromIndex?: any, toIndex?: any): any;
        static nativeNumberSort$java_lang_Object(array: any): void;
        static nativeNumberSort$java_lang_Object$int$int(array: any, fromIndex: number, toIndex: number): void;
        /**
         * Sort a subset of an array of number primitives.
         * @param {*} array
         * @param {number} fromIndex
         * @param {number} toIndex
         * @private
         */
        static nativeNumberSort(array?: any, fromIndex?: any, toIndex?: any): any;
    }
    namespace Arrays {
        class ArrayList<E> extends java.util.AbstractList<E> implements java.util.RandomAccess, java.io.Serializable {
            /**
             * The only reason this is non-final is so that E[] (and E) will be exposed
             * for serialization.
             */
            array: E[];
            constructor(array: E[]);
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            contains(o: any): boolean;
            /**
             *
             * @param {number} index
             * @return {*}
             */
            get(index: number): E;
            /**
             *
             * @param {number} index
             * @param {*} value
             * @return {*}
             */
            set(index: number, value: E): E;
            /**
             *
             * @return {number}
             */
            size(): number;
            toArray$(): any[];
            toArray$java_lang_Object_A<T>(out: T[]): T[];
            /**
             *
             * @param {Array} out
             * @return {Array}
             */
            toArray<T>(out?: any): any;
        }
    }
}
declare namespace java.util {
    /**
     * Capacity increment is ignored.
     * @param {number} initialCapacity
     * @param {number} ignoredCapacityIncrement
     * @class
     * @extends java.util.AbstractList
     */
    class Vector<E> extends java.util.AbstractList<E> implements java.util.List<E>, java.util.RandomAccess, java.lang.Cloneable, java.io.Serializable {
        stream(): java.util.stream.Stream<any>;
        forEach(action: (p1: any) => void): void;
        removeIf(filter: (p1: any) => boolean): boolean;
        arrayList: java.util.ArrayList<E>;
        /**
         * Ensures that RPC will consider type parameter E to be exposed. It will be
         * pruned by dead code elimination.
         */
        exposeElement: E;
        constructor(initialCapacity?: any, ignoredCapacityIncrement?: any);
        add$java_lang_Object(o: E): boolean;
        add$int$java_lang_Object(index: number, o: E): void;
        /**
         *
         * @param {number} index
         * @param {*} o
         */
        add(index?: any, o?: any): any;
        addAll$java_util_Collection(c: java.util.Collection<any>): boolean;
        addAll$int$java_util_Collection(index: number, c: java.util.Collection<any>): boolean;
        /**
         *
         * @param {number} index
         * @param {*} c
         * @return {boolean}
         */
        addAll(index?: any, c?: any): any;
        addElement(o: E): void;
        capacity(): number;
        /**
         *
         */
        clear(): void;
        clone(): any;
        /**
         *
         * @param {*} elem
         * @return {boolean}
         */
        contains(elem: any): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        containsAll(c: java.util.Collection<any>): boolean;
        copyInto(objs: any[]): void;
        elementAt(index: number): E;
        elements(): java.util.Enumeration<E>;
        ensureCapacity(capacity: number): void;
        firstElement(): E;
        /**
         *
         * @param {number} index
         * @return {*}
         */
        get(index: number): E;
        indexOf$java_lang_Object(elem: any): number;
        indexOf$java_lang_Object$int(elem: any, index: number): number;
        indexOf(elem?: any, index?: any): any;
        insertElementAt(o: E, index: number): void;
        /**
         *
         * @return {boolean}
         */
        isEmpty(): boolean;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<E>;
        lastElement(): E;
        lastIndexOf$java_lang_Object(o: any): number;
        lastIndexOf$java_lang_Object$int(o: any, index: number): number;
        lastIndexOf(o?: any, index?: any): any;
        remove$int(index: number): E;
        /**
         *
         * @param {number} index
         * @return {*}
         */
        remove(index?: any): any;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        removeAll(c: java.util.Collection<any>): boolean;
        removeAllElements(): void;
        removeElement(o: any): boolean;
        removeElementAt(index: number): void;
        /**
         *
         * @param {number} index
         * @param {*} elem
         * @return {*}
         */
        set(index: number, elem: E): E;
        setElementAt(o: E, index: number): void;
        setSize(size: number): void;
        /**
         *
         * @return {number}
         */
        size(): number;
        /**
         *
         * @param {number} fromIndex
         * @param {number} toIndex
         * @return {*}
         */
        subList(fromIndex: number, toIndex: number): java.util.List<E>;
        toArray$(): any[];
        toArray$java_lang_Object_A<T>(a: T[]): T[];
        /**
         *
         * @param {Array} a
         * @return {Array}
         */
        toArray<T>(a?: any): any;
        /**
         *
         * @return {string}
         */
        toString(): string;
        trimToSize(): void;
        /**
         *
         * @param {number} fromIndex
         * @param {number} endIndex
         */
        removeRange(fromIndex: number, endIndex: number): void;
        static checkArrayElementIndex(index: number, size: number): void;
        static checkArrayIndexOutOfBounds(expression: boolean, index: number): void;
    }
}
declare namespace java.util {
    /**
     * An unbounded priority queue based on a priority heap. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/PriorityQueue.html">[Sun
     * docs]</a>
     *
     * @param <E> element type.
     * @param {number} initialCapacity
     * @param {*} cmp
     * @class
     * @extends java.util.AbstractQueue
     */
    class PriorityQueue<E> extends java.util.AbstractQueue<E> {
        static INITIAL_CAPACITY: number;
        static getLeftChild(node: number): number;
        static getParent(node: number): number;
        static getRightChild(node: number): number;
        static isLeaf(node: number, size: number): boolean;
        cmp: java.util.Comparator<any>;
        /**
         * A heap held in an array. heap[0] is the root of the heap (the smallest
         * element), the subtrees of node i are 2*i+1 (left) and 2*i+2 (right). Node i
         * is a leaf node if 2*i>=n. Node i's parent, if i>0, is floor((i-1)/2).
         */
        heap: java.util.ArrayList<E>;
        constructor(initialCapacity?: any, cmp?: any);
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        addAll(c: java.util.Collection<any>): boolean;
        /**
         *
         */
        clear(): void;
        comparator(): java.util.Comparator<any>;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        contains(o: any): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        containsAll(c: java.util.Collection<any>): boolean;
        /**
         *
         * @return {boolean}
         */
        isEmpty(): boolean;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<E>;
        /**
         *
         * @param {*} e
         * @return {boolean}
         */
        offer(e: E): boolean;
        /**
         *
         * @return {*}
         */
        peek(): E;
        /**
         *
         * @return {*}
         */
        poll(): E;
        remove$java_lang_Object(o: any): boolean;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        remove(o?: any): any;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        removeAll(c: java.util.Collection<any>): boolean;
        /**
         *
         * @param {*} c
         * @return {boolean}
         */
        retainAll(c: java.util.Collection<any>): boolean;
        /**
         *
         * @return {number}
         */
        size(): number;
        toArray$(): any[];
        toArray$java_lang_Object_A<T>(a: T[]): T[];
        /**
         *
         * @param {Array} a
         * @return {Array}
         */
        toArray<T>(a?: any): any;
        /**
         *
         * @return {string}
         */
        toString(): string;
        /**
         * Make the subtree rooted at <code>node</code> a valid heap. O(n) time
         *
         * @param {number} node
         */
        makeHeap(node: number): void;
        /**
         * Merge two subheaps into a single heap. O(log n) time
         *
         * PRECONDITION: both children of <code>node</code> are heaps
         *
         * @param {number} node the parent of the two subtrees to merge
         */
        mergeHeaps(node: number): void;
        getSmallestChild(node: number, heapSize: number): number;
        isLeaf(node: number): boolean;
        removeAtIndex(index: number): void;
    }
}
declare namespace java.util {
    /**
     * Skeletal implementation of the Map interface.
     * <a href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/AbstractMap.html">
     * [Sun docs]</a>
     *
     * @param <K>
     * the key type.
     * @param <V>
     * the value type.
     * @class
     */
    abstract class AbstractMap<K, V> implements java.util.Map<K, V> {
        merge(key: any, value: any, map: (p1: any, p2: any) => any): any;
        computeIfAbsent(key: any, mappingFunction: (p1: any) => any): any;
        constructor();
        /**
         *
         */
        clear(): void;
        /**
         *
         * @param {*} key
         * @return {boolean}
         */
        containsKey(key: any): boolean;
        /**
         *
         * @param {*} value
         * @return {boolean}
         */
        containsValue(value: any): boolean;
        containsEntry(entry: Map.Entry<any, any>): boolean;
        /**
         *
         * @return {*}
         */
        abstract entrySet(): java.util.Set<Map.Entry<K, V>>;
        /**
         *
         * @param {*} obj
         * @return {boolean}
         */
        equals(obj: any): boolean;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        get(key: any): V;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @return {boolean}
         */
        isEmpty(): boolean;
        /**
         *
         * @return {*}
         */
        keySet(): java.util.Set<K>;
        /**
         *
         * @param {*} key
         * @param {*} value
         * @return {*}
         */
        put(key: K, value: V): V;
        /**
         *
         * @param {*} map
         */
        putAll(map: java.util.Map<any, any>): void;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        remove(key: any): V;
        /**
         *
         * @return {number}
         */
        size(): number;
        toString$(): string;
        toString$java_util_Map_Entry(entry: Map.Entry<K, V>): string;
        toString(entry?: any): any;
        toString$java_lang_Object(o: any): string;
        /**
         *
         * @return {*}
         */
        values(): java.util.Collection<V>;
        static getEntryKeyOrNull<K, V>(entry: Map.Entry<K, V>): K;
        static getEntryValueOrNull<K, V>(entry: Map.Entry<K, V>): V;
        implFindEntry(key: any, remove: boolean): Map.Entry<K, V>;
    }
    namespace AbstractMap {
        /**
         * Basic {@link Map.Entry} implementation used by {@link SimpleEntry} and
         * {@link SimpleImmutableEntry}.
         * @class
         */
        abstract class AbstractEntry<K, V> implements Map.Entry<K, V> {
            key: K;
            value: V;
            constructor(key: K, value: V);
            /**
             *
             * @return {*}
             */
            getKey(): K;
            /**
             *
             * @return {*}
             */
            getValue(): V;
            /**
             *
             * @param {*} value
             * @return {*}
             */
            setValue(value: V): V;
            /**
             *
             * @param {*} other
             * @return {boolean}
             */
            equals(other: any): boolean;
            /**
             * Calculate the hash code using Sun's specified algorithm.
             * @return {number}
             */
            hashCode(): number;
            /**
             *
             * @return {string}
             */
            toString(): string;
        }
        /**
         * A mutable {@link Map.Entry} shared by several {@link Map}
         * implementations.
         * @param {*} key
         * @param {*} value
         * @class
         * @extends java.util.AbstractMap.AbstractEntry
         */
        class SimpleEntry<K, V> extends AbstractMap.AbstractEntry<K, V> {
            constructor(key?: any, value?: any);
        }
        /**
         * An immutable {@link Map.Entry} shared by several {@link Map}
         * implementations.
         * @param {*} key
         * @param {*} value
         * @class
         * @extends java.util.AbstractMap.AbstractEntry
         */
        class SimpleImmutableEntry<K, V> extends AbstractMap.AbstractEntry<K, V> {
            constructor(key?: any, value?: any);
            /**
             *
             * @param {*} value
             * @return {*}
             */
            setValue(value: V): V;
        }
        class AbstractMap$0 extends java.util.AbstractSet<any> {
            __parent: any;
            /**
             *
             */
            clear(): void;
            /**
             *
             * @param {*} key
             * @return {boolean}
             */
            contains(key: any): boolean;
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<any>;
            /**
             *
             * @param {*} key
             * @return {boolean}
             */
            remove(key: any): boolean;
            /**
             *
             * @return {number}
             */
            size(): number;
            constructor(__parent: any);
        }
        namespace AbstractMap$0 {
            class AbstractMap$0$0 implements java.util.Iterator<any> {
                private outerIter;
                __parent: any;
                forEachRemaining(consumer: (p1: any) => void): void;
                /**
                 *
                 * @return {boolean}
                 */
                hasNext(): boolean;
                /**
                 *
                 * @return {*}
                 */
                next(): any;
                /**
                 *
                 */
                remove(): void;
                constructor(__parent: any, outerIter: any);
            }
        }
        class AbstractMap$1 extends java.util.AbstractCollection<any> {
            __parent: any;
            /**
             *
             */
            clear(): void;
            /**
             *
             * @param {*} value
             * @return {boolean}
             */
            contains(value: any): boolean;
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<any>;
            /**
             *
             * @return {number}
             */
            size(): number;
            constructor(__parent: any);
        }
        namespace AbstractMap$1 {
            class AbstractMap$1$0 implements java.util.Iterator<any> {
                private outerIter;
                __parent: any;
                forEachRemaining(consumer: (p1: any) => void): void;
                /**
                 *
                 * @return {boolean}
                 */
                hasNext(): boolean;
                /**
                 *
                 * @return {*}
                 */
                next(): any;
                /**
                 *
                 */
                remove(): void;
                constructor(__parent: any, outerIter: any);
            }
        }
    }
}
declare namespace java.util {
    /**
     * A {@link java.util.Set} of {@link Enum}s. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/EnumSet.html">[Sun
     * docs]</a>
     *
     * @param <E> enumeration type
     * @extends java.util.AbstractSet
     * @class
     */
    abstract class EnumSet<E extends java.lang.Enum<E>> extends java.util.AbstractSet<E> {
        static allOf<E extends java.lang.Enum<E>>(elementType: any): EnumSet<E>;
        static complementOf<E extends java.lang.Enum<E>>(other: EnumSet<E>): EnumSet<E>;
        static copyOf$java_util_Collection<E extends java.lang.Enum<E>>(c: java.util.Collection<E>): EnumSet<E>;
        static copyOf$java_util_EnumSet<E extends java.lang.Enum<E>>(s: EnumSet<E>): EnumSet<E>;
        static copyOf<E extends java.lang.Enum<E>>(s?: any): any;
        static noneOf<E extends java.lang.Enum<E>>(elementType: any): EnumSet<E>;
        static of$java_lang_Enum<E extends java.lang.Enum<E>>(first: E): EnumSet<E>;
        static of$java_lang_Enum$java_lang_Enum_A<E extends java.lang.Enum<E>>(first: E, ...rest: E[]): EnumSet<E>;
        static of<E extends java.lang.Enum<E>>(first?: any, ...rest: any[]): any;
        static range<E extends java.lang.Enum<E>>(from: E, to: E): EnumSet<E>;
        constructor();
        abstract clone(): EnumSet<E>;
        abstract capacity(): number;
    }
    namespace EnumSet {
        /**
         * Constructs a set taking ownership of the specified set. The size must
         * accurately reflect the number of non-null items in set.
         * @param {Array} all
         * @param {Array} set
         * @param {number} size
         * @class
         * @extends java.util.EnumSet
         */
        class EnumSetImpl<E extends java.lang.Enum<E>> extends java.util.EnumSet<E> {
            /**
             * All enums; reference to the class's copy; must not be modified.
             */
            all: E[];
            /**
             * Live enums in the set.
             */
            set: E[];
            /**
             * Count of enums in the set.
             */
            __size: number;
            constructor(all: E[], set: E[], size: number);
            add$java_lang_Enum(e: E): boolean;
            /**
             *
             * @param {java.lang.Enum} e
             * @return {boolean}
             */
            add(e?: any): any;
            /**
             *
             * @return {java.util.EnumSet}
             */
            clone(): java.util.EnumSet<E>;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            contains(o: any): boolean;
            containsEnum(e: java.lang.Enum<any>): boolean;
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<E>;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            remove(o: any): boolean;
            removeEnum(e: java.lang.Enum<any>): boolean;
            /**
             *
             * @return {number}
             */
            size(): number;
            /**
             *
             * @return {number}
             */
            capacity(): number;
        }
        namespace EnumSetImpl {
            class IteratorImpl implements java.util.Iterator<any> {
                __parent: any;
                forEachRemaining(consumer: (p1: any) => void): void;
                i: number;
                last: number;
                constructor(__parent: any);
                /**
                 *
                 * @return {boolean}
                 */
                hasNext(): boolean;
                /**
                 *
                 * @return {java.lang.Enum}
                 */
                next(): any;
                /**
                 *
                 */
                remove(): void;
                findNext(): void;
            }
        }
    }
}
declare namespace java.util {
    /**
     * Implements a set in terms of a hash table. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/HashSet.html">[Sun
     * docs]</a>
     *
     * @param <E> element type.
     * @param {number} initialCapacity
     * @param {number} loadFactor
     * @class
     * @extends java.util.AbstractSet
     */
    class HashSet<E> extends java.util.AbstractSet<E> implements java.util.Set<E>, java.lang.Cloneable, java.io.Serializable {
        stream(): java.util.stream.Stream<any>;
        forEach(action: (p1: any) => void): void;
        removeIf(filter: (p1: any) => boolean): boolean;
        map: java.util.HashMap<E, any>;
        /**
         * Ensures that RPC will consider type parameter E to be exposed. It will be
         * pruned by dead code elimination.
         */
        exposeElement: E;
        constructor(initialCapacity?: any, loadFactor?: any);
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        add(o: E): boolean;
        /**
         *
         */
        clear(): void;
        clone(): any;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        contains(o: any): boolean;
        /**
         *
         * @return {boolean}
         */
        isEmpty(): boolean;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<E>;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        remove(o: any): boolean;
        /**
         *
         * @return {number}
         */
        size(): number;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
}
declare namespace java.util {
    /**
     * Implements a set using a TreeMap. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/TreeSet.html">[Sun
     * docs]</a>
     *
     * @param <E> element type.
     * @param {*} c
     * @class
     * @extends java.util.AbstractSet
     */
    class TreeSet<E> extends java.util.AbstractSet<E> implements java.util.NavigableSet<E>, java.io.Serializable {
        stream(): java.util.stream.Stream<any>;
        forEach(action: (p1: any) => void): void;
        removeIf(filter: (p1: any) => boolean): boolean;
        /**
         * TreeSet is stored as a TreeMap of the requested type to a constant Boolean.
         */
        map: java.util.NavigableMap<E, boolean>;
        constructor(c?: any);
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        add(o: E): boolean;
        /**
         *
         * @param {*} e
         * @return {*}
         */
        ceiling(e: E): E;
        /**
         *
         */
        clear(): void;
        /**
         *
         * @return {*}
         */
        comparator(): java.util.Comparator<any>;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        contains(o: any): boolean;
        /**
         *
         * @return {*}
         */
        descendingIterator(): java.util.Iterator<E>;
        /**
         *
         * @return {*}
         */
        descendingSet(): java.util.NavigableSet<E>;
        /**
         *
         * @return {*}
         */
        first(): E;
        /**
         *
         * @param {*} e
         * @return {*}
         */
        floor(e: E): E;
        headSet$java_lang_Object(toElement: E): java.util.SortedSet<E>;
        headSet$java_lang_Object$boolean(toElement: E, inclusive: boolean): java.util.NavigableSet<E>;
        /**
         *
         * @param {*} toElement
         * @param {boolean} inclusive
         * @return {*}
         */
        headSet(toElement?: any, inclusive?: any): any;
        /**
         *
         * @param {*} e
         * @return {*}
         */
        higher(e: E): E;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<E>;
        /**
         *
         * @return {*}
         */
        last(): E;
        /**
         *
         * @param {*} e
         * @return {*}
         */
        lower(e: E): E;
        /**
         *
         * @return {*}
         */
        pollFirst(): E;
        /**
         *
         * @return {*}
         */
        pollLast(): E;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        remove(o: any): boolean;
        /**
         *
         * @return {number}
         */
        size(): number;
        subSet$java_lang_Object$boolean$java_lang_Object$boolean(fromElement: E, fromInclusive: boolean, toElement: E, toInclusive: boolean): java.util.NavigableSet<E>;
        /**
         *
         * @param {*} fromElement
         * @param {boolean} fromInclusive
         * @param {*} toElement
         * @param {boolean} toInclusive
         * @return {*}
         */
        subSet(fromElement?: any, fromInclusive?: any, toElement?: any, toInclusive?: any): any;
        subSet$java_lang_Object$java_lang_Object(fromElement: E, toElement: E): java.util.SortedSet<E>;
        tailSet$java_lang_Object(fromElement: E): java.util.SortedSet<E>;
        tailSet$java_lang_Object$boolean(fromElement: E, inclusive: boolean): java.util.NavigableSet<E>;
        /**
         *
         * @param {*} fromElement
         * @param {boolean} inclusive
         * @return {*}
         */
        tailSet(fromElement?: any, inclusive?: any): any;
    }
}
declare namespace javaemul.internal.stream {
    class StreamRowSortingCollector extends javaemul.internal.stream.StreamRowCollector {
        comparator: java.util.Comparator<any>;
        constructor(collection: java.util.List<any>, comparator: java.util.Comparator<any>);
        /**
         *
         */
        end(): void;
    }
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/NumberFormatException.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @class
     * @extends java.lang.IllegalArgumentException
     */
    class NumberFormatException extends java.lang.IllegalArgumentException {
        static forInputString(s: string): java.lang.NumberFormatException;
        static forNullInputString(): java.lang.NumberFormatException;
        static forRadix(radix: number): java.lang.NumberFormatException;
        constructor(message?: any);
    }
}
declare namespace java.nio.charset {
    /**
     * GWT emulation of {@link IllegalCharsetNameException}.
     * @param {string} charsetName
     * @class
     * @extends java.lang.IllegalArgumentException
     */
    class IllegalCharsetNameException extends java.lang.IllegalArgumentException {
        charsetName: string;
        constructor(charsetName: string);
        getCharsetName(): string;
    }
}
declare namespace java.nio.charset {
    /**
     * GWT emulation of {@link UnsupportedCharsetException}.
     * @param {string} charsetName
     * @class
     * @extends java.lang.IllegalArgumentException
     */
    class UnsupportedCharsetException extends java.lang.IllegalArgumentException {
        charsetName: string;
        constructor(charsetName: string);
        getCharsetName(): string;
    }
}
declare namespace java.util.regex {
    class PatternSyntaxException extends java.lang.IllegalArgumentException {
        pattern: string;
        constructor(desc?: any, pattern?: any, index?: any);
        static createSyntaxError(desc: string, index: number): Error;
        getIndex(): number;
        getDescription(): string;
        getPattern(): string;
        /**
         *
         * @return {string}
         */
        getMessage(): string;
    }
}
declare namespace java.nio {
    class InvalidMarkException extends java.lang.IllegalStateException {
        constructor();
    }
}
declare namespace java.lang {
    /**
     * NOTE: in GWT this will never be thrown for normal array accesses, only for
     * explicit throws.
     *
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/ArrayIndexOutOfBoundsException.html">the
     * official Java API doc</a> for details.
     * @param {number} index
     * @class
     * @extends java.lang.IndexOutOfBoundsException
     */
    class ArrayIndexOutOfBoundsException extends java.lang.IndexOutOfBoundsException {
        constructor(msg?: any);
    }
}
declare namespace java.lang {
    /**
     * See <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/StringIndexOfBoundsException.html">the
     * official Java API doc</a> for details.
     * @param {string} message
     * @class
     * @extends java.lang.IndexOutOfBoundsException
     */
    class StringIndexOutOfBoundsException extends java.lang.IndexOutOfBoundsException {
        constructor(message?: any);
    }
}
declare namespace java.nio {
    class ReadOnlyBufferException extends java.lang.UnsupportedOperationException {
        constructor();
    }
}
declare namespace java.util {
    class InputMismatchException extends java.util.NoSuchElementException {
        constructor(message?: any);
    }
}
declare namespace java.util {
    /**
     * Linked list implementation.
     * <a href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/LinkedList.html">
     * [Sun docs]</a>
     *
     * @param <E>
     * element type.
     * @param {*} c
     * @class
     * @extends java.util.AbstractSequentialList
     */
    class LinkedList<E> extends java.util.AbstractSequentialList<E> implements java.lang.Cloneable, java.util.List<E>, java.util.Deque<E>, java.io.Serializable {
        stream(): java.util.stream.Stream<any>;
        forEach(action: (p1: any) => void): void;
        removeIf(filter: (p1: any) => boolean): boolean;
        /**
         * Ensures that RPC will consider type parameter E to be exposed. It will be
         * pruned by dead code elimination.
         */
        exposeElement: E;
        /**
         * Header node - header.next is the first element of the list.
         */
        header: LinkedList.Node<E>;
        /**
         * Tail node - tail.prev is the last element of the list.
         */
        tail: LinkedList.Node<E>;
        /**
         * Number of nodes currently present in the list.
         */
        __size: number;
        constructor(c?: any);
        /**
         *
         * @param {number} index
         * @param {*} element
         */
        add(index?: any, element?: any): any;
        add$java_lang_Object(o: E): boolean;
        /**
         *
         * @param {*} o
         */
        addFirst(o: E): void;
        /**
         *
         * @param {*} o
         */
        addLast(o: E): void;
        /**
         *
         */
        clear(): void;
        reset(): void;
        clone(): any;
        /**
         *
         * @return {*}
         */
        descendingIterator(): java.util.Iterator<E>;
        /**
         *
         * @return {*}
         */
        element(): E;
        /**
         *
         * @return {*}
         */
        getFirst(): E;
        /**
         *
         * @return {*}
         */
        getLast(): E;
        listIterator$int(index: number): java.util.ListIterator<E>;
        /**
         *
         * @param {number} index
         * @return {*}
         */
        listIterator(index?: any): any;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        offer(o: E): boolean;
        /**
         *
         * @param {*} e
         * @return {boolean}
         */
        offerFirst(e: E): boolean;
        /**
         *
         * @param {*} e
         * @return {boolean}
         */
        offerLast(e: E): boolean;
        /**
         *
         * @return {*}
         */
        peek(): E;
        /**
         *
         * @return {*}
         */
        peekFirst(): E;
        /**
         *
         * @return {*}
         */
        peekLast(): E;
        /**
         *
         * @return {*}
         */
        poll(): E;
        /**
         *
         * @return {*}
         */
        pollFirst(): E;
        /**
         *
         * @return {*}
         */
        pollLast(): E;
        /**
         *
         * @return {*}
         */
        pop(): E;
        /**
         *
         * @param {*} e
         */
        push(e: E): void;
        /**
         *
         * @param {number} index
         * @return {*}
         */
        remove(index?: any): any;
        remove$(): E;
        /**
         *
         * @return {*}
         */
        removeFirst(): E;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        removeFirstOccurrence(o: any): boolean;
        /**
         *
         * @return {*}
         */
        removeLast(): E;
        /**
         *
         * @param {*} o
         * @return {boolean}
         */
        removeLastOccurrence(o: any): boolean;
        /**
         *
         * @return {number}
         */
        size(): number;
        addNode(o: E, prev: LinkedList.Node<E>, next: LinkedList.Node<E>): void;
        removeNode(node: LinkedList.Node<E>): E;
    }
    namespace LinkedList {
        class DescendingIteratorImpl implements java.util.Iterator<any> {
            __parent: any;
            forEachRemaining(consumer: (p1: any) => void): void;
            itr: java.util.ListIterator<any>;
            /**
             *
             * @return {boolean}
             */
            hasNext(): boolean;
            /**
             *
             * @return {*}
             */
            next(): any;
            /**
             *
             */
            remove(): void;
            constructor(__parent: any);
        }
        /**
         * @param {number} index
         * from the beginning of the list (0 = first node)
         * @param {java.util.LinkedList.Node} startNode
         * the initial current node
         * @class
         */
        class ListIteratorImpl2 implements java.util.ListIterator<any> {
            __parent: any;
            forEachRemaining(consumer: (p1: any) => void): void;
            /**
             * The index to the current position.
             */
            currentIndex: number;
            /**
             * Current node, to be returned from next.
             */
            currentNode: LinkedList.Node<any>;
            /**
             * The last node returned from next/previous, or null if deleted or
             * never called.
             */
            lastNode: LinkedList.Node<any>;
            constructor(__parent: any, index: number, startNode: LinkedList.Node<any>);
            /**
             *
             * @param {*} o
             */
            add(o: any): void;
            /**
             *
             * @return {boolean}
             */
            hasNext(): boolean;
            /**
             *
             * @return {boolean}
             */
            hasPrevious(): boolean;
            /**
             *
             * @return {*}
             */
            next(): any;
            /**
             *
             * @return {number}
             */
            nextIndex(): number;
            /**
             *
             * @return {*}
             */
            previous(): any;
            /**
             *
             * @return {number}
             */
            previousIndex(): number;
            /**
             *
             */
            remove(): void;
            /**
             *
             * @param {*} o
             */
            set(o: any): void;
        }
        /**
         * Internal class representing a doubly-linked list node.
         *
         * @param <E>
         * element type
         * @class
         */
        class Node<E> {
            next: LinkedList.Node<E>;
            prev: LinkedList.Node<E>;
            value: E;
            constructor();
        }
    }
}
declare namespace java.util {
    /**
     * Maintains a last-in, first-out collection of objects. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/Stack.html">[Sun
     * docs]</a>
     *
     * @param <E> element type.
     * @class
     * @extends java.util.Vector
     */
    class Stack<E> extends java.util.Vector<E> {
        /**
         *
         * @return {*}
         */
        clone(): any;
        empty(): boolean;
        peek(): E;
        pop(): E;
        push(o: E): E;
        search(o: any): number;
        constructor();
    }
}
declare namespace java.util {
    /**
     * Implementation of Map interface based on a hash table.
     * <a href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/HashMap.html">[Sun
     * docs]</a>
     *
     * @param <K>
     * key type
     * @param <V>
     * value type
     * @param {number} ignored
     * @param {number} alsoIgnored
     * @class
     * @extends java.util.AbstractMap
     */
    abstract class AbstractHashMap<K, V> extends java.util.AbstractMap<K, V> {
        /**
         * A map of integral hashCodes onto entries.
         */
        hashCodeMap: java.util.InternalHashCodeMap<K, V>;
        /**
         * A map of Strings onto values.
         */
        stringMap: java.util.InternalStringMap<K, V>;
        constructor(ignored?: any, alsoIgnored?: any);
        /**
         *
         */
        clear(): void;
        reset(): void;
        /**
         *
         * @param {*} key
         * @return {boolean}
         */
        containsKey(key: any): boolean;
        /**
         *
         * @param {*} value
         * @return {boolean}
         */
        containsValue(value: any): boolean;
        _containsValue(value: any, entries: java.lang.Iterable<Map.Entry<K, V>>): boolean;
        /**
         *
         * @return {*}
         */
        entrySet(): java.util.Set<java.util.Map.Entry<K, V>>;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        get(key: any): V;
        /**
         *
         * @param {*} key
         * @param {*} value
         * @return {*}
         */
        put(key: K, value: V): V;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        remove(key: any): V;
        /**
         *
         * @return {number}
         */
        size(): number;
        /**
         * Subclasses must override to return a whether or not two keys or values
         * are equal.
         * @param {*} value1
         * @param {*} value2
         * @return {boolean}
         */
        abstract _equals(value1: any, value2: any): boolean;
        /**
         * Subclasses must override to return a hash code for a given key. The key
         * is guaranteed to be non-null and not a String.
         * @param {*} key
         * @return {number}
         */
        abstract getHashCode(key: any): number;
        /**
         * Returns the Map.Entry whose key is Object equal to <code>key</code>,
         * provided that <code>key</code>'s hash code is <code>hashCode</code>; or
         * <code>null</code> if no such Map.Entry exists at the specified hashCode.
         * @param {*} key
         * @return {*}
         * @private
         */
        getHashValue(key: any): V;
        /**
         * Returns the value for the given key in the stringMap. Returns
         * <code>null</code> if the specified key does not exist.
         * @param {string} key
         * @return {*}
         * @private
         */
        getStringValue(key: string): V;
        /**
         * Returns true if the a key exists in the hashCodeMap that is Object equal
         * to <code>key</code>, provided that <code>key</code>'s hash code is
         * <code>hashCode</code>.
         * @param {*} key
         * @return {boolean}
         * @private
         */
        hasHashValue(key: any): boolean;
        /**
         * Returns true if the given key exists in the stringMap.
         * @param {string} key
         * @return {boolean}
         * @private
         */
        hasStringValue(key: string): boolean;
        /**
         * Sets the specified key to the specified value in the hashCodeMap. Returns
         * the value previously at that key. Returns <code>null</code> if the
         * specified key did not exist.
         * @param {*} key
         * @param {*} value
         * @return {*}
         * @private
         */
        putHashValue(key: K, value: V): V;
        /**
         * Sets the specified key to the specified value in the stringMap. Returns
         * the value previously at that key. Returns <code>null</code> if the
         * specified key did not exist.
         * @param {string} key
         * @param {*} value
         * @return {*}
         * @private
         */
        putStringValue(key: string, value: V): V;
        /**
         * Removes the pair whose key is Object equal to <code>key</code> from
         * <code>hashCodeMap</code>, provided that <code>key</code>'s hash code is
         * <code>hashCode</code>. Returns the value that was associated with the
         * removed key, or null if no such key existed.
         * @param {*} key
         * @return {*}
         * @private
         */
        removeHashValue(key: any): V;
        /**
         * Removes the specified key from the stringMap and returns the value that
         * was previously there. Returns <code>null</code> if the specified key does
         * not exist.
         * @param {string} key
         * @return {*}
         * @private
         */
        removeStringValue(key: string): V;
    }
    namespace AbstractHashMap {
        class EntrySet extends java.util.AbstractSet<Map.Entry<any, any>> {
            __parent: any;
            /**
             *
             */
            clear(): void;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            contains(o: any): boolean;
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<Map.Entry<any, any>>;
            /**
             *
             * @param {*} entry
             * @return {boolean}
             */
            remove(entry: any): boolean;
            /**
             *
             * @return {number}
             */
            size(): number;
            constructor(__parent: any);
        }
        /**
         * Iterator for <code>EntrySet</code>.
         * @class
         */
        class EntrySetIterator implements java.util.Iterator<Map.Entry<any, any>> {
            __parent: any;
            forEachRemaining(consumer: (p1: any) => void): void;
            stringMapEntries: java.util.Iterator<Map.Entry<any, any>>;
            current: java.util.Iterator<Map.Entry<any, any>>;
            last: java.util.Iterator<Map.Entry<any, any>>;
            __hasNext: boolean;
            constructor(__parent: any);
            /**
             *
             * @return {boolean}
             */
            hasNext(): boolean;
            computeHasNext(): boolean;
            /**
             *
             * @return {*}
             */
            next(): Map.Entry<any, any>;
            /**
             *
             */
            remove(): void;
        }
    }
}
declare namespace java.util {
    /**
     * Skeletal implementation of a NavigableMap.
     * @extends java.util.AbstractMap
     * @class
     */
    abstract class AbstractNavigableMap<K, V> extends java.util.AbstractMap<K, V> implements java.util.NavigableMap<K, V> {
        merge(key: any, value: any, map: (p1: any, p2: any) => any): any;
        computeIfAbsent(key: any, mappingFunction: (p1: any) => any): any;
        static copyOf<K, V>(entry: Map.Entry<K, V>): Map.Entry<K, V>;
        static getKeyOrNSE<K, V>(entry: Map.Entry<K, V>): K;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        ceilingEntry(key: K): Map.Entry<K, V>;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        ceilingKey(key: K): K;
        /**
         *
         * @param {*} k
         * @return {boolean}
         */
        containsKey(k: any): boolean;
        /**
         *
         * @return {*}
         */
        descendingKeySet(): java.util.NavigableSet<K>;
        /**
         *
         * @return {*}
         */
        descendingMap(): java.util.NavigableMap<K, V>;
        /**
         *
         * @return {*}
         */
        entrySet(): java.util.Set<Map.Entry<K, V>>;
        /**
         *
         * @return {*}
         */
        firstEntry(): Map.Entry<K, V>;
        /**
         *
         * @return {*}
         */
        firstKey(): K;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        floorEntry(key: K): Map.Entry<K, V>;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        floorKey(key: K): K;
        /**
         *
         * @param {*} k
         * @return {*}
         */
        get(k: any): V;
        headMap(toKey?: any, inclusive?: any): any;
        headMap$java_lang_Object(toKey: K): java.util.SortedMap<K, V>;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        higherEntry(key: K): Map.Entry<K, V>;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        higherKey(key: K): K;
        /**
         *
         * @return {*}
         */
        keySet(): java.util.Set<K>;
        /**
         *
         * @return {*}
         */
        lastEntry(): Map.Entry<K, V>;
        /**
         *
         * @return {*}
         */
        lastKey(): K;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        lowerEntry(key: K): Map.Entry<K, V>;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        lowerKey(key: K): K;
        /**
         *
         * @return {*}
         */
        navigableKeySet(): java.util.NavigableSet<K>;
        /**
         *
         * @return {*}
         */
        pollFirstEntry(): Map.Entry<K, V>;
        /**
         *
         * @return {*}
         */
        pollLastEntry(): Map.Entry<K, V>;
        subMap(fromKey?: any, fromInclusive?: any, toKey?: any, toInclusive?: any): any;
        subMap$java_lang_Object$java_lang_Object(fromKey: K, toKey: K): java.util.SortedMap<K, V>;
        tailMap(fromKey?: any, inclusive?: any): any;
        tailMap$java_lang_Object(fromKey: K): java.util.SortedMap<K, V>;
        /**
         *
         * @param {*} entry
         * @return {boolean}
         */
        containsEntry(entry: Map.Entry<any, any>): boolean;
        /**
         * Returns an iterator over the entries in this map in descending order.
         * @return {*}
         */
        abstract descendingEntryIterator(): java.util.Iterator<Map.Entry<K, V>>;
        /**
         * Returns an iterator over the entries in this map in ascending order.
         * @return {*}
         */
        abstract entryIterator(): java.util.Iterator<Map.Entry<K, V>>;
        /**
         * Returns the entry corresponding to the specified key. If no such entry exists returns
         * {@code null}.
         * @param {*} key
         * @return {*}
         */
        abstract getEntry(key: K): Map.Entry<K, V>;
        /**
         * Returns the first entry or {@code null} if map is empty.
         * @return {*}
         */
        abstract getFirstEntry(): Map.Entry<K, V>;
        /**
         * Returns the last entry or {@code null} if map is empty.
         * @return {*}
         */
        abstract getLastEntry(): Map.Entry<K, V>;
        /**
         * Gets the entry corresponding to the specified key or the entry for the least key greater than
         * the specified key. If no such entry exists returns {@code null}.
         * @param {*} key
         * @return {*}
         */
        abstract getCeilingEntry(key: K): Map.Entry<K, V>;
        /**
         * Gets the entry corresponding to the specified key or the entry for the greatest key less than
         * the specified key. If no such entry exists returns {@code null}.
         * @param {*} key
         * @return {*}
         */
        abstract getFloorEntry(key: K): Map.Entry<K, V>;
        /**
         * Gets the entry for the least key greater than the specified key. If no such entry exists
         * returns {@code null}.
         * @param {*} key
         * @return {*}
         */
        abstract getHigherEntry(key: K): Map.Entry<K, V>;
        /**
         * Returns the entry for the greatest key less than the specified key. If no such entry exists
         * returns {@code null}.
         * @param {*} key
         * @return {*}
         */
        abstract getLowerEntry(key: K): Map.Entry<K, V>;
        /**
         * Remove an entry from the tree, returning whether it was found.
         * @param {*} entry
         * @return {boolean}
         */
        abstract removeEntry(entry: Map.Entry<K, V>): boolean;
        pollEntry(entry: Map.Entry<K, V>): Map.Entry<K, V>;
        abstract comparator(): any;
        constructor();
    }
    namespace AbstractNavigableMap {
        class DescendingMap extends java.util.AbstractNavigableMap<any, any> {
            __parent: any;
            /**
             *
             */
            clear(): void;
            /**
             *
             * @return {*}
             */
            comparator(): java.util.Comparator<any>;
            /**
             *
             * @return {*}
             */
            descendingMap(): java.util.NavigableMap<any, any>;
            headMap$java_lang_Object$boolean(toKey: any, inclusive: boolean): java.util.NavigableMap<any, any>;
            /**
             *
             * @param {*} toKey
             * @param {boolean} inclusive
             * @return {*}
             */
            headMap(toKey?: any, inclusive?: any): any;
            /**
             *
             * @param {*} key
             * @param {*} value
             * @return {*}
             */
            put(key: any, value: any): any;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            remove(key: any): any;
            /**
             *
             * @return {number}
             */
            size(): number;
            subMap$java_lang_Object$boolean$java_lang_Object$boolean(fromKey: any, fromInclusive: boolean, toKey: any, toInclusive: boolean): java.util.NavigableMap<any, any>;
            /**
             *
             * @param {*} fromKey
             * @param {boolean} fromInclusive
             * @param {*} toKey
             * @param {boolean} toInclusive
             * @return {*}
             */
            subMap(fromKey?: any, fromInclusive?: any, toKey?: any, toInclusive?: any): any;
            tailMap$java_lang_Object$boolean(fromKey: any, inclusive: boolean): java.util.NavigableMap<any, any>;
            /**
             *
             * @param {*} fromKey
             * @param {boolean} inclusive
             * @return {*}
             */
            tailMap(fromKey?: any, inclusive?: any): any;
            ascendingMap(): java.util.AbstractNavigableMap<any, any>;
            /**
             *
             * @return {*}
             */
            descendingEntryIterator(): java.util.Iterator<Map.Entry<any, any>>;
            /**
             *
             * @return {*}
             */
            entryIterator(): java.util.Iterator<Map.Entry<any, any>>;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            getEntry(key: any): Map.Entry<any, any>;
            /**
             *
             * @return {*}
             */
            getFirstEntry(): Map.Entry<any, any>;
            /**
             *
             * @return {*}
             */
            getLastEntry(): Map.Entry<any, any>;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            getCeilingEntry(key: any): Map.Entry<any, any>;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            getFloorEntry(key: any): Map.Entry<any, any>;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            getHigherEntry(key: any): Map.Entry<any, any>;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            getLowerEntry(key: any): Map.Entry<any, any>;
            /**
             *
             * @param {*} entry
             * @return {boolean}
             */
            removeEntry(entry: Map.Entry<any, any>): boolean;
            constructor(__parent: any);
        }
        class EntrySet extends java.util.AbstractSet<Map.Entry<any, any>> {
            __parent: any;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            contains(o: any): boolean;
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<Map.Entry<any, any>>;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            remove(o: any): boolean;
            /**
             *
             * @return {number}
             */
            size(): number;
            constructor(__parent: any);
        }
        class NavigableKeySet<K, V> extends java.util.AbstractSet<K> implements java.util.NavigableSet<K> {
            stream(): java.util.stream.Stream<any>;
            forEach(action: (p1: any) => void): void;
            removeIf(filter: (p1: any) => boolean): boolean;
            map: java.util.NavigableMap<K, V>;
            constructor(map: java.util.NavigableMap<K, V>);
            /**
             *
             * @param {*} k
             * @return {*}
             */
            ceiling(k: K): K;
            /**
             *
             */
            clear(): void;
            /**
             *
             * @return {*}
             */
            comparator(): java.util.Comparator<any>;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            contains(o: any): boolean;
            /**
             *
             * @return {*}
             */
            descendingIterator(): java.util.Iterator<K>;
            /**
             *
             * @return {*}
             */
            descendingSet(): java.util.NavigableSet<K>;
            /**
             *
             * @return {*}
             */
            first(): K;
            /**
             *
             * @param {*} k
             * @return {*}
             */
            floor(k: K): K;
            headSet$java_lang_Object(toElement: K): java.util.SortedSet<K>;
            headSet$java_lang_Object$boolean(toElement: K, inclusive: boolean): java.util.NavigableSet<K>;
            /**
             *
             * @param {*} toElement
             * @param {boolean} inclusive
             * @return {*}
             */
            headSet(toElement?: any, inclusive?: any): any;
            /**
             *
             * @param {*} k
             * @return {*}
             */
            higher(k: K): K;
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<K>;
            /**
             *
             * @return {*}
             */
            last(): K;
            /**
             *
             * @param {*} k
             * @return {*}
             */
            lower(k: K): K;
            /**
             *
             * @return {*}
             */
            pollFirst(): K;
            /**
             *
             * @return {*}
             */
            pollLast(): K;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            remove(o: any): boolean;
            /**
             *
             * @return {number}
             */
            size(): number;
            subSet$java_lang_Object$boolean$java_lang_Object$boolean(fromElement: K, fromInclusive: boolean, toElement: K, toInclusive: boolean): java.util.NavigableSet<K>;
            /**
             *
             * @param {*} fromElement
             * @param {boolean} fromInclusive
             * @param {*} toElement
             * @param {boolean} toInclusive
             * @return {*}
             */
            subSet(fromElement?: any, fromInclusive?: any, toElement?: any, toInclusive?: any): any;
            subSet$java_lang_Object$java_lang_Object(fromElement: K, toElement: K): java.util.SortedSet<K>;
            tailSet$java_lang_Object(fromElement: K): java.util.SortedSet<K>;
            tailSet$java_lang_Object$boolean(fromElement: K, inclusive: boolean): java.util.NavigableSet<K>;
            /**
             *
             * @param {*} fromElement
             * @param {boolean} inclusive
             * @return {*}
             */
            tailSet(fromElement?: any, inclusive?: any): any;
        }
        namespace NavigableKeySet {
            class NavigableKeySet$0 implements java.util.Iterator<any> {
                private entryIterator;
                __parent: any;
                forEachRemaining(consumer: (p1: any) => void): void;
                /**
                 *
                 * @return {boolean}
                 */
                hasNext(): boolean;
                /**
                 *
                 * @return {*}
                 */
                next(): any;
                /**
                 *
                 */
                remove(): void;
                constructor(__parent: any, entryIterator: any);
            }
        }
    }
}
declare namespace java.util {
    /**
     * Utility methods that operate on collections. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/Collections.html">[Sun
     * docs]</a>
     * @class
     */
    class Collections {
        static EMPTY_LIST: java.util.List<any>;
        static EMPTY_LIST_$LI$(): java.util.List<any>;
        static EMPTY_MAP: java.util.Map<any, any>;
        static EMPTY_MAP_$LI$(): java.util.Map<any, any>;
        static EMPTY_SET: java.util.Set<any>;
        static EMPTY_SET_$LI$(): java.util.Set<any>;
        static addAll<T>(c: java.util.Collection<any>, ...a: T[]): boolean;
        static asLifoQueue<T>(deque: java.util.Deque<T>): java.util.Queue<T>;
        static binarySearch$java_util_List$java_lang_Object<T>(sortedList: java.util.List<any>, key: T): number;
        static binarySearch$java_util_List$java_lang_Object$java_util_Comparator<T>(sortedList: java.util.List<any>, key: T, comparator: java.util.Comparator<any>): number;
        /**
         * Perform a binary search on a sorted List, using a user-specified comparison
         * function.
         *
         * <p>
         * Note: The GWT implementation differs from the JDK implementation in that it
         * does not do an iterator-based binary search for Lists that do not implement
         * RandomAccess.
         * </p>
         *
         * @param {*} sortedList List to search
         * @param {*} key value to search for
         * @param {*} comparator comparision function, <code>null</code> indicates
         * <i>natural ordering</i> should be used.
         * @return {number} the index of an element with a matching value, or a negative number
         * which is the index of the next larger value (or just past the end
         * of the array if the searched value is larger than all elements in
         * the array) minus 1 (to ensure error returns are negative)
         * @throws ClassCastException if <code>key</code> and
         * <code>sortedList</code>'s elements cannot be compared by
         * <code>comparator</code>.
         */
        static binarySearch<T>(sortedList?: any, key?: any, comparator?: any): any;
        static copy<T>(dest: java.util.List<any>, src: java.util.List<any>): void;
        static disjoint(c1: java.util.Collection<any>, c2: java.util.Collection<any>): boolean;
        static emptyIterator<T>(): java.util.Iterator<T>;
        static emptyList<T>(): java.util.List<T>;
        static emptyListIterator<T>(): java.util.ListIterator<T>;
        static emptyMap<K, V>(): java.util.Map<K, V>;
        static emptySet<T>(): java.util.Set<T>;
        static enumeration<T>(c: java.util.Collection<T>): java.util.Enumeration<T>;
        static fill<T>(list: java.util.List<any>, obj: T): void;
        static frequency(c: java.util.Collection<any>, o: any): number;
        static list<T>(e: java.util.Enumeration<T>): java.util.ArrayList<T>;
        static max$java_util_Collection<T extends any & java.lang.Comparable<any>>(coll: java.util.Collection<any>): T;
        static max$java_util_Collection$java_util_Comparator<T>(coll: java.util.Collection<any>, comp: java.util.Comparator<any>): T;
        static max<T>(coll?: any, comp?: any): any;
        static min<T>(coll: java.util.Collection<any>, comp?: java.util.Comparator<any>): T;
        static newSetFromMap<E>(map: java.util.Map<E, boolean>): java.util.Set<E>;
        static nCopies<T>(n: number, o: T): java.util.List<T>;
        static replaceAll<T>(list: java.util.List<T>, oldVal: T, newVal: T): boolean;
        static reverse<T>(l: java.util.List<T>): void;
        static reverseOrder$<T>(): java.util.Comparator<T>;
        static reverseOrder$java_util_Comparator<T>(cmp: java.util.Comparator<T>): java.util.Comparator<T>;
        static reverseOrder<T>(cmp?: any): any;
        /**
         * Rotates the elements in {@code list} by the distance {@code dist}
         * <p>
         * e.g. for a given list with elements [1, 2, 3, 4, 5, 6, 7, 8, 9, 0], calling rotate(list, 3) or
         * rotate(list, -7) would modify the list to look like this: [8, 9, 0, 1, 2, 3, 4, 5, 6, 7]
         *
         * @param {*} lst the list whose elements are to be rotated.
         * @param {number} dist is the distance the list is rotated. This can be any valid integer. Negative values
         * rotate the list backwards.
         */
        static rotate(lst: java.util.List<any>, dist: number): void;
        static shuffle<T>(list: java.util.List<T>, rnd?: java.util.Random): void;
        static singleton<T>(o: T): java.util.Set<T>;
        static singletonList<T>(o: T): java.util.List<T>;
        static singletonMap<K, V>(key: K, value: V): java.util.Map<K, V>;
        static sort$java_util_List<T>(target: java.util.List<T>): void;
        static sort$java_util_List$java_util_Comparator<T>(target: java.util.List<T>, c: java.util.Comparator<any>): void;
        static sort<T>(target?: any, c?: any): any;
        static swap(list: java.util.List<any>, i: number, j: number): void;
        static unmodifiableCollection<T>(coll: java.util.Collection<any>): java.util.Collection<T>;
        static unmodifiableList<T>(list: java.util.List<any>): java.util.List<T>;
        static unmodifiableMap<K, V>(map: java.util.Map<any, any>): java.util.Map<K, V>;
        static unmodifiableSet<T>(set: java.util.Set<any>): java.util.Set<T>;
        static unmodifiableSortedMap<K, V>(map: java.util.SortedMap<K, any>): java.util.SortedMap<K, V>;
        static unmodifiableSortedSet<T>(set: java.util.SortedSet<any>): java.util.SortedSet<T>;
        static hashCode$java_lang_Iterable<T>(collection: java.lang.Iterable<T>): number;
        static hashCode$java_util_List<T>(list: java.util.List<T>): number;
        /**
         * Computes hash code preserving collection order (e.g. ArrayList).
         * @param {*} list
         * @return {number}
         */
        static hashCode<T>(list?: any): any;
        /**
         * Replace contents of a list from an array.
         *
         * @param <T> element type
         * @param {*} target list to replace contents from an array
         * @param {Array} x an Object array which can contain only T instances
         * @private
         */
        static replaceContents<T>(target: java.util.List<T>, x: any[]): void;
        static swapImpl$java_util_List$int$int<T>(list: java.util.List<T>, i: number, j: number): void;
        static swapImpl<T>(list?: any, i?: any, j?: any): any;
        static swapImpl$java_lang_Object_A$int$int(a: any[], i: number, j: number): void;
    }
    namespace Collections {
        class LifoQueue<E> extends java.util.AbstractQueue<E> implements java.io.Serializable {
            deque: java.util.Deque<E>;
            constructor(deque: java.util.Deque<E>);
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<E>;
            /**
             *
             * @param {*} e
             * @return {boolean}
             */
            offer(e: E): boolean;
            /**
             *
             * @return {*}
             */
            peek(): E;
            /**
             *
             * @return {*}
             */
            poll(): E;
            /**
             *
             * @return {number}
             */
            size(): number;
        }
        class EmptyList extends java.util.AbstractList<any> implements java.util.RandomAccess, java.io.Serializable {
            /**
             *
             * @param {*} object
             * @return {boolean}
             */
            contains(object: any): boolean;
            /**
             *
             * @param {number} location
             * @return {*}
             */
            get(location: number): any;
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<any>;
            /**
             *
             * @param {number} from
             * @return {*}
             */
            listIterator(from?: any): any;
            listIterator$(): java.util.ListIterator<any>;
            /**
             *
             * @return {number}
             */
            size(): number;
            constructor();
        }
        class EmptyListIterator implements java.util.ListIterator<any> {
            forEachRemaining(consumer: (p1: any) => void): void;
            static INSTANCE: Collections.EmptyListIterator;
            static INSTANCE_$LI$(): Collections.EmptyListIterator;
            /**
             *
             * @param {*} o
             */
            add(o: any): void;
            /**
             *
             * @return {boolean}
             */
            hasNext(): boolean;
            /**
             *
             * @return {boolean}
             */
            hasPrevious(): boolean;
            /**
             *
             * @return {*}
             */
            next(): any;
            /**
             *
             * @return {number}
             */
            nextIndex(): number;
            /**
             *
             * @return {*}
             */
            previous(): any;
            /**
             *
             * @return {number}
             */
            previousIndex(): number;
            /**
             *
             */
            remove(): void;
            /**
             *
             * @param {*} o
             */
            set(o: any): void;
            constructor();
        }
        class EmptySet extends java.util.AbstractSet<any> implements java.io.Serializable {
            /**
             *
             * @param {*} object
             * @return {boolean}
             */
            contains(object: any): boolean;
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<any>;
            /**
             *
             * @return {number}
             */
            size(): number;
            constructor();
        }
        class EmptyMap extends java.util.AbstractMap<any, any> implements java.io.Serializable {
            /**
             *
             * @param {*} key
             * @return {boolean}
             */
            containsKey(key: any): boolean;
            /**
             *
             * @param {*} value
             * @return {boolean}
             */
            containsValue(value: any): boolean;
            /**
             *
             * @return {*}
             */
            entrySet(): java.util.Set<any>;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            get(key: any): any;
            /**
             *
             * @return {*}
             */
            keySet(): java.util.Set<any>;
            /**
             *
             * @return {number}
             */
            size(): number;
            /**
             *
             * @return {*}
             */
            values(): java.util.Collection<any>;
            constructor();
        }
        class ReverseComparator {
            static INSTANCE: Collections.ReverseComparator;
            static INSTANCE_$LI$(): Collections.ReverseComparator;
            compare$java_lang_Comparable$java_lang_Comparable(o1: java.lang.Comparable<any>, o2: java.lang.Comparable<any>): number;
            /**
             *
             * @param {*} o1
             * @param {*} o2
             * @return {number}
             */
            compare(o1?: any, o2?: any): any;
            constructor();
        }
        class SetFromMap<E> extends java.util.AbstractSet<E> implements java.io.Serializable {
            backingMap: java.util.Map<E, boolean>;
            __keySet: java.util.Set<E>;
            constructor(map: java.util.Map<E, boolean>);
            /**
             *
             * @param {*} e
             * @return {boolean}
             */
            add(e: E): boolean;
            /**
             *
             */
            clear(): void;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            contains(o: any): boolean;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            equals(o: any): boolean;
            /**
             *
             * @return {number}
             */
            hashCode(): number;
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<E>;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            remove(o: any): boolean;
            /**
             *
             * @return {number}
             */
            size(): number;
            /**
             *
             * @return {string}
             */
            toString(): string;
            /**
             * Lazy initialize keySet to avoid NPE after deserialization.
             * @return {*}
             * @private
             */
            keySet(): java.util.Set<E>;
        }
        class SingletonList<E> extends java.util.AbstractList<E> implements java.io.Serializable {
            element: E;
            constructor(element: E);
            /**
             *
             * @param {*} item
             * @return {boolean}
             */
            contains(item: any): boolean;
            /**
             *
             * @param {number} index
             * @return {*}
             */
            get(index: number): E;
            /**
             *
             * @return {number}
             */
            size(): number;
        }
        class UnmodifiableCollection<T> implements java.util.Collection<T> {
            stream(): java.util.stream.Stream<any>;
            forEach(action: (p1: any) => void): void;
            removeIf(filter: (p1: any) => boolean): boolean;
            coll: java.util.Collection<any>;
            constructor(coll: java.util.Collection<any>);
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            add(o: T): boolean;
            /**
             *
             * @param {*} c
             * @return {boolean}
             */
            addAll(c: java.util.Collection<any>): boolean;
            /**
             *
             */
            clear(): void;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            contains(o: any): boolean;
            /**
             *
             * @param {*} c
             * @return {boolean}
             */
            containsAll(c: java.util.Collection<any>): boolean;
            /**
             *
             * @return {boolean}
             */
            isEmpty(): boolean;
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<T>;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            remove(o: any): boolean;
            /**
             *
             * @param {*} c
             * @return {boolean}
             */
            removeAll(c: java.util.Collection<any>): boolean;
            /**
             *
             * @param {*} c
             * @return {boolean}
             */
            retainAll(c: java.util.Collection<any>): boolean;
            /**
             *
             * @return {number}
             */
            size(): number;
            toArray$(): any[];
            toArray$java_lang_Object_A<E>(a: E[]): E[];
            /**
             *
             * @param {Array} a
             * @return {Array}
             */
            toArray<E>(a?: any): any;
            /**
             *
             * @return {string}
             */
            toString(): string;
        }
        class UnmodifiableCollectionIterator<T> implements java.util.Iterator<T> {
            forEachRemaining(consumer: (p1: any) => void): void;
            it: java.util.Iterator<any>;
            constructor(it: java.util.Iterator<any>);
            /**
             *
             * @return {boolean}
             */
            hasNext(): boolean;
            /**
             *
             * @return {*}
             */
            next(): T;
            /**
             *
             */
            remove(): void;
        }
        class RandomHolder {
            static rnd: java.util.Random;
            static rnd_$LI$(): java.util.Random;
            constructor();
        }
        class UnmodifiableList<T> extends Collections.UnmodifiableCollection<T> implements java.util.List<T> {
            stream(): java.util.stream.Stream<any>;
            forEach(action: (p1: any) => void): void;
            removeIf(filter: (p1: any) => boolean): boolean;
            list: java.util.List<any>;
            constructor(list: java.util.List<any>);
            add$int$java_lang_Object(index: number, element: T): void;
            /**
             *
             * @param {number} index
             * @param {*} element
             */
            add(index?: any, element?: any): any;
            addAll$int$java_util_Collection(index: number, c: java.util.Collection<any>): boolean;
            /**
             *
             * @param {number} index
             * @param {*} c
             * @return {boolean}
             */
            addAll(index?: any, c?: any): any;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            equals(o: any): boolean;
            /**
             *
             * @param {number} index
             * @return {*}
             */
            get(index: number): T;
            /**
             *
             * @return {number}
             */
            hashCode(): number;
            /**
             *
             * @param {*} o
             * @return {number}
             */
            indexOf(o: any): number;
            /**
             *
             * @return {boolean}
             */
            isEmpty(): boolean;
            /**
             *
             * @param {*} o
             * @return {number}
             */
            lastIndexOf(o: any): number;
            listIterator$(): java.util.ListIterator<T>;
            listIterator$int(from: number): java.util.ListIterator<T>;
            /**
             *
             * @param {number} from
             * @return {*}
             */
            listIterator(from?: any): any;
            remove$int(index: number): T;
            /**
             *
             * @param {number} index
             * @return {*}
             */
            remove(index?: any): any;
            /**
             *
             * @param {number} index
             * @param {*} element
             * @return {*}
             */
            set(index: number, element: T): T;
            /**
             *
             * @param {number} fromIndex
             * @param {number} toIndex
             * @return {*}
             */
            subList(fromIndex: number, toIndex: number): java.util.List<T>;
        }
        class UnmodifiableSet<T> extends Collections.UnmodifiableCollection<T> implements java.util.Set<T> {
            stream(): java.util.stream.Stream<any>;
            forEach(action: (p1: any) => void): void;
            removeIf(filter: (p1: any) => boolean): boolean;
            constructor(set: java.util.Set<any>);
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            equals(o: any): boolean;
            /**
             *
             * @return {number}
             */
            hashCode(): number;
        }
        class UnmodifiableListIterator<T> extends Collections.UnmodifiableCollectionIterator<T> implements java.util.ListIterator<T> {
            forEachRemaining(consumer: (p1: any) => void): void;
            lit: java.util.ListIterator<any>;
            constructor(lit: java.util.ListIterator<any>);
            /**
             *
             * @param {*} o
             */
            add(o: T): void;
            /**
             *
             * @return {boolean}
             */
            hasPrevious(): boolean;
            /**
             *
             * @return {number}
             */
            nextIndex(): number;
            /**
             *
             * @return {*}
             */
            previous(): T;
            /**
             *
             * @return {number}
             */
            previousIndex(): number;
            /**
             *
             * @param {*} o
             */
            set(o: T): void;
        }
        class UnmodifiableRandomAccessList<T> extends Collections.UnmodifiableList<T> implements java.util.RandomAccess {
            constructor(list: java.util.List<any>);
        }
        class UnmodifiableMap<K, V> implements java.util.Map<K, V> {
            merge(key: any, value: any, map: (p1: any, p2: any) => any): any;
            computeIfAbsent(key: any, mappingFunction: (p1: any) => any): any;
            __entrySet: Collections.UnmodifiableSet<java.util.Map.Entry<K, V>>;
            __keySet: Collections.UnmodifiableSet<K>;
            map: java.util.Map<any, any>;
            __values: Collections.UnmodifiableCollection<V>;
            constructor(map: java.util.Map<any, any>);
            /**
             *
             */
            clear(): void;
            /**
             *
             * @param {*} key
             * @return {boolean}
             */
            containsKey(key: any): boolean;
            /**
             *
             * @param {*} val
             * @return {boolean}
             */
            containsValue(val: any): boolean;
            /**
             *
             * @return {*}
             */
            entrySet(): java.util.Set<java.util.Map.Entry<K, V>>;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            equals(o: any): boolean;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            get(key: any): V;
            /**
             *
             * @return {number}
             */
            hashCode(): number;
            /**
             *
             * @return {boolean}
             */
            isEmpty(): boolean;
            /**
             *
             * @return {*}
             */
            keySet(): java.util.Set<K>;
            /**
             *
             * @param {*} key
             * @param {*} value
             * @return {*}
             */
            put(key: K, value: V): V;
            /**
             *
             * @param {*} t
             */
            putAll(t: java.util.Map<any, any>): void;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            remove(key: any): V;
            /**
             *
             * @return {number}
             */
            size(): number;
            /**
             *
             * @return {string}
             */
            toString(): string;
            /**
             *
             * @return {*}
             */
            values(): java.util.Collection<V>;
        }
        namespace UnmodifiableMap {
            class UnmodifiableEntrySet<K, V> extends Collections.UnmodifiableSet<java.util.Map.Entry<K, V>> {
                constructor(s: java.util.Set<any>);
                /**
                 *
                 * @param {*} o
                 * @return {boolean}
                 */
                contains(o: any): boolean;
                /**
                 *
                 * @param {*} o
                 * @return {boolean}
                 */
                containsAll(o: java.util.Collection<any>): boolean;
                /**
                 *
                 * @return {*}
                 */
                iterator(): java.util.Iterator<java.util.Map.Entry<K, V>>;
                toArray$(): any[];
                toArray$java_lang_Object_A<T>(a: T[]): T[];
                /**
                 *
                 * @param {Array} a
                 * @return {Array}
                 */
                toArray<T>(a?: any): any;
                /**
                 * Wrap an array of Map.Entries as UnmodifiableEntries.
                 *
                 * @param {Array} array array to wrap
                 * @param {number} size number of entries to wrap
                 * @private
                 */
                wrap(array: any[], size: number): void;
            }
            namespace UnmodifiableEntrySet {
                class UnmodifiableEntry<K, V> implements java.util.Map.Entry<K, V> {
                    entry: java.util.Map.Entry<any, any>;
                    constructor(entry: java.util.Map.Entry<any, any>);
                    /**
                     *
                     * @param {*} o
                     * @return {boolean}
                     */
                    equals(o: any): boolean;
                    /**
                     *
                     * @return {*}
                     */
                    getKey(): K;
                    /**
                     *
                     * @return {*}
                     */
                    getValue(): V;
                    /**
                     *
                     * @return {number}
                     */
                    hashCode(): number;
                    /**
                     *
                     * @param {*} value
                     * @return {*}
                     */
                    setValue(value: V): V;
                    /**
                     *
                     * @return {string}
                     */
                    toString(): string;
                }
                class UnmodifiableEntrySet$0 implements java.util.Iterator<java.util.Map.Entry<any, any>> {
                    private it;
                    __parent: any;
                    forEachRemaining(consumer: (p1: any) => void): void;
                    /**
                     *
                     * @return {boolean}
                     */
                    hasNext(): boolean;
                    /**
                     *
                     * @return {*}
                     */
                    next(): java.util.Map.Entry<any, any>;
                    /**
                     *
                     */
                    remove(): void;
                    constructor(__parent: any, it: any);
                }
            }
        }
        class UnmodifiableSortedSet<E> extends Collections.UnmodifiableSet<E> implements java.util.SortedSet<E> {
            stream(): java.util.stream.Stream<any>;
            forEach(action: (p1: any) => void): void;
            removeIf(filter: (p1: any) => boolean): boolean;
            sortedSet: java.util.SortedSet<E>;
            constructor(sortedSet: java.util.SortedSet<any>);
            /**
             *
             * @return {*}
             */
            comparator(): java.util.Comparator<any>;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            equals(o: any): boolean;
            /**
             *
             * @return {*}
             */
            first(): E;
            /**
             *
             * @return {number}
             */
            hashCode(): number;
            /**
             *
             * @param {*} toElement
             * @return {*}
             */
            headSet(toElement: E): java.util.SortedSet<E>;
            /**
             *
             * @return {*}
             */
            last(): E;
            /**
             *
             * @param {*} fromElement
             * @param {*} toElement
             * @return {*}
             */
            subSet(fromElement: E, toElement: E): java.util.SortedSet<E>;
            /**
             *
             * @param {*} fromElement
             * @return {*}
             */
            tailSet(fromElement: E): java.util.SortedSet<E>;
        }
        class UnmodifiableSortedMap<K, V> extends Collections.UnmodifiableMap<K, V> implements java.util.SortedMap<K, V> {
            merge(key: any, value: any, map: (p1: any, p2: any) => any): any;
            computeIfAbsent(key: any, mappingFunction: (p1: any) => any): any;
            sortedMap: java.util.SortedMap<K, any>;
            constructor(sortedMap: java.util.SortedMap<K, any>);
            /**
             *
             * @return {*}
             */
            comparator(): java.util.Comparator<any>;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            equals(o: any): boolean;
            /**
             *
             * @return {*}
             */
            firstKey(): K;
            /**
             *
             * @return {number}
             */
            hashCode(): number;
            /**
             *
             * @param {*} toKey
             * @return {*}
             */
            headMap(toKey: K): java.util.SortedMap<K, V>;
            /**
             *
             * @return {*}
             */
            lastKey(): K;
            /**
             *
             * @param {*} fromKey
             * @param {*} toKey
             * @return {*}
             */
            subMap(fromKey: K, toKey: K): java.util.SortedMap<K, V>;
            /**
             *
             * @param {*} fromKey
             * @return {*}
             */
            tailMap(fromKey: K): java.util.SortedMap<K, V>;
        }
        class Collections$0<T> implements java.util.Enumeration<T> {
            private it;
            /**
             *
             * @return {boolean}
             */
            hasMoreElements(): boolean;
            /**
             *
             * @return {*}
             */
            nextElement(): T;
            constructor(it: any);
        }
        class Collections$1<T> {
            private cmp;
            
            /**
             *
             * @param {*} t1
             * @param {*} t2
             * @return {number}
             */
            compare(t1: T, t2: T): number;
            

            constructor(cmp: any);
        }
    }
}
declare namespace java.util {
    /**
     * A {@link java.util.Map} of {@link Enum}s. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/EnumMap.html">[Sun
     * docs]</a>
     *
     * @param <K> key type
     * @param <V> value type
     * @param {java.lang.Class} type
     * @class
     * @extends java.util.AbstractMap
     */
    class EnumMap<K extends java.lang.Enum<K>, V> extends java.util.AbstractMap<K, V> {
        __keySet: java.util.EnumSet<K>;
        __values: V[];
        constructor(type?: any);
        /**
         *
         */
        clear(): void;
        clone(): EnumMap<K, V>;
        /**
         *
         * @param {*} key
         * @return {boolean}
         */
        containsKey(key: any): boolean;
        /**
         *
         * @param {*} value
         * @return {boolean}
         */
        containsValue(value: any): boolean;
        /**
         *
         * @return {*}
         */
        entrySet(): java.util.Set<java.util.Map.Entry<K, V>>;
        /**
         *
         * @param {*} k
         * @return {*}
         */
        get(k: any): V;
        put$java_lang_Enum$java_lang_Object(key: K, value: V): V;
        /**
         *
         * @param {java.lang.Enum} key
         * @param {*} value
         * @return {*}
         */
        put(key?: any, value?: any): any;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        remove(key: any): V;
        /**
         *
         * @return {number}
         */
        size(): number;
        /**
         * Returns <code>key</code> as <code>K</code>. Only runtime checks that
         * key is an Enum, not that it's the particular Enum K. Should only be called
         * when you are sure <code>key</code> is of type <code>K</code>.
         * @param {*} key
         * @return {java.lang.Enum}
         * @private
         */
        asKey(key: any): K;
        asOrdinal(key: any): number;
        init$java_lang_Class(type: any): void;
        init(type?: any): any;
        init$java_util_EnumMap(m: EnumMap<K, any>): void;
        set(ordinal: number, value: V): V;
    }
    namespace EnumMap {
        class EntrySet extends java.util.AbstractSet<Map.Entry<any, any>> {
            __parent: any;
            /**
             *
             */
            clear(): void;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            contains(o: any): boolean;
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<Map.Entry<any, any>>;
            /**
             *
             * @param {*} entry
             * @return {boolean}
             */
            remove(entry: any): boolean;
            /**
             *
             * @return {number}
             */
            size(): number;
            constructor(__parent: any);
        }
        class EntrySetIterator implements java.util.Iterator<Map.Entry<any, any>> {
            __parent: any;
            forEachRemaining(consumer: (p1: any) => void): void;
            it: java.util.Iterator<any>;
            key: any;
            /**
             *
             * @return {boolean}
             */
            hasNext(): boolean;
            /**
             *
             * @return {*}
             */
            next(): Map.Entry<any, any>;
            /**
             *
             */
            remove(): void;
            constructor(__parent: any);
        }
        class MapEntry extends java.util.AbstractMapEntry<any, any> {
            __parent: any;
            key: any;
            constructor(__parent: any, key: any);
            /**
             *
             * @return {java.lang.Enum}
             */
            getKey(): any;
            /**
             *
             * @return {*}
             */
            getValue(): any;
            /**
             *
             * @param {*} value
             * @return {*}
             */
            setValue(value: any): any;
        }
    }
}
declare namespace java.util {
    /**
     * Hash table and linked-list implementation of the Set interface with
     * predictable iteration order. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/LinkedHashSet.html">[Sun
     * docs]</a>
     *
     * @param <E> element type.
     * @param {number} ignored
     * @param {number} alsoIgnored
     * @class
     * @extends java.util.HashSet
     */
    class LinkedHashSet<E> extends java.util.HashSet<E> implements java.util.Set<E>, java.lang.Cloneable {
        stream(): java.util.stream.Stream<any>;
        forEach(action: (p1: any) => void): void;
        removeIf(filter: (p1: any) => boolean): boolean;
        constructor(ignored?: any, alsoIgnored?: any);
        /**
         *
         * @return {*}
         */
        clone(): any;
    }
}
declare namespace java.util {
    /**
     * Implementation of Map interface based on a hash table. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/HashMap.html">[Sun
     * docs]</a>
     *
     * @param <K> key type
     * @param <V> value type
     * @param {number} ignored
     * @param {number} alsoIgnored
     * @class
     * @extends java.util.AbstractHashMap
     */
    class HashMap<K, V> extends java.util.AbstractHashMap<K, V> implements java.lang.Cloneable, java.io.Serializable {
        /**
         * Ensures that RPC will consider type parameter K to be exposed. It will be
         * pruned by dead code elimination.
         */
        exposeKey: K;
        /**
         * Ensures that RPC will consider type parameter V to be exposed. It will be
         * pruned by dead code elimination.
         */
        exposeValue: V;
        constructor(ignored?: any, alsoIgnored?: any);
        clone(): any;
        /**
         *
         * @param {*} value1
         * @param {*} value2
         * @return {boolean}
         */
        _equals(value1: any, value2: any): boolean;
        /**
         *
         * @param {*} key
         * @return {number}
         */
        getHashCode(key: any): number;
    }
}
declare namespace java.util {
    /**
     * Map using reference equality on keys. <a
     * href="http://java.sun.com/j2se/1.5.0/docs/api/java/util/IdentityHashMap.html">[Sun
     * docs]</a>
     *
     * @param <K> key type
     * @param <V> value type
     * @param {number} ignored
     * @class
     * @extends java.util.AbstractHashMap
     */
    class IdentityHashMap<K, V> extends java.util.AbstractHashMap<K, V> implements java.util.Map<K, V>, java.lang.Cloneable, java.io.Serializable {
        merge(key: any, value: any, map: (p1: any, p2: any) => any): any;
        computeIfAbsent(key: any, mappingFunction: (p1: any) => any): any;
        /**
         * Ensures that RPC will consider type parameter K to be exposed. It will be
         * pruned by dead code elimination.
         */
        exposeKey: K;
        /**
         * Ensures that RPC will consider type parameter V to be exposed. It will be
         * pruned by dead code elimination.
         */
        exposeValue: V;
        constructor(toBeCopied?: any);
        clone(): any;
        /**
         *
         * @param {*} obj
         * @return {boolean}
         */
        equals(obj: any): boolean;
        /**
         *
         * @return {number}
         */
        hashCode(): number;
        /**
         *
         * @param {*} value1
         * @param {*} value2
         * @return {boolean}
         */
        _equals(value1: any, value2: any): boolean;
        /**
         *
         * @param {*} key
         * @return {number}
         */
        getHashCode(key: any): number;
    }
}
declare namespace java.util {
    /**
     * Implements a TreeMap using a red-black tree. This guarantees O(log n)
     * performance on lookups, inserts, and deletes while maintaining linear
     * in-order traversal time. Null keys and values are fully supported if the
     * comparator supports them (the default comparator does not).
     *
     * @param <K> key type
     * @param <V> value type
     * @param {*} c
     * @class
     * @extends java.util.AbstractNavigableMap
     */
    class TreeMap<K, V> extends java.util.AbstractNavigableMap<K, V> implements java.io.Serializable {
        static SubMapType_All: TreeMap.SubMapType;
        static SubMapType_All_$LI$(): TreeMap.SubMapType;
        static SubMapType_Head: TreeMap.SubMapType;
        static SubMapType_Head_$LI$(): TreeMap.SubMapType;
        static SubMapType_Range: TreeMap.SubMapType;
        static SubMapType_Range_$LI$(): TreeMap.SubMapType;
        static SubMapType_Tail: TreeMap.SubMapType;
        static SubMapType_Tail_$LI$(): TreeMap.SubMapType;
        static LEFT: number;
        static RIGHT: number;
        static otherChild(child: number): number;
        cmp: java.util.Comparator<any>;
        exposeKeyType: K;
        exposeValueType: V;
        root: TreeMap.Node<K, V>;
        __size: number;
        constructor(c?: any);
        /**
         *
         */
        clear(): void;
        /**
         *
         * @return {*}
         */
        comparator(): java.util.Comparator<any>;
        /**
         *
         * @return {*}
         */
        entrySet(): java.util.Set<Map.Entry<K, V>>;
        headMap$java_lang_Object$boolean(toKey: K, inclusive: boolean): java.util.NavigableMap<K, V>;
        /**
         *
         * @param {*} toKey
         * @param {boolean} inclusive
         * @return {*}
         */
        headMap(toKey?: any, inclusive?: any): any;
        /**
         *
         * @param {*} key
         * @param {*} value
         * @return {*}
         */
        put(key: K, value: V): V;
        /**
         *
         * @param {*} k
         * @return {*}
         */
        remove(k: any): V;
        /**
         *
         * @return {number}
         */
        size(): number;
        subMap$java_lang_Object$boolean$java_lang_Object$boolean(fromKey: K, fromInclusive: boolean, toKey: K, toInclusive: boolean): java.util.NavigableMap<K, V>;
        /**
         *
         * @param {*} fromKey
         * @param {boolean} fromInclusive
         * @param {*} toKey
         * @param {boolean} toInclusive
         * @return {*}
         */
        subMap(fromKey?: any, fromInclusive?: any, toKey?: any, toInclusive?: any): any;
        tailMap$java_lang_Object$boolean(fromKey: K, inclusive: boolean): java.util.NavigableMap<K, V>;
        /**
         *
         * @param {*} fromKey
         * @param {boolean} inclusive
         * @return {*}
         */
        tailMap(fromKey?: any, inclusive?: any): any;
        /**
         * Returns the first node which compares greater than the given key.
         *
         * @param {*} key the key to search for
         * @return {java.util.TreeMap.Node} the next node, or null if there is none
         * @param {boolean} inclusive
         * @private
         */
        getNodeAfter(key: K, inclusive: boolean): TreeMap.Node<K, V>;
        /**
         * Returns the last node which is strictly less than the given key.
         *
         * @param {*} key the key to search for
         * @return {java.util.TreeMap.Node} the previous node, or null if there is none
         * @param {boolean} inclusive
         * @private
         */
        getNodeBefore(key: K, inclusive: boolean): TreeMap.Node<K, V>;
        assertCorrectness$(): void;
        /**
         *
         * @return {*}
         */
        descendingEntryIterator(): java.util.Iterator<Map.Entry<K, V>>;
        /**
         *
         * @return {*}
         */
        entryIterator(): java.util.Iterator<Map.Entry<K, V>>;
        assertCorrectness$java_util_TreeMap_Node$boolean(tree: TreeMap.Node<K, V>, isRed: boolean): number;
        /**
         * Internal helper function for public {@link #assertCorrectness()}.
         *
         * @param {java.util.TreeMap.Node} tree the subtree to validate.
         * @param {boolean} isRed true if the parent of this node is red.
         * @return {number} the black height of this subtree.
         * @throws RuntimeException if this RB-tree is not valid.
         * @private
         */
        assertCorrectness(tree?: any, isRed?: any): any;
        /**
         * Finds an entry given a key and returns the node.
         *
         * @param {*} key the search key
         * @return {*} the node matching the key or null
         */
        getEntry(key: K): Map.Entry<K, V>;
        /**
         * Returns the left-most node of the tree, or null if empty.
         * @return {*}
         */
        getFirstEntry(): Map.Entry<K, V>;
        /**
         * Returns the right-most node of the tree, or null if empty.
         * @return {*}
         */
        getLastEntry(): Map.Entry<K, V>;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        getCeilingEntry(key: K): Map.Entry<K, V>;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        getFloorEntry(key: K): Map.Entry<K, V>;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        getHigherEntry(key: K): Map.Entry<K, V>;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        getLowerEntry(key: K): Map.Entry<K, V>;
        /**
         *
         * @param {*} entry
         * @return {boolean}
         */
        removeEntry(entry: Map.Entry<K, V>): boolean;
        inOrderAdd(list: java.util.List<Map.Entry<K, V>>, type: TreeMap.SubMapType, current: TreeMap.Node<K, V>, fromKey: K, fromInclusive: boolean, toKey: K, toInclusive: boolean): void;
        inRange(type: TreeMap.SubMapType, key: K, fromKey: K, fromInclusive: boolean, toKey: K, toInclusive: boolean): boolean;
        /**
         * Insert a node into a subtree, collecting state about the insertion.
         *
         * If the same key already exists, the value of the node is overwritten with
         * the value from the new node instead.
         *
         * @param {java.util.TreeMap.Node} tree subtree to insert into
         * @param {java.util.TreeMap.Node} newNode new node to insert
         * @param {java.util.TreeMap.State} state result of the insertion: state.found true if the key already
         * existed in the tree state.value the old value if the key existed
         * @return {java.util.TreeMap.Node} the new subtree root
         * @private
         */
        insert(tree: TreeMap.Node<K, V>, newNode: TreeMap.Node<K, V>, state: TreeMap.State<V>): TreeMap.Node<K, V>;
        /**
         * Returns true if <code>node</code> is red. Note that null pointers are
         * considered black.
         * @param {java.util.TreeMap.Node} node
         * @return {boolean}
         * @private
         */
        isRed(node: TreeMap.Node<K, V>): boolean;
        /**
         * Returns true if <code>a</code> is greater than or equal to <code>b</code>.
         * @param {*} a
         * @param {*} b
         * @param {boolean} orEqual
         * @return {boolean}
         * @private
         */
        larger(a: K, b: K, orEqual: boolean): boolean;
        /**
         * Returns true if <code>a</code> is less than or equal to <code>b</code>.
         * @param {*} a
         * @param {*} b
         * @param {boolean} orEqual
         * @return {boolean}
         * @private
         */
        smaller(a: K, b: K, orEqual: boolean): boolean;
        /**
         * Remove a key from the tree, returning whether it was found and its value.
         *
         * @param {*} key key to remove
         * @param {java.util.TreeMap.State} state return state, not null
         * @return {boolean} true if the value was found
         * @private
         */
        removeWithState(key: K, state: TreeMap.State<V>): boolean;
        /**
         * replace 'node' with 'newNode' in the tree rooted at 'head'. Could have
         * avoided this traversal if each node maintained a parent pointer.
         * @param {java.util.TreeMap.Node} head
         * @param {java.util.TreeMap.Node} node
         * @param {java.util.TreeMap.Node} newNode
         * @private
         */
        replaceNode(head: TreeMap.Node<K, V>, node: TreeMap.Node<K, V>, newNode: TreeMap.Node<K, V>): void;
        /**
         * Perform a double rotation, first rotating the child which will become the
         * root in the opposite direction, then rotating the root in the specified
         * direction.
         *
         * <pre>
         * A                                               F
         * B   C    becomes (with rotateDirection=0)       A   C
         * D E F G                                         B E   G
         * D
         * </pre>
         *
         * @param {java.util.TreeMap.Node} tree root of the subtree to rotate
         * @param {number} rotateDirection the direction to rotate: 0=left, 1=right
         * @return {java.util.TreeMap.Node} the new root of the rotated subtree
         * @private
         */
        rotateDouble(tree: TreeMap.Node<K, V>, rotateDirection: number): TreeMap.Node<K, V>;
        /**
         * Perform a single rotation, pushing the root of the subtree to the specified
         * direction.
         *
         * <pre>
         * A                                              B
         * B   C     becomes (with rotateDirection=1)     D   A
         * D E                                              E   C
         * </pre>
         *
         * @param {java.util.TreeMap.Node} tree the root of the subtree to rotate
         * @param {number} rotateDirection the direction to rotate: 0=left rotation, 1=right
         * @return {java.util.TreeMap.Node} the new root of the rotated subtree
         * @private
         */
        rotateSingle(tree: TreeMap.Node<K, V>, rotateDirection: number): TreeMap.Node<K, V>;
    }
    namespace TreeMap {
        /**
         * Create an iterator which may return only a restricted range.
         *
         * @param {*} fromKey the first key to return in the iterator.
         * @param {*} toKey the upper bound of keys to return.
         * @param {java.util.TreeMap.SubMapType} type
         * @param {boolean} fromInclusive
         * @param {boolean} toInclusive
         * @class
         */
        class DescendingEntryIterator implements java.util.Iterator<Map.Entry<any, any>> {
            __parent: any;
            forEachRemaining(consumer: (p1: any) => void): void;
            iter: java.util.ListIterator<Map.Entry<any, any>>;
            last: Map.Entry<any, any>;
            constructor(__parent: any, type?: any, fromKey?: any, fromInclusive?: any, toKey?: any, toInclusive?: any);
            /**
             *
             * @return {boolean}
             */
            hasNext(): boolean;
            /**
             *
             * @return {*}
             */
            next(): Map.Entry<any, any>;
            /**
             *
             */
            remove(): void;
        }
        /**
         * Create an iterator which may return only a restricted range.
         *
         * @param {*} fromKey the first key to return in the iterator.
         * @param {*} toKey the upper bound of keys to return.
         * @param {java.util.TreeMap.SubMapType} type
         * @param {boolean} fromInclusive
         * @param {boolean} toInclusive
         * @class
         */
        class EntryIterator implements java.util.Iterator<Map.Entry<any, any>> {
            __parent: any;
            forEachRemaining(consumer: (p1: any) => void): void;
            iter: java.util.ListIterator<Map.Entry<any, any>>;
            last: Map.Entry<any, any>;
            constructor(__parent: any, type?: any, fromKey?: any, fromInclusive?: any, toKey?: any, toInclusive?: any);
            /**
             *
             * @return {boolean}
             */
            hasNext(): boolean;
            /**
             *
             * @return {*}
             */
            next(): Map.Entry<any, any>;
            /**
             *
             */
            remove(): void;
        }
        class __java_util_TreeMap_EntrySet extends java.util.AbstractNavigableMap.EntrySet {
            __parent: any;
            /**
             *
             */
            clear(): void;
            constructor(__parent: any);
        }
        /**
         * Create a node of the specified color.
         *
         * @param {*} key
         * @param {*} value
         * @param {boolean} isRed true if this should be a red node, false for black
         * @class
         * @extends java.util.AbstractMap.SimpleEntry
         */
        class Node<K, V> extends AbstractMap.SimpleEntry<K, V> {
            child: TreeMap.Node<K, V>[];
            isRed: boolean;
            constructor(key?: any, value?: any, isRed?: any);
        }
        /**
         * A state object which is passed down the tree for both insert and remove.
         * All uses make use of the done flag to indicate when no further rebalancing
         * of the tree is required. Remove methods use the found flag to indicate when
         * the desired key has been found. value is used both to return the value of a
         * removed node as well as to pass in a value which must match (used for
         * entrySet().remove(entry)), and the matchValue flag is used to request this
         * behavior.
         *
         * @param <V> value type
         * @class
         */
        class State<V> {
            done: boolean;
            found: boolean;
            matchValue: boolean;
            value: V;
            /**
             *
             * @return {string}
             */
            toString(): string;
            constructor();
        }
        class SubMap extends java.util.AbstractNavigableMap<any, any> {
            __parent: any;
            fromInclusive: boolean;
            fromKey: any;
            toInclusive: boolean;
            toKey: any;
            type: TreeMap.SubMapType;
            constructor(__parent: any, type: TreeMap.SubMapType, fromKey: any, fromInclusive: boolean, toKey: any, toInclusive: boolean);
            /**
             *
             * @return {*}
             */
            comparator(): java.util.Comparator<any>;
            /**
             *
             * @return {*}
             */
            entrySet(): java.util.Set<Map.Entry<any, any>>;
            headMap$java_lang_Object$boolean(toKey: any, toInclusive: boolean): java.util.NavigableMap<any, any>;
            /**
             *
             * @param {*} toKey
             * @param {boolean} toInclusive
             * @return {*}
             */
            headMap(toKey?: any, toInclusive?: any): any;
            /**
             *
             * @return {boolean}
             */
            isEmpty(): boolean;
            /**
             *
             * @param {*} key
             * @param {*} value
             * @return {*}
             */
            put(key: any, value: any): any;
            /**
             *
             * @param {*} k
             * @return {*}
             */
            remove(k: any): any;
            /**
             *
             * @return {number}
             */
            size(): number;
            subMap$java_lang_Object$boolean$java_lang_Object$boolean(newFromKey: any, newFromInclusive: boolean, newToKey: any, newToInclusive: boolean): java.util.NavigableMap<any, any>;
            /**
             *
             * @param {*} newFromKey
             * @param {boolean} newFromInclusive
             * @param {*} newToKey
             * @param {boolean} newToInclusive
             * @return {*}
             */
            subMap(newFromKey?: any, newFromInclusive?: any, newToKey?: any, newToInclusive?: any): any;
            tailMap$java_lang_Object$boolean(fromKey: any, fromInclusive: boolean): java.util.NavigableMap<any, any>;
            /**
             *
             * @param {*} fromKey
             * @param {boolean} fromInclusive
             * @return {*}
             */
            tailMap(fromKey?: any, fromInclusive?: any): any;
            /**
             *
             * @return {*}
             */
            descendingEntryIterator(): java.util.Iterator<Map.Entry<any, any>>;
            /**
             *
             * @return {*}
             */
            entryIterator(): java.util.Iterator<Map.Entry<any, any>>;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            getEntry(key: any): Map.Entry<any, any>;
            /**
             *
             * @return {*}
             */
            getFirstEntry(): Map.Entry<any, any>;
            /**
             *
             * @return {*}
             */
            getLastEntry(): Map.Entry<any, any>;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            getCeilingEntry(key: any): Map.Entry<any, any>;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            getFloorEntry(key: any): Map.Entry<any, any>;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            getHigherEntry(key: any): Map.Entry<any, any>;
            /**
             *
             * @param {*} key
             * @return {*}
             */
            getLowerEntry(key: any): Map.Entry<any, any>;
            /**
             *
             * @param {*} entry
             * @return {boolean}
             */
            removeEntry(entry: Map.Entry<any, any>): boolean;
            guardInRange(entry: Map.Entry<any, any>): Map.Entry<any, any>;
            inRange(key: any): boolean;
        }
        namespace SubMap {
            class SubMap$0 extends TreeMap.SubMap.EntrySet {
                __parent: any;
                /**
                 *
                 * @return {boolean}
                 */
                isEmpty(): boolean;
                constructor(__parent: any);
            }
        }
        class SubMapType {
            /**
             * Returns true if this submap type uses a from-key.
             * @return {boolean}
             */
            fromKeyValid(): boolean;
            /**
             * Returns true if this submap type uses a to-key.
             * @return {boolean}
             */
            toKeyValid(): boolean;
            constructor();
        }
        class SubMapTypeHead extends TreeMap.SubMapType {
            /**
             *
             * @return {boolean}
             */
            toKeyValid(): boolean;
            constructor();
        }
        class SubMapTypeRange extends TreeMap.SubMapType {
            /**
             *
             * @return {boolean}
             */
            fromKeyValid(): boolean;
            /**
             *
             * @return {boolean}
             */
            toKeyValid(): boolean;
            constructor();
        }
        class SubMapTypeTail extends TreeMap.SubMapType {
            /**
             *
             * @return {boolean}
             */
            fromKeyValid(): boolean;
            constructor();
        }
    }
}
declare namespace java.lang {
    class System {
        static __static_initialized: boolean;
        static __static_initialize(): void;
        static ENVIRONMENT_IS_WEB: boolean;
        static ENVIRONMENT_IS_WEB_$LI$(): boolean;
        static ENVIRONMENT_IS_WORKER: boolean;
        static ENVIRONMENT_IS_WORKER_$LI$(): boolean;
        static ENVIRONMENT_IS_NODE: boolean;
        static ENVIRONMENT_IS_NODE_$LI$(): boolean;
        static ENVIRONMENT_IS_SHELL: boolean;
        static ENVIRONMENT_IS_SHELL_$LI$(): boolean;
        static propertyMap: java.util.Map<string, string>;
        static propertyMap_$LI$(): java.util.Map<string, string>;
        static err: java.io.PrintStream;
        static err_$LI$(): java.io.PrintStream;
        static out: java.io.PrintStream;
        static out_$LI$(): java.io.PrintStream;
        static in: java.io.InputStream;
        static in_$LI$(): java.io.InputStream;
        static __static_initializer_0(): void;
        static arraycopy(src: any, srcOfs: number, dest: any, destOfs: number, len: number): void;
        static currentTimeMillis(): number;
        static gc(): void;
        static getProperty$java_lang_String(key: string): string;
        static getProperty$java_lang_String$java_lang_String(key: string, def: string): string;
        static getProperty(key?: any, def?: any): any;
        static identityHashCode(o: any): number;
        static setErr(err: java.io.PrintStream): void;
        static setOut(out: java.io.PrintStream): void;
        static lineSeparator(): string;
        static exit(status: number): void;
    }
    namespace System {
        class System$0 extends java.io.OutputStream {
            sep: string;
            toOut: string;
            write(buffer?: any, offset?: any, count?: any): any;
            write$int(i: number): void;
            /**
             *
             */
            flush(): void;
            constructor();
        }
        class System$1 extends java.io.OutputStream {
            sep: string;
            toOut: string;
            write(buffer?: any, offset?: any, count?: any): any;
            write$int(i: number): void;
            /**
             *
             */
            flush(): void;
            constructor();
        }
        class System$2 extends java.io.InputStream {
            readData: string[];
            where: number;
            sep: string;
            readerFunction: () => string;
            read(buffer?: any, byteOffset?: any, byteCount?: any): any;
            read$(): number;
            constructor();
        }
    }
}
declare namespace java.util {
    class Hashtable<K, V> extends java.util.HashMap<K, V> implements java.util.Dictionary<K, V> {
        static serialVersionUID: number;
        constructor(ignored?: any, alsoIgnored?: any);
        keys(): java.util.Enumeration<K>;
        elements(): java.util.Enumeration<V>;
    }
    namespace Hashtable {
        class Hashtable$0 implements java.util.Enumeration<any> {
            private it;
            __parent: any;
            /**
             *
             * @return {boolean}
             */
            hasMoreElements(): boolean;
            /**
             *
             * @return {*}
             */
            nextElement(): any;
            constructor(__parent: any, it: any);
        }
        class Hashtable$1 implements java.util.Enumeration<any> {
            private it;
            __parent: any;
            /**
             *
             * @return {boolean}
             */
            hasMoreElements(): boolean;
            /**
             *
             * @return {*}
             */
            nextElement(): any;
            constructor(__parent: any, it: any);
        }
    }
}
declare namespace java.util {
    /**
     * Hash table implementation of the Map interface with predictable iteration
     * order. <a href=
     * "http://java.sun.com/j2se/1.5.0/docs/api/java/util/LinkedHashMap.html">[Sun
     * docs]</a>
     *
     * @param <K>
     * key type.
     * @param <V>
     * value type.
     * @param {number} ignored
     * @param {number} alsoIgnored
     * @param {boolean} accessOrder
     * @class
     * @extends java.util.HashMap
     */
    class LinkedHashMap<K, V> extends java.util.HashMap<K, V> implements java.util.Map<K, V> {
        merge(key: any, value: any, map: (p1: any, p2: any) => any): any;
        computeIfAbsent(key: any, mappingFunction: (p1: any) => any): any;
        accessOrder: boolean;
        head: LinkedHashMap.ChainEntry;
        map: java.util.HashMap<K, LinkedHashMap.ChainEntry>;
        constructor(ignored?: any, alsoIgnored?: any, accessOrder?: any);
        /**
         *
         */
        clear(): void;
        resetChainEntries(): void;
        /**
         *
         * @return {*}
         */
        clone(): any;
        /**
         *
         * @param {*} key
         * @return {boolean}
         */
        containsKey(key: any): boolean;
        /**
         *
         * @param {*} value
         * @return {boolean}
         */
        containsValue(value: any): boolean;
        /**
         *
         * @return {*}
         */
        entrySet(): java.util.Set<java.util.Map.Entry<K, V>>;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        get(key: any): V;
        /**
         *
         * @param {*} key
         * @param {*} value
         * @return {*}
         */
        put(key: K, value: V): V;
        /**
         *
         * @param {*} key
         * @return {*}
         */
        remove(key: any): V;
        /**
         *
         * @return {number}
         */
        size(): number;
        removeEldestEntry(eldest: java.util.Map.Entry<K, V>): boolean;
        recordAccess(entry: LinkedHashMap.ChainEntry): void;
    }
    namespace LinkedHashMap {
        /**
         * The entry we use includes next/prev pointers for a doubly-linked circular
         * list with a head node. This reduces the special cases we have to deal
         * with in the list operations.
         *
         * Note that we duplicate the key from the underlying hash map so we can
         * find the eldest entry. The alternative would have been to modify HashMap
         * so more of the code was directly usable here, but this would have added
         * some overhead to HashMap, or to reimplement most of the HashMap code here
         * with small modifications. Paying a small storage cost only if you use
         * LinkedHashMap and minimizing code size seemed like a better tradeoff
         * @param {*} key
         * @param {*} value
         * @class
         * @extends java.util.AbstractMap.SimpleEntry
         */
        class ChainEntry extends AbstractMap.SimpleEntry<any, any> {
            __parent: any;
            next: LinkedHashMap.ChainEntry;
            prev: LinkedHashMap.ChainEntry;
            constructor(__parent: any, key?: any, value?: any);
            /**
             * Add this node to the end of the chain.
             */
            addToEnd(): void;
            /**
             * Remove this node from any list it may be a part of.
             */
            remove(): void;
        }
        class __java_util_LinkedHashMap_EntrySet extends java.util.AbstractSet<java.util.Map.Entry<any, any>> {
            __parent: any;
            /**
             *
             */
            clear(): void;
            /**
             *
             * @param {*} o
             * @return {boolean}
             */
            contains(o: any): boolean;
            /**
             *
             * @return {*}
             */
            iterator(): java.util.Iterator<java.util.Map.Entry<any, any>>;
            /**
             *
             * @param {*} entry
             * @return {boolean}
             */
            remove(entry: any): boolean;
            /**
             *
             * @return {number}
             */
            size(): number;
            constructor(__parent: any);
        }
        namespace __java_util_LinkedHashMap_EntrySet {
            class EntryIterator implements java.util.Iterator<java.util.Map.Entry<any, any>> {
                __parent: any;
                forEachRemaining(consumer: (p1: any) => void): void;
                last: LinkedHashMap.ChainEntry;
                __next: LinkedHashMap.ChainEntry;
                constructor(__parent: any);
                /**
                 *
                 * @return {boolean}
                 */
                hasNext(): boolean;
                /**
                 *
                 * @return {*}
                 */
                next(): java.util.Map.Entry<any, any>;
                /**
                 *
                 */
                remove(): void;
            }
        }
    }
}
declare namespace java.nio.file {
    class Path implements java.lang.Comparable<Path>, java.lang.Iterable<Path> {
        forEach(action: (p1: any) => void): void;
        static PATH_SEPARATOR: string;
        static PATH_SEPARATOR_$LI$(): string;
        fullPath: string;
        constructor(fullPath: string);
        compareTo$java_nio_file_Path(path: Path): number;
        /**
         *
         * @param {java.nio.file.Path} path
         * @return {number}
         */
        compareTo(path?: any): any;
        isAbsolute(): boolean;
        getFileName(): Path;
        getParent(): Path;
        resolve$java_nio_file_Path(other: Path): Path;
        resolve(other?: any): any;
        resolve$java_lang_String(other: string): Path;
        toAbsolutePath(): Path;
        /**
         *
         * @return {*}
         */
        iterator(): java.util.Iterator<Path>;
        /**
         *
         * @return {string}
         */
        toString(): string;
    }
}
declare namespace java.util {
    /**
     * A helper to detect concurrent modifications to collections. This is implemented as a helper
     * utility so that we could remove the checks easily by a flag.
     * @class
     */
    class ConcurrentModificationDetector {
        static API_CHECK: boolean;
        static API_CHECK_$LI$(): boolean;
        static MOD_COUNT_PROPERTY: string;
        static structureChanged(map: any): void;
        static recordLastKnownStructure(host: any, iterator: java.util.Iterator<any>): void;
        static checkStructuralChange(host: any, iterator: java.util.Iterator<any>): void;
    }
}
declare namespace java.util.logging {
    /**
     * An emulation of the java.util.logging.Logger class. See
     * <a href="http://java.sun.com/j2se/1.4.2/docs/api/java/util/logging/Logger.html">
     * The Java API doc for details</a>
     * @class
     */
    class Logger {
        static __static_initialized: boolean;
        static __static_initialize(): void;
        static GLOBAL_LOGGER_NAME: string;
        static LOGGING_ENABLED: string;
        static LOGGING_ENABLED_$LI$(): string;
        static LOGGING_WARNING: boolean;
        static LOGGING_WARNING_$LI$(): boolean;
        static LOGGING_SEVERE: boolean;
        static LOGGING_SEVERE_$LI$(): boolean;
        static LOGGING_FALSE: boolean;
        static LOGGING_FALSE_$LI$(): boolean;
        static __static_initializer_0(): void;
        static getGlobal(): Logger;
        static getLogger(name: string): Logger;
        static assertLoggingValues(): void;
        handlers: java.util.List<java.util.logging.Handler>;
        level: java.util.logging.Level;
        name: string;
        parent: Logger;
        useParentHandlers: boolean;
        constructor(name: string, resourceName: string);
        addHandler(handler: java.util.logging.Handler): void;
        config(msg: string): void;
        fine(msg: string): void;
        finer(msg: string): void;
        finest(msg: string): void;
        info(msg: string): void;
        warning(msg: string): void;
        severe(msg: string): void;
        getHandlers(): java.util.logging.Handler[];
        getLevel(): java.util.logging.Level;
        getName(): string;
        getParent(): Logger;
        getUseParentHandlers(): boolean;
        isLoggable(messageLevel: java.util.logging.Level): boolean;
        log$java_util_logging_Level$java_lang_String(level: java.util.logging.Level, msg: string): void;
        log$java_util_logging_Level$java_lang_String$java_lang_Throwable(level: java.util.logging.Level, msg: string, thrown: Error): void;
        log(level?: any, msg?: any, thrown?: any): any;
        log$java_util_logging_LogRecord(record: java.util.logging.LogRecord): void;
        removeHandler(handler: java.util.logging.Handler): void;
        setLevel(newLevel: java.util.logging.Level): void;
        setParent(newParent: Logger): void;
        setUseParentHandlers(newUseParentHandlers: boolean): void;
        getEffectiveLevel(): java.util.logging.Level;
        actuallyLog$java_util_logging_Level$java_lang_String$java_lang_Throwable(level: java.util.logging.Level, msg: string, thrown: Error): void;
        actuallyLog(level?: any, msg?: any, thrown?: any): any;
        actuallyLog$java_util_logging_LogRecord(record: java.util.logging.LogRecord): void;
    }
}
declare namespace javaemul.internal {
    /**
     * A utility class that provides utility functions to do precondition checks inside GWT-SDK.
     * @class
     */
    class InternalPreconditions {
        static CHECKED_MODE: boolean;
        static CHECKED_MODE_$LI$(): boolean;
        static TYPE_CHECK: boolean;
        static TYPE_CHECK_$LI$(): boolean;
        static API_CHECK: boolean;
        static API_CHECK_$LI$(): boolean;
        static BOUND_CHECK: boolean;
        static BOUND_CHECK_$LI$(): boolean;
        static checkType(expression: boolean): void;
        static checkCriticalType(expression: boolean): void;
        static checkArrayType$boolean(expression: boolean): void;
        static checkCriticalArrayType$boolean(expression: boolean): void;
        static checkArrayType$boolean$java_lang_Object(expression: boolean, errorMessage: any): void;
        /**
         * Ensures the truth of an expression that verifies array type.
         * @param {boolean} expression
         * @param {*} errorMessage
         */
        static checkArrayType(expression?: any, errorMessage?: any): any;
        static checkCriticalArrayType$boolean$java_lang_Object(expression: boolean, errorMessage: any): void;
        static checkCriticalArrayType(expression?: any, errorMessage?: any): any;
        static checkElement$boolean(expression: boolean): void;
        static checkCriticalElement$boolean(expression: boolean): void;
        static checkElement$boolean$java_lang_Object(expression: boolean, errorMessage: any): void;
        /**
         * Ensures the truth of an expression involving existence of an element.
         * @param {boolean} expression
         * @param {*} errorMessage
         */
        static checkElement(expression?: any, errorMessage?: any): any;
        static checkCriticalElement$boolean$java_lang_Object(expression: boolean, errorMessage: any): void;
        /**
         * Ensures the truth of an expression involving existence of an element.
         * <p>
         * For cases where failing fast is pretty important and not failing early could cause bugs that
         * are much harder to debug.
         * @param {boolean} expression
         * @param {*} errorMessage
         */
        static checkCriticalElement(expression?: any, errorMessage?: any): any;
        static checkArgument$boolean(expression: boolean): void;
        static checkCriticalArgument$boolean(expression: boolean): void;
        static checkArgument$boolean$java_lang_Object(expression: boolean, errorMessage: any): void;
        static checkCriticalArgument$boolean$java_lang_Object(expression: boolean, errorMessage: any): void;
        static checkArgument$boolean$java_lang_String$java_lang_Object_A(expression: boolean, errorMessageTemplate: string, ...errorMessageArgs: any[]): void;
        /**
         * Ensures the truth of an expression involving one or more parameters to the calling method.
         * @param {boolean} expression
         * @param {string} errorMessageTemplate
         * @param {Array} errorMessageArgs
         */
        static checkArgument(expression?: any, errorMessageTemplate?: any, ...errorMessageArgs: any[]): any;
        static checkCriticalArgument$boolean$java_lang_String$java_lang_Object_A(expression: boolean, errorMessageTemplate: string, ...errorMessageArgs: any[]): void;
        /**
         * Ensures the truth of an expression involving one or more parameters to the calling method.
         * <p>
         * For cases where failing fast is pretty important and not failing early could cause bugs that
         * are much harder to debug.
         * @param {boolean} expression
         * @param {string} errorMessageTemplate
         * @param {Array} errorMessageArgs
         */
        static checkCriticalArgument(expression?: any, errorMessageTemplate?: any, ...errorMessageArgs: any[]): any;
        static checkState$boolean(expression: boolean): void;
        /**
         * Ensures the truth of an expression involving the state of the calling instance, but not
         * involving any parameters to the calling method.
         * <p>
         * For cases where failing fast is pretty important and not failing early could cause bugs that
         * are much harder to debug.
         * @param {boolean} expression
         */
        static checkCritcalState(expression: boolean): void;
        static checkState$boolean$java_lang_Object(expression: boolean, errorMessage: any): void;
        /**
         * Ensures the truth of an expression involving the state of the calling instance, but not
         * involving any parameters to the calling method.
         * @param {boolean} expression
         * @param {*} errorMessage
         */
        static checkState(expression?: any, errorMessage?: any): any;
        /**
         * Ensures the truth of an expression involving the state of the calling instance, but not
         * involving any parameters to the calling method.
         * @param {boolean} expression
         * @param {*} errorMessage
         */
        static checkCriticalState(expression: boolean, errorMessage: any): void;
        static checkNotNull$java_lang_Object<T>(reference: T): T;
        static checkCriticalNotNull$java_lang_Object<T>(reference: T): T;
        static checkNotNull$java_lang_Object$java_lang_Object(reference: any, errorMessage: any): void;
        /**
         * Ensures that an object reference passed as a parameter to the calling method is not null.
         * @param {*} reference
         * @param {*} errorMessage
         */
        static checkNotNull(reference?: any, errorMessage?: any): any;
        static checkCriticalNotNull$java_lang_Object$java_lang_Object(reference: any, errorMessage: any): void;
        static checkCriticalNotNull(reference?: any, errorMessage?: any): any;
        /**
         * Ensures that {@code size} specifies a valid array size (i.e. non-negative).
         * @param {number} size
         */
        static checkArraySize(size: number): void;
        static checkCriticalArraySize(size: number): void;
        /**
         * Ensures that {@code index} specifies a valid <i>element</i> in an array, list or string of size
         * {@code size}. An element index may range from zero, inclusive, to {@code size}, exclusive.
         * @param {number} index
         * @param {number} size
         */
        static checkElementIndex(index: number, size: number): void;
        static checkCriticalElementIndex(index: number, size: number): void;
        /**
         * Ensures that {@code index} specifies a valid <i>position</i> in an array, list or string of
         * size {@code size}. A position index may range from zero to {@code size}, inclusive.
         * @param {number} index
         * @param {number} size
         */
        static checkPositionIndex(index: number, size: number): void;
        static checkCriticalPositionIndex(index: number, size: number): void;
        /**
         * Ensures that {@code start} and {@code end} specify a valid <i>positions</i> in an array, list
         * or string of size {@code size}, and are in order. A position index may range from zero to
         * {@code size}, inclusive.
         * @param {number} start
         * @param {number} end
         * @param {number} size
         */
        static checkPositionIndexes(start: number, end: number, size: number): void;
        /**
         * Ensures that {@code start} and {@code end} specify a valid <i>positions</i> in an array, list
         * or string of size {@code size}, and are in order. A position index may range from zero to
         * {@code size}, inclusive.
         * @param {number} start
         * @param {number} end
         * @param {number} size
         */
        static checkCriticalPositionIndexes(start: number, end: number, size: number): void;
        /**
         * Checks that bounds are correct.
         *
         * @throw StringIndexOutOfBoundsException if the range is not legal
         * @param {number} start
         * @param {number} end
         * @param {number} size
         */
        static checkStringBounds(start: number, end: number, size: number): void;
        /**
         * Substitutes each {@code %s} in {@code template} with an argument. These are matched by
         * position: the first {@code %s} gets {@code args[0]}, etc.  If there are more arguments than
         * placeholders, the unmatched arguments will be appended to the end of the formatted message in
         * square braces.
         * @param {string} template
         * @param {Array} args
         * @return {string}
         * @private
         */
        static format(template: string, ...args: any[]): string;
        constructor();
    }
}
