package org.restheart.polyglot;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.GetRequest;
import com.mashape.unirest.request.HttpRequestWithBody;

public class UnirestWrapper {
    public UnirestWrapper() {
    }

    public GetRequest head(String url){
        return Unirest.head(url);
    }

    public HttpRequestWithBody options(String url){
        return Unirest.options(url);
    }

    public GetRequest get(String url){
        return Unirest.get(url);
    }

    public HttpRequestWithBody post(String url){
        return Unirest.post(url);
    }

    public HttpRequestWithBody put(String url){
        return Unirest.put(url);
    }

    public HttpRequestWithBody patch(String url){
        return Unirest.patch(url);
    }

    public HttpRequestWithBody delete(String url){
        return Unirest.delete(url);
    }
}
