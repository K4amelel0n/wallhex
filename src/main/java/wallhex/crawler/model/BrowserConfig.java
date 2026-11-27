package wallhex.crawler.model;

import java.util.List;

public record BrowserConfig(List<String> clickableSelectors, List<String> requestResourceTypes) {

  public BrowserConfig {
    if (clickableSelectors == null || clickableSelectors.isEmpty()) {
      throw new IllegalArgumentException("clickableSelectors must not be empty");
    }
    clickableSelectors = List.copyOf(clickableSelectors);

    if (requestResourceTypes == null) {
      throw new IllegalArgumentException("requestResourceTypes must not be null");
    }
    requestResourceTypes = List.copyOf(requestResourceTypes);
  }

  public static BrowserConfig defaults() {
    return new BrowserConfig(
        List.of("button", "a[href]", "input[type=\"submit\"]", "input[type=\"button\"]"),
        List.of("fetch", "xhr", "document"));
  }
}
