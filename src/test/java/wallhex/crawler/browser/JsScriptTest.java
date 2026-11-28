package wallhex.crawler.browser;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class JsScriptTest {

  @ParameterizedTest
  @EnumSource(JsScript.class)
  void sourceOfEachScriptIsNonEmpty(JsScript script) {
    assertFalse(script.source().isBlank());
  }
}
