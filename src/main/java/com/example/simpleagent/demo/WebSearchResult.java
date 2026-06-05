package com.example.simpleagent.demo;

public class WebSearchResult {
    private final String title;
    private final String url;
    private final String snippet;

    public WebSearchResult(String title, String url, String snippet) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
    }

    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getSnippet() { return snippet; }
}
