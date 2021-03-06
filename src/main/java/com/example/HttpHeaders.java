// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.example;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * A collection of headers on an HTTP request or response.
 */
public class HttpHeaders implements Iterable<HttpHeader> {
    private Map<String, HttpHeader> headers;

    /**
     * Create an empty HttpHeaders instance.
     */
    public HttpHeaders() {
        this.headers = new HashMap<>();
    }

    /**
     * Create an HttpHeaders instance with the given size.
     */
    public HttpHeaders(int size) {
        this.headers = new HashMap<>(size);
    }

    /**
     * Create a HttpHeaders instance with the provided initial headers.
     *
     * @param headers the map of initial headers
     */
    public HttpHeaders(Map<String, String> headers) {
        this.headers = new HashMap<>(headers.size());
        for (final Map.Entry<String, String> header : headers.entrySet()) {
            this.set(header.getKey(), header.getValue());
        }
    }

    /**
     * Gets the number of headers in the collection.
     *
     * @return the number of headers in this collection.
     */
    public int size() {
        return headers.size();
    }

    /**
     * Set a header.
     * <p>
     * if header with same name already exists then the value will be overwritten. if value is null and header with
     * provided name already exists then it will be removed.
     *
     * @param name the name
     * @param value the value
     * @return this HttpHeaders
     */
    public HttpHeaders set(String name, String value) {
        final String headerKey = name.toLowerCase(Locale.ROOT);
        if (value == null) {
            headers.remove(headerKey);
        } else {
            headers.put(headerKey, new HttpHeader(name, value));
        }
        return this;
    }

    /**
     * Get the header value for the provided header name. Null will be returned if the header name isn't found.
     *
     * @param name the name of the header to look for
     * @return The String value of the header, or null if the header isn't found
     */
    public String value(String name) {
        final HttpHeader header = getHeader(name);
        return header == null ? null : header.value();
    }

    /**
     * Get the header values for the provided header name. Null will be returned if the header name isn't found.
     *
     * @param name the name of the header to look for
     * @return the values of the header, or null if the header isn't found
     */
    public String[] values(String name) {
        final HttpHeader header = getHeader(name);
        return header == null ? null : header.values();
    }

    private HttpHeader getHeader(String headerName) {
        final String headerKey = headerName.toLowerCase(Locale.ROOT);
        return headers.get(headerKey);
    }

    /**
     * Get {@link Map} representation of the HttpHeaders collection.
     *
     * @return the headers as map
     */
    public Map<String, String> toMap() {
        final Map<String, String> result = new HashMap<>(headers.size());
        for (final HttpHeader header : headers.values()) {
            result.put(header.name(), header.value());
        }
        return result;
    }

    @Override
    public Iterator<HttpHeader> iterator() {
        return headers.values().iterator();
    }
}
