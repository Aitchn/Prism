package com.aitchn.prism.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;

/**
 * Serves packs at immutable URLs {@code /<sha1>.zip}. Old hashes stay available so a client that is still
 * downloading an earlier build is not cut off; only the newest few are kept.
 */
public final class PackServer implements AutoCloseable {

    private static final int KEEP = 4;

    private final HttpServer server;
    private final Map<String, byte[]> packs = new ConcurrentHashMap<>();
    private final java.util.Deque<String> order = new java.util.ArrayDeque<>();

    /** @param port 0 picks a free port; see {@link #port()} */
    public PackServer(@NotNull String bindAddress, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "prism-pack-http");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /** Makes {@code zip} available as {@code /<sha1>.zip}. */
    public synchronized void publish(@NotNull String sha1, byte @NotNull [] zip) {
        if (packs.put(sha1, zip) == null) {
            order.addLast(sha1);
            while (order.size() > KEEP) {
                packs.remove(order.removeFirst());
            }
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            byte[] zip = null;
            if (path.startsWith("/") && path.endsWith(".zip")) {
                zip = packs.get(path.substring(1, path.length() - 4));
            }
            if (zip == null || !exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            exchange.getResponseHeaders().add("Cache-Control", "public, max-age=31536000, immutable");
            exchange.sendResponseHeaders(200, zip.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(zip);
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
