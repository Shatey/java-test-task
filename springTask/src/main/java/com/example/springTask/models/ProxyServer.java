package com.example.springTask.models;

import lombok.Data;

@Data
public class ProxyServer {
    private String host;
    private int port;
    private String username;
    private String password;
    private boolean isAvailable;

    public ProxyServer(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.isAvailable = true;
    }
}
