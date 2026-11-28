package wallhex.crawler.browser;

import java.util.stream.Stream;

public record ObservedElement(
    String locator,
    String id,
    boolean hasStableId,
    String tagName,
    String text,
    String href,
    String ariaLabel,
    String title,
    String placeholder,
    String value,
    String alt,
    String type) {

  public String accessibleName() {
    return Stream.of(text, ariaLabel, title, alt, value, placeholder)
        .filter(candidate -> candidate != null && !candidate.isEmpty())
        .findFirst()
        .orElse("");
  }
}
