package wallhex.crawler.browser;

import java.time.Duration;

public class ClickTimeoutException extends RuntimeException {
  private final String selector;
  private final Duration timeout;

  ClickTimeoutException(String selector, Duration timeout, Throwable cause) {
    super(
        "Click on locator '%s' did not complete within %d ms"
            .formatted(selector, timeout.toMillis()),
        cause);
    this.selector = selector;
    this.timeout = timeout;
  }

  public String selector() {
    return selector;
  }

  public Duration timeout() {
    return timeout;
  }
}
