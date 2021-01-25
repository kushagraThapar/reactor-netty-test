// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.example;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.Connection;
import reactor.netty.ConnectionObserver;
import reactor.netty.NettyOutbound;
import reactor.netty.channel.ChannelOperations;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.http.client.HttpClientState;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.context.Context;

import javax.net.ssl.SSLException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * HttpClient that is implemented using reactor-netty.
 */
class ReactorNettyClient {

    private static final int DEFAULT_MAX_HTTP_INITIAL_LINE_LENGTH = 4096; //4KB
    private static final int DEFAULT_MAX_HTTP_CHUNK_SIZE_IN_BYTES = 8192; //8KB
    private static final int DEFAULT_MAX_HTTP_REQUEST_HEADER_SIZE = 32 * 1024; //32 KB
    private static final String REACTOR_NETTY_REQUEST_RECORD_KEY = "reactorNettyRequestRecordKey";

    private static final Logger logger = LoggerFactory.getLogger(ReactorNettyClient.class.getSimpleName());

    private reactor.netty.http.client.HttpClient httpClient;
    private ConnectionProvider connectionProvider;

    private ReactorNettyClient() {
    }

    /**
     * Creates ReactorNettyClient with {@link ConnectionProvider}.
     */
    public static ReactorNettyClient createWithConnectionProvider(ConnectionProvider connectionProvider) {
        ReactorNettyClient reactorNettyClient = new ReactorNettyClient();
        reactorNettyClient.connectionProvider = connectionProvider;
        reactorNettyClient.httpClient = HttpClient
            .create(connectionProvider)
            .observe(getConnectionObserver())
            .resolver(DefaultAddressResolverGroup.INSTANCE);
        reactorNettyClient.configureChannelPipelineHandlers();
        return reactorNettyClient;
    }

