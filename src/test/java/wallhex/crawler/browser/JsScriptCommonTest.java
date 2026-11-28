package wallhex.crawler.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.BrowserContext;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class JsScriptCommonTest extends PlaywrightPageTest {

  @Override
  protected void configureContext(BrowserContext context) {
    context.addInitScript(JsScript.COMMON.source());
  }

  @Test
  void sanityCheck() {
    page.navigate(htmlUrl("<div></div>"));
    assertEquals("object", page.evaluate("() => typeof document._wallhex"));
  }

  @Nested
  class HasStableId {
    @ParameterizedTest(name = "id=\"{0}\"")
    @ValueSource(strings = {"1-fdsafa", "1adf", "111,fds342", "1-fsa-20"})
    void shouldRejectIdWithDigits(String id) {
      assertFalse(hasStableIdFor(id));
    }

    @ParameterizedTest(name = "id=\"{0}\"")
    @ValueSource(strings = {"", "   ", "\t"})
    void shouldRejectBlankId(String id) {
      assertFalse(hasStableIdFor(id));
    }

    @ParameterizedTest(name = "id=\"{0}\"")
    @ValueSource(strings = {"login-form", "submitButton", "main_nav"})
    void shouldAcceptIdWithoutDigits(String id) {
      assertTrue(hasStableIdFor(id));
    }

    private boolean hasStableIdFor(String id) {
      return (Boolean)
          page.evaluate(
              """
              (id) => {
                const el = document.createElement("div");
                el.id = id;
                return document._wallhex.hasStableId(el);
              }
              """,
              id);
    }
  }

  @Nested
  class NormalizeText {

    @ParameterizedTest(name = "\"{0}\"")
    @MethodSource("normalizeTextCases")
    void shouldNormalize(String name, String value, String expected) {
      assertEquals(expected, page.evaluate("(val)=> document._wallhex.normalizeText(val)", value));
    }

    static Stream<Arguments> normalizeTextCases() {
      String a254 = "a".repeat(254);
      String a255 = "a".repeat(255);

      return Stream.of(
          Arguments.of("null becomes empty", null, ""),
          Arguments.of("empty stays empty", "", ""),
          Arguments.of("only whitespace becomes empty", " \t\n ", ""),
          Arguments.of("plain text unchanged", "Login", "Login"),
          Arguments.of("trims leading and trailing", "  Login  ", "Login"),
          Arguments.of("collapses inner spaces", "Add   to  basket", "Add to basket"),
          Arguments.of("collapses newlines and tabs", "Add\n\tto\r\nbasket", "Add to basket"),
          Arguments.of(
              "collapses non-breaking space", "Add\u00A0to\u00A0\u00A0basket", "Add to basket"),
          Arguments.of("keeps 254 chars", a254, a254),
          Arguments.of("keeps 255 chars", a255, a255),
          Arguments.of("cuts 256 chars to 255", a255 + "a", a255),
          Arguments.of("trims before cutting", "   " + a255 + "   ", a255),
          Arguments.of("collapses before cutting", "a" + " ".repeat(300) + "b", "a b"),
          Arguments.of("cut can leave trailing space", a254 + " b", a254 + " "));
    }
  }

  @Nested
  class CssPath {

    @ParameterizedTest(name = "{0}")
    @MethodSource("cssPathCases")
    void shouldBuildPath(String name, String html, String expected) {
      assertEquals(expected, callOnTarget("cssPath", html));
    }

    static Stream<Arguments> cssPathCases() {
      return Stream.of(
          Arguments.of("single element under body", "<button data-target></button>", "button"),
          Arguments.of(
              "indexes same-tag siblings",
              "<button></button><button data-target></button>",
              "button:nth-of-type(2)"),
          Arguments.of(
              "ignores siblings with other tags",
              "<span></span><button data-target></button>",
              "button"),
          Arguments.of(
              "joins ancestors with >",
              "<div><span><a data-target></a></span></div>",
              "div > span > a"),
          Arguments.of(
              "indexes on every level",
              "<div></div><div><p></p><p><a data-target></a></p></div>",
              "div:nth-of-type(2) > p:nth-of-type(2) > a"),
          Arguments.of(
              "stops at ancestor with stable id",
              "<div id=\"menu\"><ul><li data-target></li></ul></div>",
              "#menu > ul > li"),
          Arguments.of("uses own stable id", "<button id=\"save\" data-target></button>", "#save"),
          Arguments.of(
              "skips ancestor with unstable id",
              "<div id=\"row-12\"><a data-target></a></div>",
              "div > a"),
          Arguments.of(
              "escapes id", "<div id=\"main.nav\"><a data-target></a></div>", "#main\\.nav > a"));
    }
  }

  @Nested
  class ChooseLocator {

    @ParameterizedTest(name = "{0}")
    @MethodSource("chooseLocatorCases")
    void shouldChooseLocator(String name, String html, String expected) {
      assertEquals(expected, callOnTarget("chooseLocator", html));
    }

    static Stream<Arguments> chooseLocatorCases() {
      return Stream.of(
          Arguments.of(
              "prefers stable id",
              "<button id=\"save\" name=\"s\" data-target></button>",
              "button[id=\"save\"]"),
          Arguments.of(
              "skips unstable id",
              "<button id=\"btn-1\" data-testid=\"save\" data-target></button>",
              "button[data-testid=\"save\"]"),
          Arguments.of("uses name", "<input name=\"email\" data-target>", "input[name=\"email\"]"),
          Arguments.of(
              "uses aria-label",
              "<button aria-label=\"Close\" data-target></button>",
              "button[aria-label=\"Close\"]"),
          Arguments.of(
              "uses href", "<a href=\"/login\" data-target>Login</a>", "a[href=\"\\/login\"]"),
          Arguments.of(
              "skips attribute that is not unique",
              "<button name=\"go\"></button><button name=\"go\" aria-label=\"Next\" data-target></button>",
              "button[aria-label=\"Next\"]"),
          Arguments.of(
              "tag makes attribute unique",
              "<a name=\"x\"></a><button name=\"x\" data-target></button>",
              "button[name=\"x\"]"),
          Arguments.of(
              "falls back to css path",
              "<button></button><button data-target></button>",
              "button:nth-of-type(2)"),
          Arguments.of(
              "unstable id falls back to css path",
              "<div><button id=\"b-1\" data-target></button></div>",
              "div > button"),
          Arguments.of(
              "returns null when path is not unique",
              "<div><a data-target></a></div><section><div><a></a></div></section>",
              null));
    }
  }

  private Object callOnTarget(String function, String html) {
    page.navigate(htmlUrl(html));
    return page.evaluate(
        "(fn) => document._wallhex[fn](document.querySelector('[data-target]'))", function);
  }
}
