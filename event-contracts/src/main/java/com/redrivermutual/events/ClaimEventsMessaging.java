package com.redrivermutual.events;

/**
 * Broker-level part of the contract: where claim events are published and how they are labelled.
 *
 * <p>The publisher owns the exchange. Each consumer owns its own queue (and dead-letter queue)
 * and binds it to the exchange with the routing key it needs, so adding a consumer never
 * changes the Claims API.
 */
public final class ClaimEventsMessaging {

    /** Durable topic exchange for every claim event. */
    public static final String EXCHANGE = "claims.events";

    /** Routing key of {@link ClaimSubmittedEvent}. */
    public static final String CLAIM_SUBMITTED_ROUTING_KEY = "claim.submitted";

    /** AMQP message headers. The AMQP {@code message-id} property carries the event ID. */
    public static final String HEADER_CORRELATION_ID = "x-correlation-id";
    public static final String HEADER_EVENT_TYPE = "x-event-type";
    public static final String HEADER_EVENT_VERSION = "x-event-version";

    private ClaimEventsMessaging() {
    }
}
