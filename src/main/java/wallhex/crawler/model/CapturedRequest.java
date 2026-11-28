package wallhex.crawler.model;

public record CapturedRequest(String url, String method, String body, String resourceType) {}
