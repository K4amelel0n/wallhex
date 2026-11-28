package wallhex.crawler.browser;

public class TargetNotClickableException extends RuntimeException {
  private final TargetNotClickableReason reason;

  private TargetNotClickableException(TargetNotClickableReason reason, Object... args) {
    super(reason.message(args));
    this.reason = reason;
  }

  public TargetNotClickableReason reason() {
    return this.reason;
  }

  public static TargetNotClickableException textMismatch(String actual, String expected) {
    return new TargetNotClickableException(
        TargetNotClickableReason.TEXT_MISMATCH, actual, expected);
  }

  public static TargetNotClickableException notFound(String selector) {
    return new TargetNotClickableException(TargetNotClickableReason.NOT_FOUND, selector);
  }

  public static TargetNotClickableException notVisible(String selector) {
    return new TargetNotClickableException(TargetNotClickableReason.NOT_VISIBLE, selector);
  }

  public static TargetNotClickableException disabled(String selector) {
    return new TargetNotClickableException(TargetNotClickableReason.DISABLED, selector);
  }

  public static TargetNotClickableException ambiguous(String selector, int count) {
    return new TargetNotClickableException(TargetNotClickableReason.AMBIGUOUS, selector, count);
  }
}
