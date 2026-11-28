package wallhex.crawler.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

abstract class PlaywrightPageTest {

  private static Playwright playwright;
  private static Browser browser;
  private BrowserContext context;
  protected Page page;

  @BeforeAll
  static void launchBrowser() {
    playwright = Playwright.create();
    try {
      browser = playwright.chromium().launch();
    } catch (RuntimeException e) {
      playwright.close();
      throw e;
    }
  }

  @AfterAll
  static void closeBrowser() {
    try {
      browser.close();
    } finally {
      playwright.close();
    }
  }

  @BeforeEach
  void openPage() {
    context = browser.newContext();
    configureContext(context);
    page = context.newPage();
  }

  @AfterEach
  void closeContext() {
    context.close();
  }

  protected void configureContext(BrowserContext context) {}

  protected static String htmlUrl(String html) {
    return "data:text/html;charset=UTF-8;base64,"
        + Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8));
  }
}
