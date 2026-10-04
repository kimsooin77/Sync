package com.kimsooin77.sync.sync.hr;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class HttpStubServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicReference<Throwable> requestObserverFailure = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "[]";
    private volatile long delayMillis;
    private volatile Runnable requestObserver = () -> {
    };

    private HttpStubServer(HttpServer server) {
        this.server = server;
        server.createContext("/mock/hr/employees", this::handle);
        server.start();
    }

    public static HttpStubServer start() throws IOException {
        return new HttpStubServer(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void respond(int status, String responseBody) {
        this.status = status;
        this.responseBody = responseBody;
        this.delayMillis = 0;
        this.requestObserver = () -> {
        };
        requestObserverFailure.set(null);
    }

    public void delayResponse(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    public void observeRequests(Runnable observer) {
        this.requestObserver = observer;
        requestObserverFailure.set(null);
    }

    public Throwable requestObserverFailure() {
        return requestObserverFailure.get();
    }

    void stop() {
        if (stopped.compareAndSet(false, true)) {
            server.stop(0);
        }
    }

    @Override
    public void close() {
        stop();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestMethod().equals("GET")) {
                send(exchange, 405, "");
                return;
            }
            try {
                requestObserver.run();
            } catch (Throwable observerFailure) {
                requestObserverFailure.set(observerFailure);
            }
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    send(exchange, 500, "");
                    return;
                }
            }
            send(exchange, status, responseBody);
        } catch (IOException clientDisconnected) {
            // The client may close the exchange when its read timeout expires.
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
