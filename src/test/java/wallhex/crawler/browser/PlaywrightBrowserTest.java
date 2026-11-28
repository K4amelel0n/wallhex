package wallhex.crawler.browser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import wallhex.crawler.model.ActionResult;
import wallhex.crawler.model.BrowserConfig;
import wallhex.crawler.model.BrowserObservation;
import wallhex.crawler.model.CapturedRequest;
import wallhex.crawler.model.ElementTarget;

public class PlaywrightBrowserTest {

  private static final String NEXT_PAGE = "<p id=\"page\">Następna strona</p>";

  @Nested
  class Constructor {

    @Test
    void shouldClosePlaywrightWhenBrowserLaunchFails() {
      try (MockedStatic<Playwright> playwrightStatic = mockStatic(Playwright.class)) {
        Playwright playwright = mock(Playwright.class);
        BrowserType chromium = mock(BrowserType.class);
        playwrightStatic.when(Playwright::create).thenReturn(playwright);
        when(playwright.chromium()).thenReturn(chromium);
        when(chromium.launch()).thenThrow(new PlaywrightException("launch failed"));

        assertThrows(PlaywrightException.class, PlaywrightBrowser::new);

        verify(playwright).close();
      }
    }

    @Test
    void shouldClosePlaywrightWhenContextCreationFails() {
      try (MockedStatic<Playwright> playwrightStatic = mockStatic(Playwright.class)) {
        Playwright playwright = mock(Playwright.class);
        BrowserType chromium = mock(BrowserType.class);
        Browser browser = mock(Browser.class);
        playwrightStatic.when(Playwright::create).thenReturn(playwright);
        when(playwright.chromium()).thenReturn(chromium);
        when(chromium.launch()).thenReturn(browser);
        when(browser.newContext()).thenThrow(new PlaywrightException("context failed"));

        assertThrows(PlaywrightException.class, PlaywrightBrowser::new);

        verify(playwright).close();
      }
    }
  }

  @Nested
  class Navigate {
    @Test
    void shouldThrowPlaywrightExceptionForInvalidUrl() {
      try (PlaywrightBrowser browser = new PlaywrightBrowser()) {
        assertThrows(PlaywrightException.class, () -> browser.navigate("wrong_url"));
      }
    }

    @Test
    void shouldSucceedForDataUrl() {

      try (PlaywrightBrowser browser = new PlaywrightBrowser()) {
        assertDoesNotThrow(() -> browser.navigate("data:text/html,Hello"));
      }
    }
  }

  @Nested
  class Observe {
    @Test
    void shouldReturnCurrentPage() {

      try (PlaywrightBrowser browser = new PlaywrightBrowser()) {
        String url = "data:text/html,Hello";
        browser.navigate(url);
        BrowserObservation observation = browser.observe();
        assertNotNull(observation);
        assertEquals(url, observation.url());
        assertEquals("<html><head></head><body>Hello</body></html>", observation.dom());
      }
    }

