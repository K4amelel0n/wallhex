package wallhex.crawler.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.BrowserContext;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class JsScriptDiscoverTest extends PlaywrightPageTest {

  private static final String CANDIDATES = "button, a, input";

  @Override
  protected void configureContext(BrowserContext context) {
    context.addInitScript(JsScript.COMMON.source());
  }

  @Nested
  class Filtering {

    @ParameterizedTest(name = "{0}")
    @MethodSource("filteringCases")
    void shouldKeepOnlyVisibleEnabledElements(
        String name, String html, List<String> expectedTexts) {
      List<String> texts = discover(html).stream().map(e -> (String) e.get("text")).toList();

      assertEquals(expectedTexts, texts);
    }

    static Stream<Arguments> filteringCases() {
      return Stream.of(
          Arguments.of("no candidates", "<div>nothing</div>", List.of()),
          Arguments.of("keeps visible element", "<button>Save</button>", List.of("Save")),
          Arguments.of(
              "keeps document order",
              "<a href=\"https://example.com\">First</a><button>Second</button>",
              List.of("First", "Second")),
          Arguments.of(
              "skips display none",
              "<button>Shown</button><button style=\"display:none\">Gone</button>",
              List.of("Shown")),
          Arguments.of(
              "skips hidden attribute",
              "<button>Shown</button><button hidden>Gone</button>",
              List.of("Shown")),
          Arguments.of(
              "skips element inside hidden parent",
              "<button>Shown</button><div style=\"display:none\"><button>Gone</button></div>",
              List.of("Shown")),
          Arguments.of(
              "skips disabled element",
              "<button>Enabled</button><button disabled>Off</button>",
              List.of("Enabled")));
    }
  }

  @Nested
  class Fields {

    @ParameterizedTest(name = "{0}")
    @MethodSource("fieldCases")
    void shouldDescribeElement(String name, String html, String field, Object expected) {
      List<Map<String, Object>> found = discover(html);

      assertEquals(1, found.size(), "expected exactly one discovered element");
      assertEquals(expected, found.getFirst().get(field));
    }

    static Stream<Arguments> fieldCases() {
      return Stream.of(
          // identity
          Arguments.of("tag name is lowercase", "<BUTTON>Go</BUTTON>", "tagName", "button"),
          Arguments.of("id is copied", "<button id=\"save\">Go</button>", "id", "save"),
          Arguments.of("missing id is empty", "<button>Go</button>", "id", ""),
          Arguments.of(
              "stable id is recognized", "<button id=\"save\">Go</button>", "hasStableId", true),
          Arguments.of(
              "unstable id is recognized", "<button id=\"b-1\">Go</button>", "hasStableId", false),
          Arguments.of(
              "locator comes from chooseLocator",
              "<button id=\"save\">Go</button>",
              "locator",
              "button[id=\"save\"]"),

          // text
          Arguments.of(
              "text is normalized",
              "<button>  Add\n   to   basket </button>",
              "text",
              "Add to basket"),

          // href
          Arguments.of(
              "href of link",
              "<a href=\"https://example.com/login\">Login</a>",
              "href",
              "https://example.com/login"),
          Arguments.of("href of button is null", "<button>Go</button>", "href", null),

          // attributes
          Arguments.of(
              "aria-label is normalized",
              "<button aria-label=\"  Close   dialog \">x</button>",
              "ariaLabel",
              "Close dialog"),
          Arguments.of("missing aria-label is empty", "<button>Go</button>", "ariaLabel", ""),
          Arguments.of(
              "title is normalized", "<button title=\" Help \">?</button>", "title", "Help"),
          Arguments.of(
              "placeholder is normalized",
              "<input placeholder=\"  Your   email \">",
              "placeholder",
              "Your email"),
          Arguments.of(
              "value is normalized", "<input type=\"submit\" value=\"  Send  \">", "value", "Send"),
          Arguments.of(
              "value of link is empty", "<a href=\"https://example.com\">Go</a>", "value", ""),
          Arguments.of(
              "alt comes from image inside",
              "<a href=\"https://example.com\"><img alt=\" Logo \">Home</a>",
              "alt",
              "Logo"),
          Arguments.of("missing alt is empty", "<button>Go</button>", "alt", ""),
          Arguments.of(
              "type is copied", "<input type=\"submit\" value=\"Send\">", "type", "submit"),
          Arguments.of("missing type is null", "<button>Go</button>", "type", null));
    }
  }

  @Nested
  class Locators {

    @ParameterizedTest(name = "{0}")
    @MethodSource("locatorCases")
    void shouldKeepElementsWithoutUniqueLocator(String name, String html, List<String> expected) {
      List<String> locators = discover(html).stream().map(e -> (String) e.get("locator")).toList();

      assertEquals(expected, locators);
    }

    static Stream<Arguments> locatorCases() {
      return Stream.of(
          Arguments.of(
              "null locator is returned, not filtered",
              "<div><a>One</a></div><section><div><a>Two</a></div></section>",
              Arrays.asList(null, "section > div > a")));
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> discover(String html) {
    page.navigate(htmlUrl(html));
    return (List<Map<String, Object>>)
        page.locator(CANDIDATES).evaluateAll(JsScript.DISCOVER.source());
  }
}