    private void configureChannelPipelineHandlers() {

        this.httpClient.secure(sslContextSpec -> sslContextSpec.sslContext(sslContextInit()))
                       .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 45000)
                       .httpResponseDecoder(httpResponseDecoderSpec ->
                           httpResponseDecoderSpec.maxInitialLineLength(DEFAULT_MAX_HTTP_INITIAL_LINE_LENGTH)
                                                  .maxHeaderSize(DEFAULT_MAX_HTTP_REQUEST_HEADER_SIZE)
                                                  .maxChunkSize(DEFAULT_MAX_HTTP_CHUNK_SIZE_IN_BYTES)
                                                  .validateHeaders(true));
    }

    private static ConnectionObserver getConnectionObserver() {
        return (conn, state) -> {
            Instant time = Instant.now();

            if (state.equals(HttpClientState.CONNECTED) || state.equals(HttpClientState.ACQUIRED)) {
                if (conn instanceof ConnectionObserver) {
                    ConnectionObserver observer = (ConnectionObserver) conn;
                    ReactorNettyRequestRecord requestRecord =
                        observer.currentContext().getOrDefault(REACTOR_NETTY_REQUEST_RECORD_KEY, null);
                    if (requestRecord == null) {
                        throw new IllegalStateException("ReactorNettyRequestRecord not found in context");
                    }
                    requestRecord.setTimeConnected(time);
                }
            } else if (state.equals(HttpClientState.CONFIGURED)) {
                if (conn instanceof ChannelOperations) {
                    ChannelOperations observer = (ChannelOperations) conn;
                    ReactorNettyRequestRecord requestRecord =
                        observer.currentContext().getOrDefault(REACTOR_NETTY_REQUEST_RECORD_KEY, null);
                    if (requestRecord == null) {
                        throw new IllegalStateException("ReactorNettyRequestRecord not found in context");
                    }
                    requestRecord.setTimeConfigured(time);
                }
            } else if (state.equals(HttpClientState.REQUEST_SENT)) {
                if (conn instanceof ChannelOperations) {
                    ChannelOperations observer = (ChannelOperations) conn;
                    ReactorNettyRequestRecord requestRecord =
                        observer.currentContext().getOrDefault(REACTOR_NETTY_REQUEST_RECORD_KEY, null);
                    if (requestRecord == null) {
                        throw new IllegalStateException("ReactorNettyRequestRecord not found in context");
                    }
                    requestRecord.setTimeSent(time);
                }
            } else if (state.equals(HttpClientState.RESPONSE_RECEIVED)) {
                if (conn instanceof ChannelOperations) {
                    ChannelOperations observer = (ChannelOperations) conn;
                    ReactorNettyRequestRecord requestRecord =
                        observer.currentContext().getOrDefault(REACTOR_NETTY_REQUEST_RECORD_KEY, null);
                    if (requestRecord == null) {
                        throw new IllegalStateException("ReactorNettyRequestRecord not found in context");
                    }
                    requestRecord.setTimeReceived(time);
                }
            }
        };
    }

    private SslContext sslContextInit() {
        try {
            SslProvider sslProvider = SslContext.defaultClientProvider();
            return SslContextBuilder.forClient().sslProvider(sslProvider).build();
        } catch (SSLException sslException) {
            logger.error("Fatal error cannot instantiate ssl context due to {}", sslException.getMessage(),
                sslException);
            throw new IllegalStateException(sslException);
        }
    }

    public Mono<HttpResponse> send(final HttpRequest request, Duration responseTimeout) {
        Objects.requireNonNull(request.httpMethod());
        Objects.requireNonNull(request.uri());

        final AtomicReference<ReactorNettyHttpResponse> responseReference = new AtomicReference<>();

        return this.httpClient
            .keepAlive(true)
            .responseTimeout(responseTimeout)
            .request(HttpMethod.valueOf(request.httpMethod().toString()))
            .uri(request.uri().toString())
            .send(bodySendDelegate(request))
            .responseConnection((reactorNettyResponse, reactorNettyConnection) -> {
                HttpResponse httpResponse = new ReactorNettyHttpResponse(reactorNettyResponse,
                    reactorNettyConnection).withRequest(request);
                responseReference.set((ReactorNettyHttpResponse) httpResponse);
                return Mono.just(httpResponse);
            })
            .doOnCancel(() -> {
                ReactorNettyHttpResponse reactorNettyHttpResponse = responseReference.get();
                if (reactorNettyHttpResponse != null) {
                    reactorNettyHttpResponse.releaseOnNotSubscribedResponse(ReactorNettyResponseState.CANCELLED);
                }
            })
            .onErrorMap(throwable -> {
                ReactorNettyHttpResponse reactorNettyHttpResponse = responseReference.get();
                if (reactorNettyHttpResponse != null) {
                    reactorNettyHttpResponse.releaseOnNotSubscribedResponse(ReactorNettyResponseState.ERROR);
                }
                return throwable;
            })
            .contextWrite(Context.of(REACTOR_NETTY_REQUEST_RECORD_KEY, request.reactorNettyRequestRecord()))
            .single();
    }

    /**
     * Delegate to send the request content.
     *
     * @param restRequest the Rest request contains the body to be sent
     * @return a delegate upon invocation sets the request body in reactor-netty outbound object
     */
    private static BiFunction<HttpClientRequest, NettyOutbound, Publisher<Void>> bodySendDelegate(final HttpRequest restRequest) {
        return (reactorNettyRequest, reactorNettyOutbound) -> {
            for (HttpHeader header : restRequest.headers()) {
                reactorNettyRequest.header(header.name(), header.value());
            }
            if (restRequest.body() != null) {
                return reactorNettyOutbound.sendByteArray(restRequest.body());
            } else {
                return reactorNettyOutbound;
            }
        };
    }

    public void shutdown() {
        if (this.connectionProvider != null) {
            this.connectionProvider.dispose();
        }
    }

    private static class ReactorNettyHttpResponse extends HttpResponse {

        private final AtomicReference<ReactorNettyResponseState> state =
            new AtomicReference<>(ReactorNettyResponseState.NOT_SUBSCRIBED);

        private final HttpClientResponse reactorNettyResponse;
        private final Connection reactorNettyConnection;

        ReactorNettyHttpResponse(HttpClientResponse reactorNettyResponse, Connection reactorNettyConnection) {
            this.reactorNettyResponse = reactorNettyResponse;
            this.reactorNettyConnection = reactorNettyConnection;
        }

        @Override
        public int statusCode() {
            return reactorNettyResponse.status().code();
        }

        @Override
        public String headerValue(String name) {
            return reactorNettyResponse.responseHeaders().get(name);
        }

        @Override
        public HttpHeaders headers() {
            HttpHeaders headers = new HttpHeaders(reactorNettyResponse.responseHeaders().size());
            reactorNettyResponse.responseHeaders().forEach(e -> headers.set(e.getKey(), e.getValue()));
            return headers;
        }

        @Override
        public Flux<ByteBuf> body() {
            return bodyIntern()
                .doOnSubscribe(this::updateSubscriptionState);
        }

        @Override
        public Mono<byte[]> bodyAsByteArray() {
            return bodyIntern().aggregate()
                               .asByteArray()
                               .doOnSubscribe(this::updateSubscriptionState);
        }

        @Override
        public Mono<String> bodyAsString() {
            return bodyIntern().aggregate()
                               .asString()
                               .doOnSubscribe(this::updateSubscriptionState);
        }

        @Override
        public Mono<String> bodyAsString(Charset charset) {
            return bodyIntern().aggregate()
                               .asString(charset)
                               .doOnSubscribe(this::updateSubscriptionState);
        }

        private ByteBufFlux bodyIntern() {
            return reactorNettyConnection.inbound().receive();
        }

        @Override
        Connection internConnection() {
            return reactorNettyConnection;
        }

        private void updateSubscriptionState(Subscription subscription) {
            if (this.state.compareAndSet(ReactorNettyResponseState.NOT_SUBSCRIBED,
                ReactorNettyResponseState.SUBSCRIBED)) {
                return;
            }
            // https://github.com/reactor/reactor-netty/issues/503
            // FluxReceive rejects multiple subscribers, but not after a cancel().
            // Subsequent subscribers after cancel() will not be rejected, but will hang instead.
            // So we need to reject ones in cancelled state.
            if (this.state.get() == ReactorNettyResponseState.CANCELLED) {
                throw new IllegalStateException(
                    "The client response body has been released already due to cancellation.");
            }
        }

        /**
         * Called by {@link ReactorNettyClient} when a cancellation or error is detected but the content has not been
         * subscribed to. If the subscription never materializes then the content will remain not drained. Or it could
         * still materialize if the cancellation or error happened very early, or the response reading was delayed for
         * some reason.
         */
        private void releaseOnNotSubscribedResponse(ReactorNettyResponseState reactorNettyResponseState) {
            if (this.state.compareAndSet(ReactorNettyResponseState.NOT_SUBSCRIBED, reactorNettyResponseState)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Releasing body, not yet subscribed");
                }
                this.bodyIntern()
                    .doOnNext(byteBuf -> {
                    })
                    .subscribe(byteBuf -> {
                    }, ex -> {
                    });
            }
        }
    }

    private enum ReactorNettyResponseState {
        // 0 - not subscribed, 1 - subscribed, 2 - cancelled via connector (before subscribe)
        // 3 - error occurred before subscribe
        NOT_SUBSCRIBED,
        SUBSCRIBED,
        CANCELLED,
        ERROR;
    }
}