    @Test
    void shouldFindButtonCandidate() {

      String html =
          """
          <button id="login-btn"
                  class="btn primary"
                  type="submit"
                  data-testid="login-button">Zaloguj się</button>
          <p></p>
          """;

      try (PlaywrightBrowser browser = new PlaywrightBrowser(buttonsOnly())) {

        String url = htmlUrl(html);
        ElementTarget elementTarget =
            new ElementTarget(
                "button[id=\"login-btn\"]", url + "|button|login-btn||Zaloguj się", "Zaloguj się");

        browser.navigate(url);
        BrowserObservation observation = browser.observe();
        assertNotNull(observation);
        assertNotNull(observation.interactionCandidates());
        assertEquals(1, observation.interactionCandidates().size());
        assertEquals(elementTarget, observation.interactionCandidates().getFirst());
      }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wallhex.crawler.browser.PlaywrightBrowserTest#selectorCases")
    void shouldReturnOnlyElementsMatchingConfiguredSelectors(
        String name, List<String> selectors, List<String> expectedTexts) {

      String html =
          """
          <button>Zaloguj się</button>
          <a href="https://example.com/next">Dalej</a>
          <p>Tekst</p>
          """;

      try (PlaywrightBrowser browser =
          new PlaywrightBrowser(BrowserConfig.defaults().withClickableSelectors(selectors))) {

        browser.navigate(htmlUrl(html));

        List<String> texts =
            browser.observe().interactionCandidates().stream()
                .map(ElementTarget::expectedText)
                .toList();

        assertEquals(expectedTexts, texts);
      }
    }

    @Test
    void shouldReturnLocatorMatchingExactlyOneElement() {

      String html =
          """
          <button>Kliknij</button>
          <button>Kliknij</button>
          <button>Kliknij</button>
          <a href="https://example.com/next">Dalej</a>
          <a href="https://example.com/next">Dalej</a>
          """;
      String url = htmlUrl(html);

      try (PlaywrightBrowser browser = new PlaywrightBrowser();
          Playwright playwright = Playwright.create();
          Browser rawBrowser = playwright.chromium().launch();
          Page page = rawBrowser.newPage()) {

        browser.navigate(url);
        List<ElementTarget> candidates = browser.observe().interactionCandidates();
        assertEquals(5, candidates.size());

        page.navigate(url);
        for (ElementTarget candidate : candidates) {
          assertEquals(
              1, page.locator(candidate.selector()).count(), "selector: " + candidate.selector());
        }
      }
    }

    @Test
    void shouldExcludeHiddenAndDisabledElements() {

      String html =
          """
          <button>Widoczny</button>
          <button style="display:none">Ukryty css</button>
          <button hidden>Ukryty atrybut</button>
          <button disabled>Wyłączony</button>
          <a href="https://example.com/a" style="display:none">Ukryty link</a>
          <a href="https://example.com/b">Widoczny link</a>
          """;

      try (PlaywrightBrowser browser = new PlaywrightBrowser()) {

        browser.navigate(htmlUrl(html));

        List<String> texts =
            browser.observe().interactionCandidates().stream()
                .map(ElementTarget::expectedText)
                .toList();

        assertEquals(List.of("Widoczny", "Widoczny link"), texts);
      }
    }
  }

