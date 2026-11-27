package wallhex.crawler.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import wallhex.crawler.model.CapturedRequest;

public class NetworkMonitorTest {

  private static final List<String> CAPTURED_TYPES = List.of("fetch", "xhr", "document");
  private static final long STEP_MS = 10;
  private static final long QUIET_MS = 50;
  private static final long CAP_MS = 500;

  private NetworkMonitor monitor;
  private Page page;

  @BeforeEach
  void setUp() {
    monitor = new NetworkMonitor(CAPTURED_TYPES, STEP_MS, QUIET_MS, CAP_MS);
    page = sleepingPage();
  }

  @Nested
  class OnRequest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"fetch", "xhr", "document"})
    void shouldBufferConfiguredType(String type) {
      Request request = request(type);

      List<CapturedRequest> captured = monitor.captureRequests(page, () -> startAndFinish(request));

      assertEquals(
          List.of(new CapturedRequest("http://app.test/" + type, "GET", null, type)), captured);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"image", "script", "stylesheet"})
    void shouldNotBufferOtherType(String type) {
      Request request = request(type);

      List<CapturedRequest> captured = monitor.captureRequests(page, () -> startAndFinish(request));

      assertEquals(List.of(), captured);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"image", "script", "fetch"})
    void shouldWaitUntilCapWhenRequestNeverFinishes(String type) {
      Request request = request(type);

      long elapsedMs =
          elapsedMs(() -> monitor.captureRequests(page, () -> monitor.onRequest(request)));

      assertTrue(elapsedMs >= CAP_MS, "elapsed " + elapsedMs + " ms, expected at least the cap");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"websocket", "eventsource"})
    void shouldIgnoreLongLivedConnection(String type) {
      Request request = request(type);

      List<CapturedRequest> captured = new ArrayList<>();
      long elapsedMs =
          elapsedMs(
              () ->
                  captured.addAll(monitor.captureRequests(page, () -> monitor.onRequest(request))));

      assertEquals(List.of(), captured);
      assertTrue(elapsedMs < CAP_MS, "elapsed " + elapsedMs + " ms, expected less than the cap");
    }
  }

  @Nested
  class OnRequestFinished {

    @Test
    void shouldStopWaitingWhenRequestFinishesDuringWait() {

      Request request = request("fetch");
      Page finishingPage = pageRunningOnCall(3, () -> monitor.onRequestFinished(request));

      long elapsedMs =
          elapsedMs(() -> monitor.captureRequests(finishingPage, () -> monitor.onRequest(request)));

      assertTrue(elapsedMs >= QUIET_MS, "elapsed " + elapsedMs + " ms, expected the quiet period");
      assertTrue(elapsedMs < CAP_MS, "elapsed " + elapsedMs + " ms, expected less than the cap");
    }

    @Test
    void shouldKeepWaitingWhenUnknownRequestFinishesDuringWait() {

      Request trackedRequest = request("fetch");
      Request unknownRequest = request("fetch");

      Page finishingPage = pageRunningOnCall(3, () -> monitor.onRequestFinished(unknownRequest));

      long elapsedMs =
          elapsedMs(
              () ->
                  monitor.captureRequests(finishingPage, () -> monitor.onRequest(trackedRequest)));

      assertTrue(elapsedMs >= CAP_MS, "elapsed " + elapsedMs + " ms, expected the cap period");
    }
  }

  @Nested
  class OnRequestFailed {

    @Test
    void shouldStopWaitingWhenRequestFailedDuringWait() {

      Request request = request("fetch");
      Page failingPage = pageRunningOnCall(3, () -> monitor.onRequestFailed(request));

      long elapsedMs =
          elapsedMs(() -> monitor.captureRequests(failingPage, () -> monitor.onRequest(request)));

      assertTrue(elapsedMs >= QUIET_MS, "elapsed " + elapsedMs + " ms, expected the quiet period");
      assertTrue(elapsedMs < CAP_MS, "elapsed " + elapsedMs + " ms, expected less than the cap");
    }
  }

  @Nested
  class CaptureRequests {

    @Test
    void shouldClearBufferBetweenActions() {

      Request requestA = request("fetch");
      Request requestB = request("document");
      Page finishingPage = pageRunningOnCall(3, () -> monitor.onRequestFinished(requestB));

      monitor.captureRequests(page, () -> startAndFinish(requestA));

      List<CapturedRequest> captured =
          monitor.captureRequests(finishingPage, () -> monitor.onRequest(requestB));

      assertEquals(
          List.of(new CapturedRequest("http://app.test/document", "GET", null, "document")),
          captured);
    }

    @Test
    void shouldKeepEarlierResultUnchangedByLaterAction() {

      Request requestA = request("fetch");
      Request requestB = request("document");

      List<CapturedRequest> firstResult =
          monitor.captureRequests(page, () -> startAndFinish(requestA));
      monitor.captureRequests(page, () -> startAndFinish(requestB));

      assertEquals(
          List.of(new CapturedRequest("http://app.test/fetch", "GET", null, "fetch")), firstResult);
    }

    @Test
    void shouldKeepRequestsInStartOrder() {

      Request document = request("document");
      Request xhr = request("xhr");
      Request fetch = request("fetch");

      List<CapturedRequest> captured =
          monitor.captureRequests(
              page,
              () -> {
                startAndFinish(document);
                startAndFinish(xhr);
                startAndFinish(fetch);
              });

      assertEquals(
          List.of("document", "xhr", "fetch"),
          captured.stream().map(CapturedRequest::resourceType).toList());
    }

    @Test
    void shouldReturnEmptyResultAfterQuietPeriodWhenActionSendsNoRequests() {

      List<CapturedRequest> captured = new ArrayList<>();
      long elapsedMs = elapsedMs(() -> captured.addAll(monitor.captureRequests(page, () -> {})));

      assertEquals(List.of(), captured);
      assertTrue(elapsedMs >= QUIET_MS, "elapsed " + elapsedMs + " ms, expected the quiet period");
      assertTrue(elapsedMs < CAP_MS, "elapsed " + elapsedMs + " ms, expected less than the cap");
    }

    @Test
    void shouldCaptureLateRequestWhenPreviousActivityIsOld() throws InterruptedException {

      Request earlier = request("document");
      Request late = request("fetch");

      monitor.captureRequests(page, () -> startAndFinish(earlier));
      Thread.sleep(QUIET_MS * 2);

      Page latePage = pageRunningOnCall(2, () -> startAndFinish(late));
      List<CapturedRequest> captured = monitor.captureRequests(latePage, () -> {});

      assertEquals(
          List.of(new CapturedRequest("http://app.test/fetch", "GET", null, "fetch")), captured);
    }
  }

  private void startAndFinish(Request request) {
    monitor.onRequest(request);
    monitor.onRequestFinished(request);
  }

  private static long elapsedMs(Runnable action) {
    long start = System.nanoTime();
    action.run();
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  private static Request request(String resourceType) {
    Request request = mock(Request.class);
    when(request.resourceType()).thenReturn(resourceType);
    when(request.url()).thenReturn("http://app.test/" + resourceType);
    when(request.method()).thenReturn("GET");
    return request;
  }

  private static Page sleepingPage() {
    Page page = mock(Page.class);
    doAnswer(
            invocation -> {
              double ms = invocation.getArgument(0);
              Thread.sleep((long) ms);
              return null;
            })
        .when(page)
        .waitForTimeout(anyDouble());
    return page;
  }

  private static Page pageRunningOnCall(int call, Runnable action) {
    Page page = mock(Page.class);
    AtomicInteger count = new AtomicInteger();

    doAnswer(
            invocation -> {
              if (count.incrementAndGet() == call) action.run();
              double ms = invocation.getArgument(0);
              Thread.sleep((long) ms);
              return null;
            })
        .when(page)
        .waitForTimeout(anyDouble());
    return page;
  }
}
