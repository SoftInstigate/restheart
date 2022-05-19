# Form Handler Service

An example service with a custom `Request` to handle Form POST using undertow `FormParser`

For example:

```bash
$ http --form :8080/formHandler name=Andrea nickname=uji
```

Returns

```json
{
    "name": "Andrea",
    "nickname": "uji"
}
```