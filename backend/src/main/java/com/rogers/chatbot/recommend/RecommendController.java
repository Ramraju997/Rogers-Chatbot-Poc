package com.rogers.chatbot.recommend;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Direct access to the recommendation engine, handy for testing without the agent. */
@RestController
@RequestMapping("/api")
public class RecommendController {

  private final RecommendationService service;

  public RecommendController(RecommendationService service) {
    this.service = service;
  }

  @PostMapping("/recommend")
  public RecommendResponse recommend(@RequestBody RecommendRequest request) {
    return service.recommend(request);
  }
}