  @Nested
  class Click {
    @Test
    void shouldClickElementById() {

      String html =
          """
          <button id="first-button"
                             onclick="document.getElementById('status').textContent = 'pierwszy'">
                       Kliknij
                     </button>

                     <button id="second-button"
                             onclick="document.getElementById('status').textContent = 'drugi'">
                       Kliknij
                     </button>

                     <p id="status">brak kliknięcia</p>
          """;

      try (PlaywrightBrowser browser = new PlaywrightBrowser()) {

        ElementTarget elementTarget =
            new ElementTarget("button[id=\"first-button\"]", "", "Kliknij");

        browser.navigate(htmlUrl(html));
        browser.click(elementTarget);
        BrowserObservation observation = browser.observe();
        assertTrue(observation.dom().contains("<p id=\"status\">pierwszy</p>"));
      }
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldNotClickWhenLocatorIsBlank(String locator) {

      String html =
          """
          <button onclick="document.getElementById('status').textContent = 'pierwszy'">
                       Kliknij
                     </button>
                     <button onclick="document.getElementById('status').textContent = 'drugi'">
                       Kliknij
                     </button>

                     <p id="status">brak kliknięcia</p>
          """;

      try (PlaywrightBrowser browser = new PlaywrightBrowser()) {

        ElementTarget elementTarget = new ElementTarget(locator, "", "Kliknij");

        browser.navigate(htmlUrl(html));

        assertThrows(IllegalArgumentException.class, () -> browser.click(elementTarget));
        BrowserObservation observation = browser.observe();
        assertTrue(observation.dom().contains("<p id=\"status\">brak kliknięcia</p>"));
      }
    }

    @Test
    void shouldFailWithNotFoundWhenNoElementMatches() {

      String html =
          """
          <button onclick="document.getElementById('status').textContent = 'pierwszy'">
                       Kliknij
                     </button>
                     <button onclick="document.getElementById('status').textContent = 'drugi'">
                       Kliknij
                     </button>

                     <p id="status">brak kliknięcia</p>
          """;

      try (PlaywrightBrowser browser = new PlaywrightBrowser()) {

        ElementTarget elementTarget = new ElementTarget("#a", "", "Kliknij");

        browser.navigate(htmlUrl(html));
        TargetNotClickableException ex =
            assertThrows(TargetNotClickableException.class, () -> browser.click(elementTarget));
        assertEquals(TargetNotClickableReason.NOT_FOUND, ex.reason());
        BrowserObservation observation = browser.observe();
        assertTrue(observation.dom().contains("<p id=\"status\">brak kliknięcia</p>"));
      }
    }

    @Test
    void shouldFailWithAmbiguousWhenManyElementsMatch() {

      String html =
          """
          <button onclick="document.getElementById('status').textContent = 'pierwszy'"
          name="click" >
                       Kliknij
                     </button>
                     <button onclick="document.getElementById('status').textContent = 'drugi'"
          name="click" >
                       Kliknij
                     </button>

                     <p id="status">brak kliknięcia</p>
          """;

      try (PlaywrightBrowser browser = new PlaywrightBrowser()) {

        browser.navigate(htmlUrl(html));

        ElementTarget ambiguous = new ElementTarget("button[name=\"click\"]", null, null);
        TargetNotClickableException ex =
            assertThrows(TargetNotClickableException.class, () -> browser.click(ambiguous));

        assertEquals(TargetNotClickableReason.AMBIGUOUS, ex.reason());

        BrowserObservation postClickObservation = browser.observe();

        assertTrue(postClickObservation.dom().contains("<p id=\"status\">brak kliknięcia</p>"));
      }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"style=\"display:none\"", "hidden", "hidden disabled"})
    void shouldFailWithNotVisibleWhenElementIsHidden(String attributes) {

      String html =
          """
          <button id="target" %s
              onclick="document.getElementById('status').textContent = 'klik'">Kliknij</button>
          <p id="status">brak kliknięcia</p>
          """
              .formatted(attributes);

      try (PlaywrightBrowser browser = new PlaywrightBrowser()) {

        browser.navigate(htmlUrl(html));

        ElementTarget hidden = new ElementTarget("#target", "", "Kliknij");
        TargetNotClickableException ex =
            assertThrows(TargetNotClickableException.class, () -> browser.click(hidden));

        assertEquals(TargetNotClickableReason.NOT_VISIBLE, ex.reason());
        assertTrue(browser.observe().dom().contains("<p id=\"status\">brak kliknięcia</p>"));
      }
    }

    @Test
    void shouldFailWithDisabledWhenElementIsDisabled() {

      String html =
          """
          <button id="target" disabled
              onclick="document.getElementById('status').textContent = 'klik'">Kliknij</button>
          <p id="status">brak kliknięcia</p>
          """;

      try (PlaywrightBrowser browser = new PlaywrightBrowser()) {

        browser.navigate(htmlUrl(html));

        ElementTarget disabled = new ElementTarget("#target", "", "Kliknij");
        TargetNotClickableException ex =
            assertThrows(TargetNotClickableException.class, () -> browser.click(disabled));

        assertEquals(TargetNotClickableReason.DISABLED, ex.reason());
        assertTrue(browser.observe().dom().contains("<p id=\"status\">brak kliknięcia</p>"));
      }
    }

