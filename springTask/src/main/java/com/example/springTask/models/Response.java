package com.example.springTask.models;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Response {

    private int code;
    private String body;

    public Response(int code, String body) {
        this.body = body;
        this.code = code;
    }
}
