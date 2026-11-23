package wallhex.crawler.browser;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class ElementIdentity {

  private static final String SEPARATOR = "|";

  private ElementIdentity() {}

  public static String identityKey(String pageUrl, ObservedElement element) {
    return String.join(
        SEPARATOR,
        normalizeUrl(pageUrl),
        element.tagName(),
        element.hasStableId() ? element.id() : "",
        normalizeUrl(element.href()),
        element.accessibleName());
  }

  public static String normalizeUrl(String url) {
    if (url == null || url.isEmpty()) return "";

    int hashIndex = url.indexOf('#');
    if (hashIndex < 0) return stripQueryValues(url);

    String beforeHash = url.substring(0, hashIndex);
    String fragment = url.substring(hashIndex + 1);
    return stripQueryValues(beforeHash) + "#" + stripQueryValues(fragment);
  }

  private static String stripQueryValues(String part) {
    int queryIndex = part.indexOf('?');
    if (queryIndex < 0) return part;

    String parameterNames =
        Arrays.stream(part.substring(queryIndex + 1).split("&"))
            .filter(parameter -> !parameter.isEmpty())
            .map(parameter -> parameter.split("=", 2)[0])
            .collect(Collectors.joining("&"));

    return part.substring(0, queryIndex + 1) + parameterNames;
  }
}
