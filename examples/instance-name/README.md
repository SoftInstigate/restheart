# Instance Name

RESTHeart configuration file allows defining an instance name:

```yml
#### Instance name

 # The name of this instance.
 # Displayed in log, also allows to implement instance specific custom code

instance-name: default
```

This interceptor returns the instance name in the `X-Restheart-Instance` response header on requests handled by the `MongoService`.

## Test GET

```http
$ http -a admin:secret :8080/

HTTP/1.1 200 OK
(other headers omitted)
Content-Type: text/plain
Date: Mon, 13 Apr 2020 13:32:52 GMT
X-Powered-By: restheart.org
X-Restheart-Instance: default   <========

(response content omitted)
```