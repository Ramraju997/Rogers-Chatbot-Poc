package com.rogers.chatbot.chat;

import com.google.api.gax.rpc.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

  private static final Logger log = LoggerFactory.getLogger(ChatController.class);

  private final ChatService chat;

  public ChatController(ChatService chat) {
    this.chat = chat;
  }

  public record ChatRequest(
      @NotBlank @Pattern(regexp = "[\\w-]{1,36}") String sessionId,
      @NotBlank @Size(max = 256) String text) {}

  @GetMapping("/health")
  public Map<String, Boolean> health() {
    return Map.of("ok", true);
  }

  @PostMapping("/chat")
  public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
    return chat.chat(request.sessionId(), request.text().trim());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> badRequest(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest().body(Map.of("error", "A sessionId and message text are required."));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Map<String, String>> dialogflowError(ApiException e) {
    log.error("Dialogflow error", e);
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(Map.of("error", "Dialogflow error: " + e.getStatusCode().getCode() + " " + e.getMessage()));
  }
}
