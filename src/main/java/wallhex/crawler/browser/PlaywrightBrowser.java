package wallhex.crawler.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import wallhex.crawler.model.ActionResult;
import wallhex.crawler.model.BrowserConfig;
import wallhex.crawler.model.BrowserObservation;
import wallhex.crawler.model.ElementTarget;

public class PlaywrightBrowser implements AutoCloseable {

  private final Playwright playwright;
  private final Browser browser;
  private final BrowserContext context;
  private Page page;
  private final BrowserConfig config;
  private final NetworkMonitor networkMonitor;

  private static final JsonMapper MAPPER =
      JsonMapper.builder()
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
          .build();

  public PlaywrightBrowser() {
    this(BrowserConfig.defaults());
  }

  public PlaywrightBrowser(BrowserConfig config) {
    this.config = Objects.requireNonNull(config, "config");
    networkMonitor = new NetworkMonitor(config.requestResourceTypes());

    playwright = Playwright.create();

    try {
      browser = playwright.chromium().launch();
      context = browser.newContext();
      initContext();
      page = context.newPage();

    } catch (RuntimeException e) {
      playwright.close();
      throw e;
    }
  }

  private void initContext() {

    context.addInitScript(JsScript.COMMON.source());
    context.onRequest(networkMonitor::onRequest);
    context.onRequestFinished(networkMonitor::onRequestFinished);
    context.onRequestFailed(networkMonitor::onRequestFailed);
  }

  public ActionResult navigate(String url) {
    return new ActionResult(networkMonitor.captureRequests(page, () -> page.navigate(url)));
  }

  public BrowserObservation observe() {

    return new BrowserObservation(page.url(), page.content(), findClickableElements());
  }

  public ActionResult click(ElementTarget target) {
    String selector = target.selector();
    if (selector == null || selector.isEmpty())
      throw new IllegalArgumentException("selector must not be blank");
    Locator locator = page.locator(target.selector());
    requireSingleMatch(locator, target.selector());
    requireVisibleAndEnabled(locator, target.selector());

    List<ObservedElement> discoverResult = discover(locator);

    if (discoverResult.isEmpty()) throw TargetNotClickableException.stateChanged(target.selector());

    ObservedElement observedElement = discoverResult.getFirst();
    String actual = observedElement.accessibleName();
    requireTextMatches(actual, target.expectedText());

    return new ActionResult(networkMonitor.captureRequests(page, locator::click));
  }

  private List<ElementTarget> findClickableElements() {

    Locator locator = page.locator(String.join(", ", config.clickableSelectors()));

    String pageUrl = page.url();

    List<ObservedElement> result = discover(locator);

    return result.stream()
        .filter(element -> element.locator() != null)
        .map(
            element ->
                new ElementTarget(
                    element.locator(),
                    ElementIdentity.identityKey(pageUrl, element),
                    element.accessibleName()))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private List<ObservedElement> discover(Locator locator) {

    List<Map<String, Object>> result =
        (List<Map<String, Object>>) locator.evaluateAll(JsScript.DISCOVER.source());

    return result.stream().map(PlaywrightBrowser::toObservedElement).toList();
  }

  private void requireSingleMatch(Locator locator, String selector) {
    int matchCount = locator.count();
    if (matchCount == 0) throw TargetNotClickableException.notFound(selector);
    if (matchCount > 1) throw TargetNotClickableException.ambiguous(selector, matchCount);
  }

  private void requireVisibleAndEnabled(Locator locator, String selector) {
    if (!evaluateCheck(locator, "isVisible"))
      throw TargetNotClickableException.notVisible(selector);
    if (!evaluateCheck(locator, "isEnabled")) throw TargetNotClickableException.disabled(selector);
  }

  private boolean evaluateCheck(Locator locator, String checkName) {
    Object result = locator.evaluate("(el, name) => document._wallhex[name](el)", checkName);
    return Boolean.TRUE.equals(result);
  }

  private void requireTextMatches(String actual, String expected) {
    if (!Objects.equals(actual, expected))
      throw TargetNotClickableException.textMismatch(actual, expected);
  }

  private static ObservedElement toObservedElement(Map<String, Object> raw) {
    return MAPPER.convertValue(raw, ObservedElement.class);
  }

  @Override
  public void close() {
    try {
      if (!context.isClosed()) context.close();
    } finally {
      try {
        if (browser.isConnected()) browser.close();
      } finally {
        playwright.close();
      }
    }
  }
}
