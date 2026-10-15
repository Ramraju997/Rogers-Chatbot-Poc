package com.rogers.chatbot.recommend;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Result of a recommendation request.
 *
 * <p>{@code status} tells the agent what to do next:
 *
 * <ul>
 *   <li>OK: present {@code recommendations}
 *   <li>NEED_DEVICE_CHOICE: ask the customer to pick one of {@code deviceOptions}
 *   <li>DEVICE_NOT_FOUND / NO_MATCHING_PLANS: explain {@code message} and ask again
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendResponse(
    String status,
    String message,
    List<String> deviceOptions,
    Device device,
    List<Recommendation> recommendations,
    List<String> notes) {

  public static RecommendResponse of(String status, String message) {
    return new RecommendResponse(status, message, null, null, null, null);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Device(
      String name,
      String storage,
      String color,
      String condition,
      String simType,
      String imageUrl,
      boolean fullPricingAvailable) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Recommendation(
      int rank,
      String planName,
      String planId,
      String data,
      String calling,
      double planMonthly,
      PaymentQuote payment,
      double totalMonthly,
      List<PaymentQuote> otherPaymentOptions,
      String cartUrl) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PaymentQuote(
      String option,
      String label,
      double deviceMonthly,
      double totalMonthly,
      Integer termMonths,
      Double upfront,
      Double returnCredit,
      String devicePriceId) {}
}
