package com.kimsooin77.sync.integration;

import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

final class GroupwareStubServer {

    private final ConcurrentHashMap<String, GroupwareAccount> accounts = new ConcurrentHashMap<>();
    private final Set<String> failingEmployeeNos = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Integer> failureStatuses = new ConcurrentHashMap<>();
    private final Set<String> unrelatedNotFoundEmployeeNos = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<GroupwareAccountRequest>> requests =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> idempotencyKeys =
            new ConcurrentHashMap<>();
    private volatile CountDownLatch requestEntered;
    private volatile CountDownLatch releaseRequest;
    private final HttpServer server;

    private GroupwareStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/mock/groupware/accounts", exchange -> {
            try {
                GroupwareAccountRequest request = JsonMapper.builder().build()
                        .readValue(exchange.getRequestBody(), GroupwareAccountRequest.class);
                requests.computeIfAbsent(request.employeeNo(), ignored -> new CopyOnWriteArrayList<>()).add(request);
                idempotencyKeys.computeIfAbsent(request.employeeNo(), ignored -> new CopyOnWriteArrayList<>())
                        .add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                String path = exchange.getRequestURI().getPath();
                CountDownLatch entered = requestEntered;
                CountDownLatch release = releaseRequest;
                if (entered != null && release != null) {
                    entered.countDown();
                    release.await(10, TimeUnit.SECONDS);
                }
                if (failingEmployeeNos.contains(request.employeeNo())) {
                    byte[] response = "internal Groupware details".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(failureStatuses.getOrDefault(request.employeeNo(), 500), response.length);
                    exchange.getResponseBody().write(response);
                    return;
                }
                if (unrelatedNotFoundEmployeeNos.contains(request.employeeNo())) {
                    sendJson(exchange, 404, "{\"code\":\"ROUTE_NOT_FOUND\",\"message\":\"Not found\"}");
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
                            sendJson(exchange, 404,
                                    "{\"code\":\"ACCOUNT_NOT_FOUND\",\"message\":\"Groupware account was not found.\"}");
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
                try {
                    exchange.sendResponseHeaders(400, -1);
                } catch (IOException ignored) {
                    // The client may have closed the connection after a timeout.
                }
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

    java.util.List<GroupwareAccountRequest> requestsFor(String employeeNo) {
        return java.util.List.copyOf(requests.getOrDefault(employeeNo, new CopyOnWriteArrayList<>()));
    }

    java.util.List<String> idempotencyKeysFor(String employeeNo) {
        return java.util.List.copyOf(idempotencyKeys.getOrDefault(employeeNo, new CopyOnWriteArrayList<>()));
    }

    void clear() {
        accounts.clear();
        failingEmployeeNos.clear();
        failureStatuses.clear();
        unrelatedNotFoundEmployeeNos.clear();
        requests.clear();
        idempotencyKeys.clear();
        if (releaseRequest != null) {
            releaseRequest.countDown();
        }
        requestEntered = null;
        releaseRequest = null;
    }

    void failFor(String employeeNo) {
        failingEmployeeNos.add(employeeNo);
    }

    void failWithStatus(String employeeNo, int httpStatus) {
        failingEmployeeNos.add(employeeNo);
        failureStatuses.put(employeeNo, httpStatus);
    }

    void returnUnrelatedNotFoundFor(String employeeNo) {
        unrelatedNotFoundEmployeeNos.add(employeeNo);
    }

    void blockNextRequest() {
        requestEntered = new CountDownLatch(1);
        releaseRequest = new CountDownLatch(1);
    }

    boolean awaitBlockedRequest() throws InterruptedException {
        return requestEntered != null && requestEntered.await(5, TimeUnit.SECONDS);
    }

    void releaseBlockedRequest() {
        if (releaseRequest != null) {
            releaseRequest.countDown();
        }
        requestEntered = null;
        releaseRequest = null;
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
    }

    void close() {
        server.stop(0);
    }
}
