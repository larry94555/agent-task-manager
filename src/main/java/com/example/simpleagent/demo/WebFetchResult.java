package com.example.simpleagent.demo;

import java.time.Instant;

public class WebFetchResult {
    private final String requestedUrl;
    private final String finalUrl;
    private final int statusCode;
    private final String contentType;
    private final String title;
    private final String text;
    private final Instant retrievedAt;

    public WebFetchResult(String requestedUrl, String finalUrl, int statusCode, String contentType, String title, String text, Instant retrievedAt) {
        this.requestedUrl = requestedUrl;
        this.finalUrl = finalUrl;
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.title = title;
        this.text = text;
        this.retrievedAt = retrievedAt;
    }

    public String getRequestedUrl() { return requestedUrl; }
    public String getFinalUrl() { return finalUrl; }
    public int getStatusCode() { return statusCode; }
    public String getContentType() { return contentType; }
    public String getTitle() { return title; }
    public String getText() { return text; }
    public Instant getRetrievedAt() { return retrievedAt; }
}
