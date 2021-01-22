// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.example;

import reactor.netty.http.client.HttpClientState;

import java.time.Instant;

/**
 * Represents the timeline of various events in the lifetime of a reactor netty request response.
 * <p>
 * <p><ul>
 * <li>{@link HttpClientState#CONNECTED},
 * <li>{@link HttpClientState#ACQUIRED},
 * <li>{@link HttpClientState#CONFIGURED},
 * <li>{@link HttpClientState#REQUEST_SENT},
 * <li>{@link HttpClientState#RESPONSE_RECEIVED},
 * </ul></p>
 */
public final class ReactorNettyRequestRecord {

    private volatile Instant timeCreated;
    private volatile Instant timeConnected;
    private volatile Instant timeConfigured;
    private volatile Instant timeSent;
    private volatile Instant timeReceived;
    private volatile Instant timeCompleted;

    /**
     * Gets request created instant.
     *
     * @return
     */
    public Instant timeCreated() {
        return this.timeCreated;
    }

    /**
     * Get connection established instant.
     *
     * @return timeConnected
     */
    public Instant timeConnected() {
        return this.timeConnected;
    }

    /**
     * Get connection configured instant.
     *
     * @return timeConfigured
     */
    public Instant timeConfigured() {
        return this.timeConfigured;
    }

    /**
     * Gets request sent instant.
     *
     * @return timeSent
     */
    public Instant timeSent() {
        return this.timeSent;
    }

    /**
     * Gets response received instant.
     *
     * @return timeReceived
     */
    public Instant timeReceived() {
        return this.timeReceived;
    }

    /**
     * Gets request completed  instant.
     *
     * @return timeCompleted
     */
    public Instant timeCompleted() {
        return this.timeCompleted;
    }

    /**
     * Sets request created instant.
     *
     * @param timeCreated
     */
    public void setTimeCreated(Instant timeCreated) {
        this.timeCreated = timeCreated;
    }

    /**
     * Sets connection established instant.
     *
     * @param timeConnected
     */
    public void setTimeConnected(Instant timeConnected) {
        this.timeConnected = timeConnected;
    }

    /**
     * Sets connection configured instant.
     *
     * @param timeConfigured
     */
    public void setTimeConfigured(Instant timeConfigured) {
        this.timeConfigured = timeConfigured;
    }

    /**
     * Sets request sent instant.
     *
     * @param timeSent
     */
    public void setTimeSent(Instant timeSent) {
        this.timeSent = timeSent;
    }

    /**
     * Sets response received instant.
     *
     * @param timeReceived
     */
    public void setTimeReceived(Instant timeReceived) {
        this.timeReceived = timeReceived;
    }

    /**
     * Sets request completed instant.
     *
     * @param timeCompleted
     */
    public void setTimeCompleted(Instant timeCompleted) {
        this.timeCompleted = timeCompleted;
    }
}
