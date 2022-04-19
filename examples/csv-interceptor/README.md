# CSV Interceptor

This plugin contains two interceptors:

`coordsToGeoJson` intercepts requests to the `csvLoader` service and converts coordinates from CSV to a GeoJSON object. It is an example on how to transform data in a csv upload.

See [Upload CSV files](https://restheart.org/docs/csv/) on RESTHeart documentation for more information about this example.

`csvRepresentationTransformer` transforms responses of the `mongoService` in CSV format.

Example output with the `csvRepresentationTransformer` in action

```bash
$ http -b -a admin:secret :8080/coll?csv

_id,a,_etag,
{"$oid":"6202562ce5078606d08b79e2"},1,{"$oid":"6202562ce5078606d08b79e1"}
{"$oid":"62025626e5078606d08b79df"},1,{"$oid":"62025662e5078606d08b79e5"}
```