package com.rogers.chatbot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rogers.chatbot.config.CatalogProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Loads the device and plan catalog from JSON files on disk.
 *
 * <p>The files are the raw responses of the rogers.com catalog APIs, so swapping this class for
 * live API calls later doesn't change the recommendation logic.
 */
@Service
public class CatalogService {

  private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

  private final ObjectMapper mapper;
  private final Path dataDir;
  private final Map<String, List<JsonNode>> priceCache = new ConcurrentHashMap<>();

  private List<JsonNode> devices = List.of();
  private List<JsonNode> plans = List.of();

  public CatalogService(ObjectMapper mapper, CatalogProperties props) {
    this.mapper = mapper;
    this.dataDir = Path.of(props.dataDir());
  }

  @PostConstruct
  void load() throws IOException {
    Path devicesFile = dataDir.resolve("devices.json");
    Path plansFile = dataDir.resolve("plans.json");
    if (!Files.exists(devicesFile) || !Files.exists(plansFile)) {
      throw new IllegalStateException("Catalog files not found in " + dataDir.toAbsolutePath()
          + ". Put devices.json and plans.json there (see backend/README.md).");
    }
    devices = toList(mapper.readTree(devicesFile.toFile()).path("devices"));
    plans = toList(mapper.readTree(plansFile.toFile()).path("plans"));
    log.info("Catalog loaded: {} devices, {} plans from {}", devices.size(), plans.size(), dataDir.toAbsolutePath());
  }

  public List<JsonNode> devices() {
    return devices;
  }

  public List<JsonNode> plans() {
    return plans;
  }

  /**
   * Full list of price rows (every plan tier and payment option) for a device, read from
   * device-prices/&lt;externalId&gt;.json. Empty if that file doesn't exist.
   */
  public List<JsonNode> detailedPrices(String externalId) {
    return priceCache.computeIfAbsent(externalId, id -> {
      Path file = dataDir.resolve("device-prices").resolve(id + ".json");
      if (!Files.exists(file)) return List.of();
      try {
        List<JsonNode> rows = new ArrayList<>();
        for (JsonNode sku : mapper.readTree(file.toFile())) {
          sku.path("prices").forEach(rows::add);
        }
        return rows;
      } catch (IOException e) {
        log.warn("Could not read {}: {}", file, e.getMessage());
        return List.of();
      }
    });
  }

  private static List<JsonNode> toList(JsonNode array) {
    List<JsonNode> list = new ArrayList<>();
    array.forEach(list::add);
    return List.copyOf(list);
  }
}
