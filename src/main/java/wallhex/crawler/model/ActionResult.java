package wallhex.crawler.model;

import java.util.List;

public record ActionResult(List<CapturedRequest> requests) {

  public ActionResult {
    requests = List.copyOf(requests);
  }
}
