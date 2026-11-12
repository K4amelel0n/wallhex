# Playwright for Java: crawler capabilities

Reference for the crawler investigation in [the Playwright spike](playwright-java-spike.md). These are available APIs and their limits, not proof that the crawler design is complete. The project currently depends on Playwright Java 1.64.0.

## Discovering page content

| Goal | API | What it provides |
| --- | --- | --- |
| Inspect links, forms, buttons, and fields | `page.locator(selector)` and `Locator` methods | Elements in the current browser DOM; attributes, text, and form controls can be inspected. |
| Capture the current HTML | `page.content()` | The main frame's serialized DOM, including changes made by JavaScript. |
| Capture the server's HTML response | `response.text()` on the document response | The response body as received over the network. It may differ from `page.content()`. |
| Inspect an iframe | `frame.content()` or frame locators | An iframe has a separate document; `page.content()` does not include its document contents. |
| Include open shadow roots | `page.content(new Page.ContentOptions().setIncludeShadow(true))` | Serializes open shadow roots too (available in 1.64.0); closed shadow roots remain inaccessible. |
| Capture visual state | `page.screenshot()` | Pixels as rendered by the browser, including information that HTML alone cannot express. |

The DOM is the browser's document after scripts run. A framework's *virtual DOM* is an internal framework structure; Playwright does not expose a universal virtual DOM API. Also, serialized HTML is not a complete snapshot of live state: read current input values with `locator.inputValue()` and checkbox or radio state with `locator.isChecked()`. Other state may live in browser storage or in the application's JavaScript memory.

Sources: [Page.content](https://playwright.dev/java/docs/api/class-page#content), [Frame.content](https://playwright.dev/java/docs/api/class-frame#content), [Locator](https://playwright.dev/java/docs/api/class-locator), [Response.text](https://playwright.dev/java/docs/api/class-response#text), [Screenshots](https://playwright.dev/java/docs/screenshots).

## Finding and clicking elements

| Goal | API | Limit |
| --- | --- | --- |
| Find likely click targets | `page.locator(selector)` for links, buttons, and clickable input types | A selector finds matching DOM elements, not every action a user could perform. Custom JavaScript controls may need other discovery rules. |
| Identify a specific control | `page.getByRole(role, options)` or a scoped `Locator` | Role and accessible name can describe the intended target; repeated names may still need a narrower locator. |
| Check whether a candidate can be used | `locator.isVisible()` and `locator.isEnabled()` | These checks describe the current state. The element may change before the click. |
| Perform the action | `locator.click()` | Playwright waits for one match and actionability checks, including visibility, stability, receiving pointer events, and enabled state. A timeout or ambiguous match is a failed action. |

A crawler should store enough information to locate a target again after returning to its source state. Keeping a
Playwright `Locator` alone does not define a replayable action across a new page or browser context. After clicking,
observe the URL, DOM, and relevant network events: a click can navigate, update the current page without navigation,
or leave the observed state unchanged. Discovery also needs a policy for which actions are safe to try.

Sources: [Locators](https://playwright.dev/java/docs/locators), [Locator.click](https://playwright.dev/java/docs/api/class-locator#click), [Auto-waiting and actionability](https://playwright.dev/java/docs/actionability).

## Observing network traffic

Register listeners before navigation or interaction; they only receive events that occur after registration. `Page` listeners observe one page. `BrowserContext` listeners can observe traffic across pages in that context, including popups.

| Event | Object | Useful data |
| --- | --- | --- |
| `onRequest` | `Request` | URL, method, headers, POST body (`postData()` or `postDataBuffer()`), resource type, navigation flag, and redirect links. |
| `onResponse` | `Response` | URL, status, status text, headers, body (`text()` or `body()`), and matching `Request` via `response.request()`. |
| `onRequestFinished` | `Request` | Indicates that the response body finished downloading. |
| `onRequestFailed` | `Request` | Network failure reason via `request.failure()`. |

The usual sequence is `request` → `response` (status and headers arrived) → `requestfinished` (body completed). A network error may produce `requestfailed` instead of a response. HTTP statuses such as 404 and 503 are still HTTP responses, so they do not count as `requestfailed`. A redirect finishes one request and starts another; use `redirectedFrom()` and `redirectedTo()` to follow the chain. `request.response()` gives the matching response if one arrived.

For a crawler, `resourceType()` can distinguish document navigation from scripts, images, `fetch`, and XHR traffic. Network events show requests made by the browser; they do not themselves describe every possible action a user could take. The DOM and network observations therefore answer different discovery questions.

Sources: [Request](https://playwright.dev/java/docs/api/class-request), [Response](https://playwright.dev/java/docs/api/class-response), [Page network events](https://playwright.dev/java/docs/api/class-page#onrequest), [BrowserContext network events](https://playwright.dev/java/docs/api/class-browsercontext#onrequest).

## Sessions, cookies, and authentication

`BrowserContext` is the unit of session isolation. Pages in one context share its cookies and storage. Separate contexts do not share cookies or storage, even when they use the same browser process. `browser.newPage()` creates a page in its own new context; use `browser.newContext()` and `context.newPage()` when session ownership needs to be explicit.

| Goal | API | Limit |
| --- | --- | --- |
| Inspect cookies | `context.cookies()` or `context.cookies(url)` | Includes name, value, domain, path, expiry, `HttpOnly`, `Secure`, and `SameSite`; these are credentials and should be handled accordingly. |
| Reuse authenticated browser state | `context.storageState()` and `new Browser.NewContextOptions().setStorageStatePath(path)` | Captures cookies and local storage; use `setIndexedDB(true)` when auth state is in IndexedDB. State can expire. |
| Inspect `sessionStorage` | `page.evaluate("JSON.stringify(sessionStorage)")` | It is scoped to an origin and tab and is not included in `storageState()`. |
| Start an independent session | `browser.newContext()` | The new context starts without the other context's login state unless state is explicitly supplied. |

Authentication may be represented by cookies, local storage, IndexedDB, or another application mechanism. Playwright can observe or restore browser-side state, but it cannot reveal the server's private session data merely from a session cookie. Saved storage state can contain reusable credentials; keep it out of version control.

Sources: [BrowserContext](https://playwright.dev/java/docs/api/class-browsercontext), [Authentication](https://playwright.dev/java/docs/auth), [Browser contexts and isolation](https://playwright.dev/docs/browser-contexts).

## Questions for the crawler spike

- Which differences should count as a new page state: URL, DOM, live form values, visible content, authentication identity, or some combination?
- At what point after navigation or interaction should a snapshot be taken so asynchronous content is represented consistently?
- Should network observations be collected per page or per context, especially when a page opens a popup?
- Which authenticated roles need separate contexts, and how will their saved state be protected and refreshed?
