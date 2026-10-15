package com.rogers.chatbot.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rogers.chatbot.recommend.RecommendResponse;
import java.util.List;

/**
 * @param messages        the agent's text replies
 * @param recommendations the options behind the reply, so the UI can show them as cards
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(List<String> messages, RecommendResponse recommendations) {}
