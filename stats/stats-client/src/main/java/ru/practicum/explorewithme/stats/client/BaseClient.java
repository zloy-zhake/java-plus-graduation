package ru.practicum.explorewithme.stats.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;

public abstract class BaseClient {
    protected final WebClient webClient;

    protected BaseClient(WebClient webClient) {
        this.webClient = webClient;
    }

    protected <T> ResponseEntity<Object> post(URI uri, T body) {
        return webClient.post()
                .uri(uri)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .toEntity(Object.class)
                .block();
    }
}
