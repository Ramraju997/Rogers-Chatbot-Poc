package com.rogers.chatbot.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.rogers.chatbot.catalog.CatalogService;
import com.rogers.chatbot.config.CatalogProperties;
import com.rogers.chatbot.recommend.RecommendResponse.Device;
import com.rogers.chatbot.recommend.RecommendResponse.PaymentQuote;
import com.rogers.chatbot.recommend.RecommendResponse.Recommendation;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Picks the best device + plan combinations for a customer.
 *
 * <p>Every plan has a {@code planTierCode}, and every device price row has a {@code tierCode}.
 * A phone's monthly price depends on which plan it's paired with, so we join the two on that code
 * and rank by the total the customer pays per month (plan + device).
 */
@Service
public class RecommendationService {

  private static final double UNLIMITED_GB = 999_999;
  private static final int MAX_RESULTS = 3;

  private static final Map<String, Double> MIN_DATA_GB = Map.of(
      "light", 0.0,
      "moderate", 60.0,
      "heavy", 100.0,
      "unlimited", UNLIMITED_GB);

  private static final Map<String, String> PAYMENT_LABELS = Map.of(
      "save_return", "Save & Return: lower monthly price, return the phone after the term",
      "finance", "Financing: pay over the term and keep the phone",
      "trade_in", "Financing with a trade-in credit",
      "outright", "Buy the phone outright");

  private static final Set<String> BUNDLE_WORDS = Set.of("accessories", "airpods", "beats", "bundle");

  private final CatalogService catalog;
  private final CatalogProperties props;

  public RecommendationService(CatalogService catalog, CatalogProperties props) {
    this.catalog = catalog;
    this.props = props;
  }

