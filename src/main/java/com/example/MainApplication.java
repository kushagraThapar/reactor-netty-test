package com.example;

import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.resources.ConnectionProvider;

import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

public class MainApplication {

    private static final Logger logger = LoggerFactory.getLogger(MainApplication.class);

    public static void main(String[] args) throws URISyntaxException {
        logger.info("Started main");
        ConnectionProvider provider =
            ConnectionProvider.builder("test")
                              .maxConnections(10)
                              .maxIdleTime(Duration.ofMinutes(1))
                              .pendingAcquireTimeout(Duration.ofSeconds(45))
                              .pendingAcquireMaxCount(-1)
                              .build();

        String[] hostList = { "http://yahoo.com" };

        ReactorNettyClient reactorNettyClient = ReactorNettyClient.createWithConnectionProvider(provider);

        for (int i = 0; i < 10; i++) {
            String uri = hostList[0];
            HttpRequest request = new HttpRequest(HttpMethod.GET, uri);
            Mono<HttpResponse> responseBody = getResponseBody(reactorNettyClient, request);
            getResponseBody(reactorNettyClient, request).flatMap((response) -> {
                request.reactorNettyRequestRecord().setTimeCompleted(Instant.now());
                return Mono.just(response);
            }).block();
            logger.info("Request timeline is : {}", request.reactorNettyRequestRecord());
        }
    }

    public static Mono<HttpResponse> getResponseBody(ReactorNettyClient client, HttpRequest request) {
        logger.info("Calling : {}", request.uri());
        return client.send(request, Duration.ofSeconds(5));
    }

    public static Mono<HttpResponse> getResponseBody(ReactorNettyClient client, String[] hostList, Random random) throws URISyntaxException {
        int index = random.nextInt(hostList.length);
        String uri = hostList[index];
        HttpRequest request = new HttpRequest(HttpMethod.GET, uri);
        logger.info("Calling : {}", uri);
        return client.send(request, Duration.ofSeconds(5));
    }
}
