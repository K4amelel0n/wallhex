package wallhex;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

  private final List<SelectorSearchConfig> selectors =
      new ArrayList<SelectorSearchConfig>(
          List.of(
              new SelectorSearchConfig("a", null, false),
              new SelectorSearchConfig("form", null, false),
              new SelectorSearchConfig("button", null, false)));

  private final Page page;

  private static final Logger log = LoggerFactory.getLogger(App.class);

  public App(Page page) {
    this.page = page;
  }

  public static void main(String[] args) {
    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch();
      Page page = browser.newPage();

      App app = new App(page);

      app.initListeners();

      page.navigate("https://playwright.dev/java/");
      log.info(page.title());

      app.start();
    }
  }

  private void start() {
    getForms();
  }

  protected void initListeners() {
    log.info("init listeners");
    this.page.onRequest(request -> log.info(new String(request.method() + "" + request.url())));
    this.page.onResponse(response -> log.info(new String(response.status() + "" + response.url())));
  }

  private void findElements(SelectorSearchConfig config) {
    log.info("Search for selector: {}", config.selector());

    for (Locator link : page.locator(config.selector()).all()) {
      String text = link.textContent().trim();
      String attribute =
          config.attributeName() == null ? "none" : link.getAttribute(config.attributeName());

      log.info("- {} -> {}", text, attribute);
    }
  }

  private void getForms() {
    for (Locator form : this.page.locator("form").all()) {
      String action = form.getAttribute("action");
      String method = form.getAttribute("method");

      log.info("action={}", action);
      log.info("method={}", method);

      for (Locator field : form.locator("input, select, textarea, button").all()) {
        String tag = (String) field.evaluate("el => el.tagName.toLowerCase()");
        String name = field.getAttribute("name");
        String type = field.getAttribute("type");
        boolean required = field.getAttribute("required") != null;

        log.info("tag={}, name={}, type={}, required={}", tag, name, type, required);
      }
    }
  }
}

record SelectorSearchConfig(String selector, String attributeName, boolean includeEmpty) {}
