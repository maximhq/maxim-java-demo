import ai.getmaxim.sdk.Config;
import ai.getmaxim.sdk.Maxim;
import ai.getmaxim.sdk.logger.Logger;
import ai.getmaxim.sdk.logger.LoggerConfig;
import ai.getmaxim.sdk.logger.components.*;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import java.util.UUID;

void main() {
    try {
        Config maximConfig = new Config(
                "https://app.getmaxim.ai",
                "<your maxim api key>",
                null,
                true
        );
        Maxim maximClient = new Maxim(maximConfig);
        LoggerConfig maximLoggerConfig = new LoggerConfig("<repo id>", true, 10);
        Logger maximLogger = maximClient.logger(maximLoggerConfig).get();
        UUID sessionId = UUID.randomUUID();

        SessionConfig sessionConfig = new SessionConfig(sessionId.toString(), "Conversation", null);
        Session maximSession = maximLogger.session(sessionConfig);


        // Whenever a user responds/or asks anything
        UUID traceId = UUID.randomUUID();
        Trace maximTrace = maximLogger.trace(new TraceConfig(traceId.toString()
                , "New turn", sessionId.toString(), null));


        maximTrace.setInput("Hello! What's 2 + 2?");


        UUID generationId = UUID.randomUUID();
        Map<String, Object> modelParams = new HashMap<>();
        List<CompletionRequest> messages = new ArrayList<>();
        messages.add(new CompletionRequest("user", "Hello! What's 2+2?"));
        Generation generation = maximTrace.addGeneration(new GenerationConfig(generationId.toString(), "LLM call", null, "openai", "gpt-4o", null, messages, modelParams, null, null));

        // Initialize OpenAI client using OPENAI_API_KEY environment variable
        OpenAIClient client = OpenAIOkHttpClient.builder().apiKey("api-key").build();

        // Build chat completion request
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O)
                .addUserMessage("Hello! What's 2 + 2?")
                .build();

        // Make the API call and capture the response
        ChatCompletion completion = client.chat().completions().create(params);
        // Compute usage
        int PromptTokens = 0;
        int CompletionTokens = 0;
        if (completion.usage().orElse(null) != null) {
            PromptTokens = Math.toIntExact(completion.usage().get().promptTokens());
            CompletionTokens = Math.toIntExact(completion.usage().get().completionTokens());
        }

        generation.setResult(new ChatCompletionResult(
                completion.id(),
                "chat.completion",
                completion.created(),
                completion.model(),
                completion.choices().stream()
                        .map(c -> new ChatCompletionChoice(
                                Math.toIntExact(c.index()),
                                new ChatCompletionMessage("assistant", c.message().content().orElse(""), null, null),
                                c.logprobs(),
                                c.finishReason().asString()))
                        .collect(Collectors.toList()),
                new Usage(PromptTokens, CompletionTokens, PromptTokens + CompletionTokens),
                null
        ));
        generation.end();

        String output = completion.choices().stream()
                .flatMap(choice -> choice.message().content().stream())
                .collect(Collectors.joining());


        maximTrace.setOutput(output);
        maximTrace.end();
        maximSession.end();
        maximLogger.cleanup();
        // Print the response
        System.out.println("OpenAI Response:");
        System.out.println(output);


    } catch (Exception e) {
        System.out.println(e.toString());
    }
}