    @Test
    void shouldFailWithTextMismatchWhenTextChanged() {

      String observedHtml =
          """
          <button onclick="document.getElementById('status').textContent = 'edytuj'">Edytuj</button>
          <button onclick="document.getElementById('status').textContent = 'usun'">Usuń</button>
          <p id="status">brak kliknięcia</p>
          """;

      String changedHtml =
          """
          <button onclick="document.getElementById('status').textContent = 'usun'">Usuń</button>
          <button onclick="document.getElementById('status').textContent = 'dodaj'">Dodaj</button>
          <p id="status">brak kliknięcia</p>
          """;

      try (PlaywrightBrowser browser = new PlaywrightBrowser(buttonsOnly())) {

        browser.navigate(htmlUrl(observedHtml));
        ElementTarget editTarget =
            browser.observe().interactionCandidates().stream()
                .filter(target -> target.expectedText().equals("Edytuj"))
                .findFirst()
                .orElseThrow();
        assertTrue(editTarget.selector().contains("nth-of-type"));

        browser.navigate(htmlUrl(changedHtml));
        TargetNotClickableException ex =
            assertThrows(TargetNotClickableException.class, () -> browser.click(editTarget));

        assertEquals(TargetNotClickableReason.TEXT_MISMATCH, ex.reason());
        assertTrue(browser.observe().dom().contains("<p id=\"status\">brak kliknięcia</p>"));
      }
    }

    @Test
    void shouldClickObservedElementWithoutId() {

      String html =
          """
          <button onclick="document.getElementById('status').textContent = 'pierwszy'">
            Pierwszy
          </button>
          <button onclick="document.getElementById('status').textContent = 'drugi'">
            Drugi
          </button>
          <button onclick="document.getElementById('status').textContent = 'trzeci'">
            Trzeci
          </button>
          <p id="status">brak kliknięcia</p>
          """;

      try (PlaywrightBrowser browser = new PlaywrightBrowser(buttonsOnly())) {

        browser.navigate(htmlUrl(html));
        ElementTarget second =
            browser.observe().interactionCandidates().stream()
                .filter(target -> target.expectedText().equals("Drugi"))
                .findFirst()
                .orElseThrow();

        browser.click(second);

        assertTrue(browser.observe().dom().contains("<p id=\"status\">drugi</p>"));
      }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wallhex.crawler.browser.PlaywrightBrowserTest#observedElementKinds")
    void shouldClickObservedElementOfKind(String name, String element, String expectedText) {

      String html =
          """
          <p id="status">brak kliknięcia</p>
          <button onclick="document.getElementById('status').textContent = 'przycisk'">
            Inny
          </button>
          %s
          """
              .formatted(element);

      try (PlaywrightBrowser browser = new PlaywrightBrowser()) {

        browser.navigate(htmlUrl(html));
        ElementTarget target =
            browser.observe().interactionCandidates().stream()
                .filter(candidate -> expectedText.equals(candidate.expectedText()))
                .findFirst()
                .orElseThrow();

        browser.click(target);

        assertTrue(browser.observe().dom().contains("<p id=\"status\">klik</p>"));
      }
    }

    @Test
    void shouldReturnDelayedFetchSentByClick() throws IOException {

      String html =
          """
          <button onclick="setTimeout(() => fetch('/api/items', {method: 'POST', body: 'q=1'}), 200)">
            Pobierz
          </button>
          """;

      HttpServer server = startServer(html);
      try (PlaywrightBrowser browser = new PlaywrightBrowser(buttonsOnly())) {

        browser.navigate(serverUrl(server));
        ElementTarget button = browser.observe().interactionCandidates().getFirst();

        ActionResult result = browser.click(button);

        assertEquals(
            List.of(new CapturedRequest(serverUrl(server) + "api/items", "POST", "q=1", "fetch")),
            result.requests());
      } finally {
        server.stop(0);
      }
    }

    @Test
    void shouldFailWithClickTimeoutWhenElementIsCovered() {

      String html =
          """
          <button id="target"
              onclick="document.getElementById('status').textContent = 'klik'">Kliknij</button>
          <p id="status">brak kliknięcia</p>
          <div style="position: fixed; inset: 0; background: white"></div>
          """;
      Duration timeout = Duration.ofMillis(300);

      try (PlaywrightBrowser browser =
          new PlaywrightBrowser(BrowserConfig.defaults().withClickTimeout(timeout))) {

        browser.navigate(htmlUrl(html));
        ElementTarget covered = new ElementTarget("#target", "", "Kliknij");

        long start = System.nanoTime();
        ClickTimeoutException ex =
            assertThrows(ClickTimeoutException.class, () -> browser.click(covered));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertEquals("#target", ex.selector());
        assertEquals(timeout, ex.timeout());
        assertInstanceOf(TimeoutError.class, ex.getCause());
        assertTrue(
            elapsed.compareTo(Duration.ofSeconds(5)) < 0,
            "click took " + elapsed.toMillis() + " ms, expected the configured short timeout");
        assertTrue(browser.observe().dom().contains("<p id=\"status\">brak kliknięcia</p>"));
      }
    }

    @Test
    void shouldNavigateWhenRegularLinkIsClicked() throws IOException {

      String html =
          """
          <a href="/next">Dalej</a>
          """;

      HttpServer server = startServer(html);
      try (PlaywrightBrowser browser =
          new PlaywrightBrowser(
              BrowserConfig.defaults().withClickableSelectors(List.of("a[href]")))) {

        browser.navigate(serverUrl(server));
        ElementTarget link = browser.observe().interactionCandidates().getFirst();

        ActionResult result = browser.click(link);

        String nextUrl = serverUrl(server) + "next";
        BrowserObservation observation = browser.observe();
        assertEquals(nextUrl, observation.url());
        assertTrue(observation.dom().contains(NEXT_PAGE));
        assertEquals(
            List.of(new CapturedRequest(nextUrl, "GET", null, "document")), result.requests());
      } finally {
        server.stop(0);
      }
    }
  }

