package com.npcwars.chat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import java.time.Duration;
import java.util.List;

/**
 * Chat replies from Claude through the official Anthropic Java SDK. The calls block, so they are made off the main
 * thread by {@link ChatService}; nothing in here touches the Bukkit API.
 */
public final class AnthropicChatBrain implements ChatBrain {

    private final AnthropicClient client;
    private final String model;
    private final String effort;
    private final long maxTokens;

    /**
     * @param baseUrl empty for the official API; set it to use a proxy or a test server
     * @param effort  {@code low}...{@code max}, or empty to send none (models such as Haiku 4.5 reject it)
     */
    public AnthropicChatBrain(String apiKey, String baseUrl, String model, String effort, long maxTokens, Duration timeout) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(timeout).maxRetries(1);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl.strip());
        }
        this.client = builder.build();
        this.model = model;
        this.effort = effort == null ? "" : effort.strip();
        this.maxTokens = maxTokens;
    }

    @Override
    public String reply(String system, List<ChatTurn> turns) throws ChatException {
        if (turns.isEmpty()) {
            throw new ChatException("nothing to answer");
        }
        MessageCreateParams.Builder params = MessageCreateParams.builder().model(model).maxTokens(maxTokens).system(system);
        if (!effort.isEmpty() && !effort.equalsIgnoreCase("none")) {
            params.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(effort.toLowerCase(java.util.Locale.ROOT))).build());
        }
        for (ChatTurn turn : turns) {
            if (turn.role() == ChatTurn.Role.USER) {
                params.addUserMessage(turn.text());
            } else {
                params.addAssistantMessage(turn.text());
            }
        }
        Message response;
        try {
            response = client.messages().create(params.build());
        } catch (RuntimeException ex) {
            throw new ChatException(describe(ex), ex);
        }
        return text(response);
    }

    /** Pulls the reply out of a response, refusing when the model declined. */
    static String text(Message response) throws ChatException {
        if (response.stopReason().filter(reason -> reason.equals(StopReason.REFUSAL)).isPresent()) {
            throw new ChatException("the model declined to answer");
        }
        StringBuilder out = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(text -> out.append(text.text()));
        }
        return out.toString();
    }

    private static String describe(RuntimeException ex) {
        String message = ex.getMessage();
        return ex.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    @Override
    public void close() {
        client.close();
    }
}