  public RecommendResponse recommend(RecommendRequest req) {
    if (req == null || !StringUtils.hasText(req.deviceQuery())) {
      return RecommendResponse.of("NEED_DEVICE", "Ask the customer which phone they want.");
    }

    // 1. Find the device.
    List<String> query = tokens(req.deviceQuery());
    boolean wantsBundle = query.stream().anyMatch(BUNDLE_WORDS::contains);
    List<JsonNode> candidates = catalog.devices().stream()
        .filter(d -> d.path("pricing").isObject())
        .filter(d -> StringUtils.hasText(name(d)) && !"OFFER_TILE".equals(name(d)))
        .filter(d -> wantsBundle || !name(d).contains("&"))
        .filter(d -> tokens(name(d) + " " + d.path("manufacturer").asText()).containsAll(query))
        .toList();

    List<JsonNode> exact = candidates.stream().filter(d -> isExactName(d, query)).toList();
    if (!exact.isEmpty()) candidates = exact;

    String condition = normalizeCondition(req.condition());
    if (condition != null) {
      List<JsonNode> sameCondition = candidates.stream()
          .filter(d -> condition.equals(d.path("deviceConditionKey").asText().toLowerCase(Locale.ROOT)))
          .toList();
      if (!sameCondition.isEmpty()) candidates = sameCondition;
    }

    if (candidates.isEmpty()) {
      return RecommendResponse.of("DEVICE_NOT_FOUND",
          "No device in the catalog matches '" + req.deviceQuery()
              + "'. Ask the customer to rephrase, or suggest a similar model.");
    }

    List<String> names = candidates.stream().map(RecommendationService::name).distinct().sorted().toList();
    if (names.size() > 1) {
      return new RecommendResponse("NEED_DEVICE_CHOICE",
          "Several devices match. Ask the customer which one they mean.",
          names.subList(0, Math.min(names.size(), 8)), null, null, null);
    }

    JsonNode device = candidates.stream()
        .filter(d -> "new".equalsIgnoreCase(d.path("deviceConditionKey").asText()))
        .findFirst()
        .orElse(candidates.get(0));

    // 2. All the ways this device can be priced.
    List<JsonNode> priceRows = catalog.detailedPrices(device.path("externalId").asText());
    boolean fullPricing = !priceRows.isEmpty();
    if (!fullPricing) priceRows = List.of(device.path("pricing"));

    // 3. Pair the device with every plan that can carry a device and fits the customer.
    String payment = normalizePayment(req.paymentOption());
    Double minGb = req.dataNeed() == null ? null : MIN_DATA_GB.get(req.dataNeed().toLowerCase(Locale.ROOT).trim());
    boolean international = Boolean.TRUE.equals(req.international());

    List<Candidate> options = new ArrayList<>();
    for (JsonNode plan : catalog.plans()) {
      String tier = plan.path("planTierCode").asText();
      if (tier.isEmpty() || "NOTERM".equals(tier)) continue; // BYOD plans: no device pricing
      if (!plan.path("publiclyAvailable").asBoolean(true)) continue;

      double gb = dataGb(plan);
      if (minGb != null && gb < minGb) continue;
      if (international && !plan.path("talk").path("externalId").asText().contains("countries")) continue;

      double planPrice = plan.path("rawData").path("price").asDouble();
      Map<String, PaymentQuote> quotes = new LinkedHashMap<>();
      for (JsonNode row : priceRows) {
        String option = paymentOption(row);
        if (!"outright".equals(option) && !tier.equals(row.path("tierCode").asText())) continue;
        PaymentQuote quote = quote(option, row, planPrice);
        quotes.merge(option, quote, (a, b) -> a.totalMonthly() <= b.totalMonthly() ? a : b);
      }

      Optional<PaymentQuote> main = quotes.values().stream()
          .filter(q -> "any".equals(payment) ? !"outright".equals(q.option()) : q.option().equals(payment))
          .min(Comparator.comparingDouble(PaymentQuote::totalMonthly));
      if (main.isEmpty()) continue;
      if (req.maxMonthlyBudget() != null && main.get().totalMonthly() > req.maxMonthlyBudget()) continue;

      List<PaymentQuote> others = quotes.values().stream()
          .filter(q -> q != main.get())
          // Outright needs a large upfront payment, so list it after the monthly options.
          .sorted(Comparator.comparing((PaymentQuote q) -> "outright".equals(q.option()))
              .thenComparingDouble(PaymentQuote::totalMonthly))
          .toList();
      options.add(new Candidate(plan, gb, planPrice, main.get(), others));
    }

    Device summary = summarize(device, fullPricing);
    List<String> notes = notes(priceRows, fullPricing);

    if (options.isEmpty()) {
      return new RecommendResponse("NO_MATCHING_PLANS",
          "No plan fits all of the customer's requirements for this device. Explain which requirement "
              + "rules everything out (budget, data, international or payment option) and offer to relax it.",
          null, summary, List.of(), notes);
    }

    // 4. Cheapest total first.
    options.sort(Comparator.comparingDouble((Candidate c) -> c.main().totalMonthly())
        .thenComparingDouble(Candidate::planPrice));

    List<Recommendation> recommendations = new ArrayList<>();
    for (int i = 0; i < Math.min(MAX_RESULTS, options.size()); i++) {
      Candidate c = options.get(i);
      recommendations.add(new Recommendation(
          i + 1,
          c.plan().path("planDisplayName").asText(),
          c.plan().path("planId").asText(),
          dataLabel(c.gb()),
          c.plan().path("talk").path("title").asText(null),
          c.planPrice(),
          c.main(),
          c.main().totalMonthly(),
          c.others().isEmpty() ? null : c.others(),
          cartUrl(device, c.main(), c.plan())));
    }

    return new RecommendResponse("OK",
        "Present these options cheapest first. Use only these numbers. Point out when a small price "
            + "difference buys noticeably more (for example more data).",
        null, summary, recommendations, notes);
  }

  // ---- helpers ----------------------------------------------------------------------------

  private record Candidate(JsonNode plan, double gb, double planPrice, PaymentQuote main, List<PaymentQuote> others) {}

