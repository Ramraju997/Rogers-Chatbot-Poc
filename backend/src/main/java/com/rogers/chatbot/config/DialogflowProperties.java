package com.rogers.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dialogflow")
public record DialogflowProperties(
    String projectId,
    String location,
    String agentId,
    String languageCode,
    String credentialsFile) {

  /** Global agents use the default endpoint; regional agents need a regional one. */
  public String endpoint() {
    return "global".equals(location)
        ? "dialogflow.googleapis.com:443"
        : location + "-dialogflow.googleapis.com:443";
  }
}