  @Nested
  class Close {
    @Test
    void shouldCloseBrowserWhenClickThrowsInsideTryWithResources() {

      PlaywrightBrowser browser = new PlaywrightBrowser();
      try (browser) {
        browser.navigate("data:text/html,Hello");
        ElementTarget missing = new ElementTarget("#missing", "", "x");
        assertThrows(TargetNotClickableException.class, () -> browser.click(missing));
      }

      assertThrows(PlaywrightException.class, () -> browser.navigate("data:text/html,Hello"));
    }

    @Test
    void shouldNotThrowWhenClosedTwice() {

      PlaywrightBrowser browser = new PlaywrightBrowser();
      browser.close();

      assertDoesNotThrow(browser::close);
    }
  }

  static Stream<Arguments> observedElementKinds() {
    String onclick =
        "onclick=\"document.getElementById('status').textContent = 'klik'; return false\"";
    return Stream.of(
        Arguments.of(
            "link", "<a href=\"https://example.com/next\" " + onclick + ">Dalej</a>", "Dalej"),
        Arguments.of(
            "input submit", "<input type=\"submit\" value=\"Wyślij\" " + onclick + ">", "Wyślij"),
        Arguments.of(
            "input button",
            "<input type=\"button\" value=\"Zatwierdź\" " + onclick + ">",
            "Zatwierdź"));
  }

  static Stream<Arguments> selectorCases() {
    return Stream.of(
        Arguments.of("buttons only", List.of("button"), List.of("Zaloguj się")),
        Arguments.of("links only", List.of("a[href]"), List.of("Dalej")),
        Arguments.of(
            "buttons and links", List.of("button", "a[href]"), List.of("Zaloguj się", "Dalej")));
  }

  private BrowserConfig buttonsOnly() {
    return BrowserConfig.defaults().withClickableSelectors(List.of("button"));
  }

  private HttpServer startServer(String html) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> respond(exchange, "text/html", html));
    server.createContext("/api/items", exchange -> respond(exchange, "application/json", "[]"));
    server.createContext("/next", exchange -> respond(exchange, "text/html", NEXT_PAGE));
    server.start();
    return server;
  }

  private static void respond(HttpExchange exchange, String contentType, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private String serverUrl(HttpServer server) {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
  }

  private String htmlUrl(String html) {
    return "data:text/html;charset=UTF-8;base64,"
        + Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8));
  }
}
