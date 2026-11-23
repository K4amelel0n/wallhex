package wallhex.crawler.browser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public enum JsScript {
  COMMON("/js/wallhex-common.js"),
  DISCOVER("/js/discover-clickables.js");

  private final String path;

  JsScript(String path) {
    this.path = path;
  }

  String source() {
    try (InputStream in = JsScript.class.getResourceAsStream(path)) {
      if (in == null) throw new IllegalStateException("Missing script: " + path);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
