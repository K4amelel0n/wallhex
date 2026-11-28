package wallhex.crawler.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;

public class BrowserConfigTest {

  private static final List<String> SELECTORS = List.of("button");
  private static final List<String> RESOURCE_TYPES = List.of("fetch", "xhr", "document");
  private static final Duration TIMEOUT = Duration.ofSeconds(1);

  @Nested
  class Constructor {

    @ParameterizedTest(name = "{0}")
    @NullAndEmptySource
    void shouldRejectMissingSelectors(List<String> selectors) {
      assertThrows(
          IllegalArgumentException.class,
          () -> new BrowserConfig(selectors, RESOURCE_TYPES, TIMEOUT));
    }

    @Test
    void shouldKeepSelectorsInOrder() {
      BrowserConfig config =
          new BrowserConfig(List.of("button", "a[href]"), RESOURCE_TYPES, TIMEOUT);

      assertEquals(List.of("button", "a[href]"), config.clickableSelectors());
    }

    @Test
    void shouldCopyInputList() {
      List<String> source = new ArrayList<>(List.of("button"));

      BrowserConfig config = new BrowserConfig(source, RESOURCE_TYPES, TIMEOUT);
      source.add("a[href]");

      assertEquals(List.of("button"), config.clickableSelectors());
    }

    @Test
    void shouldExposeImmutableList() {
      BrowserConfig config = new BrowserConfig(List.of("button"), RESOURCE_TYPES, TIMEOUT);

      assertThrows(
          UnsupportedOperationException.class, () -> config.clickableSelectors().add("a[href]"));
    }

    @Test
    void shouldRejectNullResourceTypes() {
      assertThrows(
          IllegalArgumentException.class, () -> new BrowserConfig(SELECTORS, null, TIMEOUT));
    }

    @Test
    void shouldAcceptEmptyResourceTypes() {
      BrowserConfig config = new BrowserConfig(SELECTORS, List.of(), TIMEOUT);

      assertEquals(List.of(), config.requestResourceTypes());
    }

    @Test
    void shouldCopyResourceTypes() {
      List<String> source = new ArrayList<>(List.of("fetch"));

      BrowserConfig config = new BrowserConfig(SELECTORS, source, TIMEOUT);
      source.add("xhr");

      assertEquals(List.of("fetch"), config.requestResourceTypes());
    }

    @Test
    void shouldExposeImmutableResourceTypes() {
      BrowserConfig config = new BrowserConfig(SELECTORS, RESOURCE_TYPES, TIMEOUT);

      assertThrows(
          UnsupportedOperationException.class, () -> config.requestResourceTypes().add("image"));
    }

    @ParameterizedTest(name = "{0}")
    @NullSource
    @MethodSource("wallhex.crawler.model.BrowserConfigTest#nonPositiveTimeouts")
    void shouldRejectNonPositiveClickTimeout(Duration timeout) {
      assertThrows(
          IllegalArgumentException.class,
          () -> new BrowserConfig(SELECTORS, RESOURCE_TYPES, timeout));
    }
  }

  @Nested
  class WithClickableSelectors {

    @Test
    void shouldReplaceOnlySelectors() {
      BrowserConfig config = new BrowserConfig(SELECTORS, RESOURCE_TYPES, TIMEOUT);

      BrowserConfig changed = config.withClickableSelectors(List.of("a[href]"));

      assertEquals(new BrowserConfig(List.of("a[href]"), RESOURCE_TYPES, TIMEOUT), changed);
      assertEquals(SELECTORS, config.clickableSelectors());
    }

    @Test
    void shouldValidateNewSelectors() {
      BrowserConfig config = BrowserConfig.defaults();

      assertThrows(IllegalArgumentException.class, () -> config.withClickableSelectors(List.of()));
    }
  }

  @Nested
  class WithClickTimeout {

    @Test
    void shouldReplaceOnlyClickTimeout() {
      BrowserConfig config = new BrowserConfig(SELECTORS, RESOURCE_TYPES, TIMEOUT);

      BrowserConfig changed = config.withClickTimeout(Duration.ofMillis(200));

      assertEquals(new BrowserConfig(SELECTORS, RESOURCE_TYPES, Duration.ofMillis(200)), changed);
      assertEquals(TIMEOUT, config.clickTimeout());
    }

    @Test
    void shouldValidateNewClickTimeout() {
      BrowserConfig config = BrowserConfig.defaults();

      assertThrows(IllegalArgumentException.class, () -> config.withClickTimeout(Duration.ZERO));
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

    @Test
    void shouldUseFifteenSecondClickTimeout() {
      assertEquals(Duration.ofSeconds(15), BrowserConfig.defaults().clickTimeout());
    }
  }

  static Stream<Duration> nonPositiveTimeouts() {
    return Stream.of(Duration.ZERO, Duration.ofMillis(-1));
  }
}
