package com.rogers.chatbot.recommend;

/**
 * What the customer wants. Only {@code deviceQuery} is required; everything else narrows the
 * results when known.
 *
 * @param deviceQuery      device as the customer said it, e.g. "iphone 18 pro"
 * @param condition        "new" or "preowned"
 * @param dataNeed         "light" (any), "moderate" (60 GB+), "heavy" (100 GB+) or "unlimited"
 * @param international    true if they call or travel outside Canada
 * @param paymentOption    "save_return", "finance", "trade_in", "outright" or "any"
 * @param maxMonthlyBudget upper limit for plan + device per month
 */
public record RecommendRequest(
    String deviceQuery,
    String condition,
    String dataNeed,
    Boolean international,
    String paymentOption,
    Double maxMonthlyBudget) {}
