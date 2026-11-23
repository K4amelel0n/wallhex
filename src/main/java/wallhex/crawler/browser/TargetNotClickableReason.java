package wallhex.crawler.browser;

public enum TargetNotClickableReason {
  NOT_FOUND("No element found for locator '%s'"),
  AMBIGUOUS("Locator '%s' matched %d elements, expected exactly 1"),
  NOT_VISIBLE("Element for locator '%s' is not visible"),
  DISABLED("Element for locator '%s' is disabled"),
  STATE_CHANGED("Element for locator '%s' changed state while it was being checked"),
  TEXT_MISMATCH("Element text '%s' does not match expected text '%s'");

  private final String template;

  TargetNotClickableReason(String template) {
    this.template = template;
  }

  public String message(Object... args) {
    return template.formatted(args);
  }
}
