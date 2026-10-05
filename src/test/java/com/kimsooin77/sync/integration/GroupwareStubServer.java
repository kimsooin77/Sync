package com.kimsooin77.sync.integration;

import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class GroupwareStubServer {

    private final ConcurrentHashMap<String, GroupwareAccount> accounts = new ConcurrentHashMap<>();
    private final Set<String> failingEmployeeNos = ConcurrentHashMap.newKeySet();
    private final HttpServer server;

    private GroupwareStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mock/groupware/accounts", exchange -> {
            try {
                GroupwareAccountRequest request = JsonMapper.builder().build()
                        .readValue(exchange.getRequestBody(), GroupwareAccountRequest.class);
                String path = exchange.getRequestURI().getPath();
                if (failingEmployeeNos.contains(request.employeeNo())) {
                    byte[] response = "internal Groupware details".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, response.length);
                    exchange.getResponseBody().write(response);
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod())) {
                    if (accounts.putIfAbsent(request.employeeNo(), GroupwareAccount.from(request, true)) != null) {
                        exchange.sendResponseHeaders(409, -1);
                    } else {
                        exchange.sendResponseHeaders(201, -1);
                    }
                } else if ("PUT".equals(exchange.getRequestMethod())) {
                    String employeeNo = path.substring(path.lastIndexOf('/') + 1);
                    if (!employeeNo.equals(request.employeeNo())) {
                        exchange.sendResponseHeaders(400, -1);
                    } else {
                        accounts.put(employeeNo, GroupwareAccount.from(request, true));
                        exchange.sendResponseHeaders(200, -1);
                    }
                } else if ("PATCH".equals(exchange.getRequestMethod()) && path.endsWith("/disable")) {
                    String employeeNo = path.substring("/mock/groupware/accounts/".length(),
                            path.length() - "/disable".length());
                    if (!employeeNo.equals(request.employeeNo())) {
                        exchange.sendResponseHeaders(400, -1);
                    } else {
                        GroupwareAccount existing = accounts.get(employeeNo);
                        if (existing == null) {
                            exchange.sendResponseHeaders(404, -1);
                        } else if (!existing.enabled()) {
                            exchange.sendResponseHeaders(200, -1);
                        } else {
                            accounts.put(employeeNo, GroupwareAccount.from(request, false));
                            exchange.sendResponseHeaders(200, -1);
                        }
                    }
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } catch (Exception failure) {
                exchange.sendResponseHeaders(400, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    static GroupwareStubServer start() {
        try {
            return new GroupwareStubServer();
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    GroupwareAccount find(String employeeNo) {
        return accounts.get(employeeNo);
    }

    void clear() {
        accounts.clear();
        failingEmployeeNos.clear();
    }

    void failFor(String employeeNo) {
        failingEmployeeNos.add(employeeNo);
    }

    void close() {
        server.stop(0);
    }
}
