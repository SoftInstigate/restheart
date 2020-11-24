wrk.method = "POST"
wrk.body   = "{\"n\": 1, \"str\": 'foo'}"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["Authorization"] = "Basic YWRtaW46c2VjcmV0"