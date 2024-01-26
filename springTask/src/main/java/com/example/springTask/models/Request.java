package com.example.springTask.models;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpMethod;

@Data
@Builder
public class Request {

    private String url;
    private String data;
    private String encoding;
    private HttpMethod method;

    public Request(String url, String data, String encoding, HttpMethod method) {
        this.url = url;
        this.data = data;
        this.encoding = encoding;
        this.method = method;
    }

//    public Request() {
//    }
//
//    public Request(String url, HttpMethod method) {
//        this.url = url;
//        this.method = method;
//    }

}