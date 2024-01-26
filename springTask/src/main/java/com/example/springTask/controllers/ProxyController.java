package com.example.springTask.controllers;

import com.example.springTask.configuration.Configuration;
import com.example.springTask.models.ProxyServer;
import com.example.springTask.models.Request;
import com.example.springTask.models.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Controller
public class ProxyController {

    private final List<ProxyServer> proxyServers;
    private final ScheduledExecutorService scheduledExecutorService;

    @Autowired
    public ProxyController() {
        String[] servers = {"23.230.251.237:12323:pack1use3:74de34eb34",
                "31.40.233.244:12323:pack1use3:74de34eb34",
                "5.183.198.248:12323:pack1use3:74de34eb34",
                "45.145.99.60:12323:pack1use3:74de34eb34",
                "185.242.108.74:12323:pack1use3:74de34eb34"};
        List<ProxyServer> proxyServers = new ArrayList<>();
        for (var proxyServer : servers) {
            String[] serverArray = proxyServer.split(":");
            proxyServers.add(new ProxyServer(serverArray[0], Integer.parseInt(serverArray[1]), serverArray[2], serverArray[3]));
        }
        this.proxyServers = proxyServers;
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    }

    public ProxyServer getAvailableProxyServer() {
        for (ProxyServer proxyServer : proxyServers) {
            if (proxyServer.isAvailable()) {
                return proxyServer;
            }
        }
        return null;
    }

    @GetMapping("/test")
    @ResponseBody
    public String test() {
        log("Testing request");
        markProxyAsUnavailable(proxyServers.get(1));
        return "Test";
    }

    @GetMapping(value = "/", produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> getProxyRequest(Request request) {
        request.setMethod(HttpMethod.GET);
        try {
            log(String.format("Incoming request: %s", request));
            Response response = doProxyRequest(request);
            log(String.format("Outgoing response: %s", response));
            return ResponseEntity.status(response.getCode()).body(response.getBody());
        } catch (IOException e) {
            Response response = new Response(500, "Error processing request: " + e.getMessage());
            log(String.format("Outgoing response: %s", response));
            return ResponseEntity.status(response.getCode()).body(response.getBody());
        }
    }

    @PostMapping(value = "/", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> postProxyRequest(@RequestParam String url, @RequestParam(required = false, defaultValue = "application/x-www-form-urlencoded") String encoding, @RequestParam String data) {
        Request request = new Request(url, data, encoding, HttpMethod.POST);
        log(String.format("Incoming request: %s", request));
        try {
            Response response = doProxyRequest(request);
            log(String.format("Outgoing response: %s", response));
            return ResponseEntity.status(response.getCode()).body(response.getBody());
        } catch (IOException e) {
            Response response = new Response(500, "Error processing request: " + e.getMessage());
            log(String.format("Outgoing response: %s", response));
            return ResponseEntity.status(response.getCode()).body(response.getBody());
        }
    }

    private Response doProxyRequest(Request request) throws IOException {
        ProxyServer proxyServer = getAvailableProxyServer();
        if (proxyServer == null) {
            return new Response(418, "No available proxy servers");
        }

        ExecutorService executor = Executors.newFixedThreadPool(20);
        Future<Response> futureResponse = executor.submit(() -> {
            try {
                return processRequest(request, proxyServer);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });

        Response response;
        try {
            response = futureResponse.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return new Response(500, "Error processing request: " + e.getMessage());
        }

        if (response.getCode() >= 500) {
            markProxyAsUnavailable(proxyServer);
        }
        return response;
    }

    private Response processRequest(Request request, ProxyServer proxyServer) throws IOException {
        int responseCode;
        HttpURLConnection connection;
        do {
            URL url = new URL(request.getUrl());
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyServer.getHost(), proxyServer.getPort()));
            connection = (HttpURLConnection) url.openConnection(proxy);
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(proxyServer.getUsername(), proxyServer.getPassword().toCharArray());
                }
            });
            connection.setRequestMethod(request.getMethod().name());
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.71 Safari/537.36");
            if (request.getMethod() == HttpMethod.POST) {
                String encoding = request.getEncoding() == null || request.getEncoding().isBlank()
                        ? "application/x-www-form-urlencoded" : request.getEncoding();
                connection.setRequestProperty("Content-Type", encoding);
                connection.setRequestProperty("Content-Length", String.valueOf(request.getData().length()));
                connection.setDoOutput(true);
                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(request.getData().getBytes());
                outputStream.flush();
            }

            responseCode = connection.getResponseCode();


            if (responseCode >= 300 && responseCode < 400) {
                var redirectedPage = connection.getHeaderField("Location");
                request.setUrl(redirectedPage);
                connection.disconnect();
            }
        }
        while (responseCode >= 300 && responseCode < 400);

        String responseBody;
        try {
            responseBody = readResponse(connection);
        } catch (IOException e) {
            responseBody = e.getMessage();
        }

        return new Response(responseCode, responseBody);
    }

    private void markProxyAsUnavailable(ProxyServer proxy) {
        proxy.setAvailable(false);
        log(String.format("Proxy %s on port %s is unavailable now", proxy.getHost(), proxy.getPort()));
        int duration = Configuration.getDurationInMinutes();
        scheduledExecutorService.schedule(() -> {
            proxy.setAvailable(true);
            log(String.format("Proxy %s on port %s is available now", proxy.getHost(), proxy.getPort()));
        }, duration, TimeUnit.MINUTES);
    }

    private String readResponse(HttpURLConnection connection) throws IOException {
        InputStream inputStream = connection.getInputStream();
        byte[] buffer = new byte[4096]; // Adjust the buffer size as needed
        StringBuilder responseBody = new StringBuilder();

        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            responseBody.append(new String(buffer, 0, bytesRead));
        }

        return responseBody.toString();
    }

    private void log (String logInfo) {
        System.out.println(logInfo);
    }
}
