package com.rogers.chatbot.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.dialogflow.cx.v3.DetectIntentRequest;
import com.google.cloud.dialogflow.cx.v3.DetectIntentResponse;
import com.google.cloud.dialogflow.cx.v3.QueryInput;
import com.google.cloud.dialogflow.cx.v3.ResponseMessage;
import com.google.cloud.dialogflow.cx.v3.SessionsClient;
import com.google.cloud.dialogflow.cx.v3.TextInput;
import com.google.cloud.dialogflow.cx.v3.ToolCall;
import com.google.cloud.dialogflow.cx.v3.ToolCallResult;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import com.rogers.chatbot.config.DialogflowProperties;
import com.rogers.chatbot.recommend.RecommendRequest;
import com.rogers.chatbot.recommend.RecommendResponse;
import com.rogers.chatbot.recommend.RecommendationService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends a customer message to the Dialogflow CX agent.
 *
 * <p>When the agent's playbook decides to use the recommendation <em>function tool</em>,
 * Dialogflow doesn't call anything itself: it returns a tool call to us. We run the
 * recommendation here, send the result back as a {@code toolCallResult}, and the agent then
 * writes its reply from those numbers. Nothing on this machine has to be reachable from the
 * internet.
 */
@Service
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);
  private static final int MAX_TOOL_ROUNDS = 3;

  private final SessionsClient client;
  private final DialogflowProperties props;
  private final RecommendationService recommendations;
  private final ObjectMapper mapper;

  public ChatService(SessionsClient client, DialogflowProperties props,
      RecommendationService recommendations, ObjectMapper mapper) {
    this.client = client;
    this.props = props;
    this.recommendations = recommendations;
    this.mapper = mapper;
  }

  public ChatResponse chat(String sessionId, String text) {
    String session = String.format("projects/%s/locations/%s/agents/%s/sessions/%s",
        props.projectId(), props.location(), props.agentId(), sessionId);

    QueryInput input = QueryInput.newBuilder()
        .setText(TextInput.newBuilder().setText(text))
        .setLanguageCode(props.languageCode())
        .build();

    List<String> messages = new ArrayList<>();
    RecommendResponse shown = null;

    for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
      DetectIntentResponse response = client.detectIntent(DetectIntentRequest.newBuilder()
          .setSession(session)
          .setQueryInput(input)
          .build());

      ToolCall toolCall = null;
      for (ResponseMessage message : response.getQueryResult().getResponseMessagesList()) {
        if (message.hasText()) messages.addAll(message.getText().getTextList());
        if (message.hasToolCall()) toolCall = message.getToolCall();
      }
      if (toolCall == null) break;
      if (round == MAX_TOOL_ROUNDS) {
        log.warn("Agent kept calling tools; stopping after {} rounds", MAX_TOOL_ROUNDS);
        break;
      }

      ToolOutcome outcome = runTool(toolCall);
      if (outcome.recommendation() != null && "OK".equals(outcome.recommendation().status())) {
        shown = outcome.recommendation();
      }
      input = QueryInput.newBuilder()
          .setToolCallResult(ToolCallResult.newBuilder()
              .setTool(toolCall.getTool())
              .setAction(toolCall.getAction())
              .setOutputParameters(outcome.output()))
          .setLanguageCode(props.languageCode())
          .build();
    }

    return new ChatResponse(messages.stream().filter(m -> !m.isBlank()).toList(), shown);
  }

  private record ToolOutcome(Struct output, RecommendResponse recommendation) {}

  /** Runs the recommendation tool with the parameters the agent collected. */
  private ToolOutcome runTool(ToolCall call) {
    try {
      String inputJson = JsonFormat.printer().print(call.getInputParameters());
      log.info("Tool call {} with {}", call.getAction(), inputJson.replaceAll("\\s+", " "));

      RecommendRequest request = mapper.readValue(inputJson, RecommendRequest.class);
      RecommendResponse result = recommendations.recommend(request);
      return new ToolOutcome(toStruct(result), result);
    } catch (Exception e) {
      log.error("Tool call failed", e);
      Struct error = Struct.newBuilder()
          .putFields("status", com.google.protobuf.Value.newBuilder().setStringValue("ERROR").build())
          .putFields("message", com.google.protobuf.Value.newBuilder()
              .setStringValue("The recommendation service failed. Apologise and suggest browsing rogers.com.")
              .build())
          .build();
      return new ToolOutcome(error, null);
    }
  }

  private Struct toStruct(Object value) throws Exception {
    Struct.Builder builder = Struct.newBuilder();
    JsonFormat.parser().ignoringUnknownFields().merge(mapper.writeValueAsString(value), builder);
    return builder.build();
  }
}
