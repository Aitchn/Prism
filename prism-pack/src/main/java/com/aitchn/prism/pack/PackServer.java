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
 * Serves packs at immutable URLs {@code /<sha1>.zip}. Each named slot (for example Prism's own pack, or BetterModel's)
 * keeps its current and previous build, so a client still downloading an earlier build is not cut off and a pack
 * that is advertised never disappears because another slot changed.
 */
public final class PackServer implements AutoCloseable {

    private static final int KEEP_PER_SLOT = 2;

    private final HttpServer server;
    private final Map<String, byte[]> packs = new ConcurrentHashMap<>();
    private final Map<String, java.util.Deque<String>> slots = new java.util.HashMap<>();

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

    /** Makes {@code zip} available as {@code /<sha1>.zip} and makes it the current build of {@code slot}. */
    public synchronized void publish(@NotNull String slot, @NotNull String sha1, byte @NotNull [] zip) {
        packs.put(sha1, zip);
        java.util.Deque<String> history = slots.computeIfAbsent(slot, k -> new java.util.ArrayDeque<>());
        history.remove(sha1);
        history.addFirst(sha1);
        while (history.size() > KEEP_PER_SLOT) {
            history.removeLast();
        }
        java.util.Set<String> keep = new java.util.HashSet<>();
        slots.values().forEach(keep::addAll);
        packs.keySet().retainAll(keep);
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
