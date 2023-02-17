# x-headers-to-qparams

This interceptor allows to map headers to query parameters

If the request contains an header starting with `X-RH-` than it adds to the request a corresponding query parameter.

## Example

The MongoService handles the request `GET /coll?filter={"foo":"bar}` using the query parameter `filter` to execute the specified MongoDb query.

Using this plugin, you can pass the query using the request header `X-RH-filter`

```
GET /coll
X-RH-filter: {"foo":"bar}
```

## CORS

The interceptor adds all the headers starting with `X-RH-` in the request to the response header `Access-Control-Expose-Headers` for CORS support.

```
http -a admin:secret OPTIONS :8080/coll X-RH-filter:'{"a":1}'
HTTP/1.1 200 OK

Access-Control-Expose-Headers: Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge,X-RH-filter <----
(other headers omitted)
```