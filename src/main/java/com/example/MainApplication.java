package com.example;

import io.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.resources.ConnectionProvider;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainApplication {

    private static final Logger logger = LoggerFactory.getLogger(MainApplication.class);

    public static void main(String[] args) throws InterruptedException, URISyntaxException {
        logger.info("Started main");
        ConnectionProvider provider =
            ConnectionProvider.builder("test")
                              .maxConnections(100)
                              .maxIdleTime(Duration.ofMinutes(1))
                              .pendingAcquireTimeout(Duration.ofSeconds(45))
                              .pendingAcquireMaxCount(-1)
                              .build();

        String[] hostList = { "http://yahoo.com", "http://google.com", "http://bing.com/",
            "http://wordpress.com/", "http://example.com/" };

        ReactorNettyClient reactorNettyClient = ReactorNettyClient.createWithConnectionProvider(provider);

        List<Mono<HttpResponse>> responseList = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            responseList.add(getResponseBody(reactorNettyClient, hostList, random));
        }
        Flux.merge(Flux.fromIterable(responseList), 50).map(HttpResponse::bodyAsString).collectList().block();

        logger.info("Sleeping thread : {}", Thread.currentThread().getName());
        Thread.sleep(5 * 1000 * 60);
    }

    public static Mono<HttpResponse> getResponseBody(ReactorNettyClient client, String[] hostList, Random random) throws URISyntaxException {
        int index = random.nextInt(hostList.length);
        String uri = hostList[index];
        HttpRequest request = new HttpRequest(HttpMethod.GET, uri);
        logger.info("Calling : {}", uri);
        return client.send(request, Duration.ofSeconds(5));
    }
}
