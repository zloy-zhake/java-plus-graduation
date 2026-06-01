package ru.practicum.explorewithme.stats.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import ru.practicum.explorewithme.stats.dto.EndpointHitDTO;
import ru.practicum.explorewithme.stats.dto.ViewStatsDTO;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class StatsClient extends BaseClient {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DiscoveryClient discoveryClient;
    private final String statsServiceId;
    private final RetryTemplate retryTemplate;

    public StatsClient(
            DiscoveryClient discoveryClient,
            @Value("${stats-service.id:stats-server}") String statsServiceId,
            @Value("${stats-service.retry.max-attempts:3}") int maxAttempts,
            @Value("${stats-service.retry.back-off-period:3000}") long backOffPeriod,
            WebClient.Builder builder) {
        super(builder.build());
        this.discoveryClient = discoveryClient;
        this.statsServiceId = statsServiceId;
        this.retryTemplate = buildRetryTemplate(maxAttempts, backOffPeriod);
    }

    private RetryTemplate buildRetryTemplate(int maxAttempts, long backOffPeriod) {
        RetryTemplate template = new RetryTemplate();

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(backOffPeriod);
        template.setBackOffPolicy(backOffPolicy);

        MaxAttemptsRetryPolicy retryPolicy = new MaxAttemptsRetryPolicy();
        retryPolicy.setMaxAttempts(maxAttempts);
        template.setRetryPolicy(retryPolicy);

        return template;
    }

    private ServiceInstance getInstance() {
        try {
            return discoveryClient.getInstances(statsServiceId).getFirst();
        } catch (Exception e) {
            throw new StatsServerUnavailable(
                    "Ошибка обнаружения адреса сервиса статистики с id: " + statsServiceId, e);
        }
    }

    private URI makeUri(String path) {
        ServiceInstance instance = retryTemplate.execute(ctx -> getInstance());
        return URI.create("http://" + instance.getHost() + ":" + instance.getPort() + path);
    }

    public ResponseEntity<Object> saveHit(EndpointHitDTO hitDto) {
        try {
            log.info("Отправка статистики на сервер: {}", hitDto);
            return post(makeUri("/hit"), hitDto);
        } catch (Exception e) {
            log.warn("Не удалось сохранить хит в статистику. Ошибка: {}. Тело: {}", e.getMessage(), hitDto);
            log.error("DEBUG: Поймали исключение: ", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    public ResponseEntity<List<ViewStatsDTO>> getStats(LocalDateTime start, LocalDateTime end,
                                                       List<String> uris, Boolean unique) {
        ServiceInstance instance = retryTemplate.execute(ctx -> getInstance());
        return webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder
                            .scheme("http")
                            .host(instance.getHost())
                            .port(instance.getPort())
                            .path("/stats")
                            .queryParam("start", start.format(FORMATTER))
                            .queryParam("end", end.format(FORMATTER));
                    if (uris != null && !uris.isEmpty()) {
                        uriBuilder.queryParam("uris", uris);
                    }
                    if (unique != null) {
                        uriBuilder.queryParam("unique", unique);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .toEntityList(ViewStatsDTO.class)
                .block();
    }
}