  private static PaymentQuote quote(String option, JsonNode row, double planPrice) {
    String label = PAYMENT_LABELS.get(option);
    String priceId = row.path("id").asText(null);
    if ("outright".equals(option)) {
      return new PaymentQuote(option, label, 0, money(planPrice), null,
          money(row.path("regularAmount").asDouble()), null, priceId);
    }
    double deviceMonthly = money(row.path("monthlyInstallmentAmount").asDouble());
    double rv = row.path("rvAmount").asDouble();
    int term = row.path("recurringChargePeriod").asInt(0);
    return new PaymentQuote(option, label, deviceMonthly, money(planPrice + deviceMonthly),
        term > 0 ? term : null, null, rv > 0 ? money(rv) : null, priceId);
  }

  private static String paymentOption(JsonNode row) {
    String tier = row.path("priceTier").asText().toUpperCase(Locale.ROOT);
    if ("oneTimeCharge".equals(row.path("priceType").asText()) || "NOTERM".equals(tier)) return "outright";
    if (tier.contains("TRADEIN")) return "trade_in";
    if (tier.contains("UPFRONTEDGE")) return "save_return";
    return "finance";
  }

  private static String normalizePayment(String value) {
    if (value == null) return "any";
    String v = value.toLowerCase(Locale.ROOT).trim().replace(' ', '_').replace('-', '_');
    return PAYMENT_LABELS.containsKey(v) ? v : "any";
  }

  private static String normalizeCondition(String value) {
    if (value == null) return null;
    String v = value.toLowerCase(Locale.ROOT);
    if (v.contains("pre") || v.contains("used") || v.contains("refurb")) return "preowned";
    if (v.contains("new")) return "new";
    return null;
  }

  private static double dataGb(JsonNode plan) {
    JsonNode raw = plan.path("rawData");
    double amount;
    try {
      amount = Double.parseDouble(raw.path("dataUsage").asText("0"));
    } catch (NumberFormatException e) {
      amount = 0;
    }
    return "MB".equalsIgnoreCase(raw.path("dataUnit").asText()) ? amount / 1000 : amount;
  }

  private static String dataLabel(double gb) {
    if (gb >= UNLIMITED_GB) return "Unlimited";
    if (gb <= 0) return "No data";
    return String.format(Locale.ROOT, "%.0f GB", gb);
  }

  private Device summarize(JsonNode d, boolean fullPricing) {
    return new Device(
        name(d),
        d.path("memory").asText(null),
        d.path("color").asText(null),
        d.path("deviceCondition").asText(null),
        d.path("simType").asText(null),
        d.path("picture").path("src").asText(null),
        fullPricing);
  }

  private static List<String> notes(List<JsonNode> rows, boolean fullPricing) {
    Set<String> notes = new LinkedHashSet<>();
    for (JsonNode row : rows) {
      for (JsonNode promo : row.path("promo").path("promotions")) {
        String text = promo.path("content").asText("").trim();
        // Save & Return amounts differ per row and are already in each quote.
        if (!text.isEmpty() && !text.contains("Save & Return credit")) notes.add(text);
      }
    }
    if (!fullPricing) {
      notes.add("Only this phone's headline price is loaded, so only plans in that price tier are shown.");
    }
    return notes.stream().limit(5).toList();
  }

  private String cartUrl(JsonNode device, PaymentQuote quote, JsonNode plan) {
    String template = props.cartUrlTemplate();
    if (!StringUtils.hasText(template)) return null;
    return template
        .replace("{slug}", enc(device.path("urlSlug").asText()))
        .replace("{deviceSkuId}", enc(device.path("masterVariantId").asText()))
        .replace("{devicePriceId}", enc(quote.devicePriceId() == null ? "" : quote.devicePriceId()))
        .replace("{planId}", enc(plan.path("planId").asText()));
  }

  private static boolean isExactName(JsonNode d, List<String> query) {
    List<String> nameTokens = tokens(name(d));
    if (nameTokens.equals(query)) return true;
    List<String> brand = tokens(d.path("manufacturer").asText());
    List<String> withoutBrand = nameTokens.stream().filter(t -> !brand.contains(t)).toList();
    return withoutBrand.equals(query);
  }

  private static List<String> tokens(String text) {
    return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
        .filter(t -> !t.isEmpty())
        .toList();
  }

  private static String name(JsonNode d) {
    return d.path("deviceName").asText("");
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static double money(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
