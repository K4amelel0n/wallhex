package wallhex.crawler.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

public class BrowserConfigTest {

  private static final List<String> SELECTORS = List.of("button");
  private static final List<String> RESOURCE_TYPES = List.of("fetch", "xhr", "document");

  @Nested
  class Constructor {

    @ParameterizedTest(name = "{0}")
    @NullAndEmptySource
    void shouldRejectMissingSelectors(List<String> selectors) {
      assertThrows(
          IllegalArgumentException.class, () -> new BrowserConfig(selectors, RESOURCE_TYPES));
    }

    @Test
    void shouldKeepSelectorsInOrder() {
      BrowserConfig config = new BrowserConfig(List.of("button", "a[href]"), RESOURCE_TYPES);

      assertEquals(List.of("button", "a[href]"), config.clickableSelectors());
    }

    @Test
    void shouldCopyInputList() {
      List<String> source = new ArrayList<>(List.of("button"));

      BrowserConfig config = new BrowserConfig(source, RESOURCE_TYPES);
      source.add("a[href]");

      assertEquals(List.of("button"), config.clickableSelectors());
    }

    @Test
    void shouldExposeImmutableList() {
      BrowserConfig config = new BrowserConfig(List.of("button"), RESOURCE_TYPES);

      assertThrows(
          UnsupportedOperationException.class, () -> config.clickableSelectors().add("a[href]"));
    }

    @Test
    void shouldRejectNullResourceTypes() {
      assertThrows(IllegalArgumentException.class, () -> new BrowserConfig(SELECTORS, null));
    }

    @Test
    void shouldAcceptEmptyResourceTypes() {
      BrowserConfig config = new BrowserConfig(SELECTORS, List.of());

      assertEquals(List.of(), config.requestResourceTypes());
    }

    @Test
    void shouldCopyResourceTypes() {
      List<String> source = new ArrayList<>(List.of("fetch"));

      BrowserConfig config = new BrowserConfig(SELECTORS, source);
      source.add("xhr");

      assertEquals(List.of("fetch"), config.requestResourceTypes());
    }

    @Test
    void shouldExposeImmutableResourceTypes() {
      BrowserConfig config = new BrowserConfig(SELECTORS, RESOURCE_TYPES);

      assertThrows(
          UnsupportedOperationException.class, () -> config.requestResourceTypes().add("image"));
    }
  }

  @Nested
  class Defaults {

    @Test
    void shouldContainButtonLinkAndInputSelectors() {
      assertEquals(
          List.of("button", "a[href]", "input[type=\"submit\"]", "input[type=\"button\"]"),
          BrowserConfig.defaults().clickableSelectors());
    }

    @Test
    void shouldContainFetchXhrAndDocumentTypes() {
      assertEquals(
          List.of("fetch", "xhr", "document"), BrowserConfig.defaults().requestResourceTypes());
    }
  }
}
