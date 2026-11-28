package wallhex.crawler.model;

import java.util.List;

public record BrowserObservation(
    String url, String dom, List<ElementTarget> interactionCandidates) {}
