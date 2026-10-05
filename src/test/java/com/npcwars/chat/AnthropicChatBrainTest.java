package com.npcwars.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the real Anthropic SDK against a local fake of the Messages API. */
class AnthropicChatBrainTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String response;

    private static String message(String stopReason, String text) {
        return "{\"id\":\"msg_test\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}],\"stop_reason\":\"" + stopReason + "\","
                + "\"stop_sequence\":null,\"usage\":{\"input_tokens\":12,\"output_tokens\":7}}";
    }

    @BeforeEach
    void start() throws IOException {
        response = message("end_turn", "hey! mining iron, you?");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            path.set(exchange.getRequestURI().getPath());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AnthropicChatBrain brain(String effort) {
        return new AnthropicChatBrain("test-key", "http://127.0.0.1:" + server.getAddress().getPort(), "claude-opus-5-5",
                effort, 1024, Duration.ofSeconds(10));
    }

    @Test
    void sendsAWellFormedRequestAndReturnsTheText() throws Exception {
        try (AnthropicChatBrain brain = brain("low")) {
            String reply = brain.reply("You are Kai.", List.of(
                    new ChatTurn(ChatTurn.Role.USER, "Steve: hi"),
                    new ChatTurn(ChatTurn.Role.ASSISTANT, "hey"),
                    new ChatTurn(ChatTurn.Role.USER, "Steve: whats up")));
            assertEquals("hey! mining iron, you?", reply);
        }
        assertEquals("/v1/messages", path.get());
        assertEquals("test-key", apiKeyHeader.get());
        JsonObject request = JsonParser.parseString(body.get()).getAsJsonObject();
        assertEquals("claude-opus-5-5", request.get("model").getAsString());
        assertEquals(1024, request.get("max_tokens").getAsInt());
        assertEquals("You are Kai.", request.get("system").getAsString());
        assertEquals("low", request.getAsJsonObject("output_config").get("effort").getAsString());
        JsonArray messages = request.getAsJsonArray("messages");
        assertEquals(3, messages.size());
        assertEquals("user", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("assistant", messages.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("Steve: whats up", messages.get(2).getAsJsonObject().get("content").getAsString().isEmpty()
                ? "" : textOf(messages.get(2).getAsJsonObject()));
    }

    private static String textOf(JsonObject message) {
        var content = message.get("content");
        if (content.isJsonPrimitive()) {
            return content.getAsString();
        }
        return content.getAsJsonArray().get(0).getAsJsonObject().get("text").getAsString();
    }

    @Test
    void effortNoneOmitsTheSetting() throws Exception {
        try (AnthropicChatBrain brain = brain("none")) {
            brain.reply("sys", List.of(new ChatTurn(ChatTurn.Role.USER, "Steve: hi")));
        }
        assertTrue(!JsonParser.parseString(body.get()).getAsJsonObject().has("output_config"), body.get());
    }

    @Test
    void aRefusalIsReportedNotShown() {
        response = message("refusal", "I can't help with that.");
        try (AnthropicChatBrain brain = brain("low")) {
            ChatException ex = assertThrows(ChatException.class,
                    () -> brain.reply("sys", List.of(new ChatTurn(ChatTurn.Role.USER, "Steve: hi"))));
            assertTrue(ex.getMessage().contains("declined"));
        }
    }

    @Test
    void httpErrorsBecomeChatExceptions() {
        status = 401;
        response = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
        try (AnthropicChatBrain brain = brain("low")) {
            ChatException ex = assertThrows(ChatException.class,
                    () -> brain.reply("sys", List.of(new ChatTurn(ChatTurn.Role.USER, "Steve: hi"))));
            assertTrue(ex.getMessage().contains("401") || ex.getMessage().toLowerCase().contains("auth")
                    || ex.getMessage().toLowerCase().contains("api-key"), ex.getMessage());
        }
    }

    @Test
    void emptyConversationsAreRejectedBeforeAnyRequest() {
        try (AnthropicChatBrain brain = brain("low")) {
            assertThrows(ChatException.class, () -> brain.reply("sys", List.of()));
        }
        assertEquals(null, body.get());
    }
}
