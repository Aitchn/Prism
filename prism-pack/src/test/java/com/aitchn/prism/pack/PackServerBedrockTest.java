package com.aitchn.prism.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class PackServerBedrockTest {

    private static HttpResponse<byte[]> get(PackServer server, String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void servesManifestPackAndMappings() throws Exception {
        try (PackServer server = new PackServer("127.0.0.1", 0)) {
            server.start();
            assertEquals(404, get(server, "/bedrock/manifest.json").statusCode());
            byte[] pack = {1, 2, 3};
            server.publishBedrock("abc", pack, "{\"format_version\":2}");
            var manifest = get(server, "/bedrock/manifest.json");
            assertEquals(200, manifest.statusCode());
            assertTrue(new String(manifest.body()).contains("\"sha1\":\"abc\""));
            assertArrayEquals(pack, get(server, "/bedrock/abc.mcpack").body());
            assertEquals("{\"format_version\":2}", new String(get(server, "/bedrock/abc.json").body()));
            assertEquals(404, get(server, "/bedrock/other.mcpack").statusCode());
        }
    }
}
