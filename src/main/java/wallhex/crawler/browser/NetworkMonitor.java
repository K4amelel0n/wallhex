package wallhex.crawler.browser;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import wallhex.crawler.model.CapturedRequest;

class NetworkMonitor {

  private final List<String> requestResourceTypes;
  private final List<String> ignoredResourceTypes = List.of("websocket", "eventsource");
  private final Set<Request> requests = new HashSet<>();
  private final List<CapturedRequest> requestsBuffer = new ArrayList<>();
  private long lastActivityNanos = System.nanoTime();

  private final long stepMs;
  private final long quietMs;
  private final long capMs;

  public NetworkMonitor(List<String> requestResourceTypes) {
    this(requestResourceTypes, 50, 500, 5000);
  }

  public NetworkMonitor(List<String> requestResourceTypes, long stepMs, long quietMs, long capMs) {
    this.requestResourceTypes = requestResourceTypes;
    this.stepMs = stepMs;
    this.quietMs = quietMs;
    this.capMs = capMs;
  }

  public List<CapturedRequest> captureRequests(Page page, Runnable action) {

    requestsBuffer.clear();
    action.run();

    lastActivityNanos = System.nanoTime();
    long deadlineNanos = lastActivityNanos + TimeUnit.MILLISECONDS.toNanos(capMs);

    while (System.nanoTime() < deadlineNanos) {
      page.waitForTimeout(stepMs);

      long quietForMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastActivityNanos);
      if (requests.isEmpty() && quietForMs >= quietMs) {
        break;
      }
    }

    return requestsBuffer.stream().toList();
  }

  public void onRequest(Request request) {
    if (!ignoredResourceTypes.contains(request.resourceType())) {
      requests.add(request);
      lastActivityNanos = System.nanoTime();
    }
    if (this.requestResourceTypes.contains(request.resourceType())) {
      requestsBuffer.add(toCapturedRequest(request));
    }
  }

  public void onRequestFinished(Request request) {
    requestEnded(request);
  }

  public void onRequestFailed(Request request) {
    requestEnded(request);
  }

  private void requestEnded(Request request) {
    if (requests.remove(request)) {
      lastActivityNanos = System.nanoTime();
    }
  }

  private static CapturedRequest toCapturedRequest(Request request) {
    return new CapturedRequest(
        request.url(), request.method(), request.postData(), request.resourceType());
  }
}
