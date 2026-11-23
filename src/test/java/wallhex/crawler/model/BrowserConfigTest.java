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

  @Nested
  class Constructor {

    @ParameterizedTest(name = "{0}")
    @NullAndEmptySource
    void shouldRejectMissingSelectors(List<String> selectors) {
      assertThrows(IllegalArgumentException.class, () -> new BrowserConfig(selectors));
    }

    @Test
    void shouldKeepSelectorsInOrder() {
      BrowserConfig config = new BrowserConfig(List.of("button", "a[href]"));

      assertEquals(List.of("button", "a[href]"), config.clickableSelectors());
    }

    @Test
    void shouldCopyInputList() {
      List<String> source = new ArrayList<>(List.of("button"));

      BrowserConfig config = new BrowserConfig(source);
      source.add("a[href]");

      assertEquals(List.of("button"), config.clickableSelectors());
    }

    @Test
    void shouldExposeImmutableList() {
      BrowserConfig config = new BrowserConfig(List.of("button"));

      assertThrows(
          UnsupportedOperationException.class, () -> config.clickableSelectors().add("a[href]"));
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
  }
}
