package wallhex.crawler.model;

import java.util.List;

public record BrowserConfig(List<String> clickableSelectors) {

  public BrowserConfig {
    if (clickableSelectors == null || clickableSelectors.isEmpty()) {
      throw new IllegalArgumentException("clickableSelectors must not be empty");
    }
    clickableSelectors = List.copyOf(clickableSelectors);
  }

  public static BrowserConfig defaults() {
    return new BrowserConfig(
        List.of("button", "a[href]", "input[type=\"submit\"]", "input[type=\"button\"]"));
  }
}
