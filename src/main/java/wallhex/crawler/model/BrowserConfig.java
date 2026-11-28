package wallhex.crawler.model;

import java.time.Duration;
import java.util.List;

public record BrowserConfig(
    List<String> clickableSelectors, List<String> requestResourceTypes, Duration clickTimeout) {

  public BrowserConfig {
    if (clickableSelectors == null || clickableSelectors.isEmpty()) {
      throw new IllegalArgumentException("clickableSelectors must not be empty");
    }
    clickableSelectors = List.copyOf(clickableSelectors);

    if (requestResourceTypes == null) {
      throw new IllegalArgumentException("requestResourceTypes must not be null");
    }
    requestResourceTypes = List.copyOf(requestResourceTypes);

    if (clickTimeout == null || clickTimeout.isZero() || clickTimeout.isNegative()) {
      throw new IllegalArgumentException("clickTimeout must be positive");
    }
  }

  public static BrowserConfig defaults() {
    return new BrowserConfig(
        List.of("button", "a[href]", "input[type=\"submit\"]", "input[type=\"button\"]"),
        List.of("fetch", "xhr", "document"),
        Duration.ofSeconds(15));
  }

  public BrowserConfig withClickableSelectors(List<String> newClickableSelectors) {
    return new BrowserConfig(newClickableSelectors, requestResourceTypes, clickTimeout);
  }

  public BrowserConfig withClickTimeout(Duration newClickTimeout) {
    return new BrowserConfig(clickableSelectors, requestResourceTypes, newClickTimeout);
  }
}
