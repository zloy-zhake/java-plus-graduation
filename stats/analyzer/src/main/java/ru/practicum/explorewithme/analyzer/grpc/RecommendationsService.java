package ru.practicum.explorewithme.analyzer.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.practicum.ewm.stats.proto.dashboard.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.dashboard.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.dashboard.RecommendationsControllerGrpc;
import ru.practicum.ewm.stats.proto.dashboard.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.dashboard.UserPredictionsRequestProto;
import ru.practicum.explorewithme.analyzer.model.EventSimilarity;
import ru.practicum.explorewithme.analyzer.model.UserAction;
import ru.practicum.explorewithme.analyzer.repository.EventScore;
import ru.practicum.explorewithme.analyzer.repository.EventSimilarityRepository;
import ru.practicum.explorewithme.analyzer.repository.UserActionRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class RecommendationsService extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {

    private final UserActionRepository userActionRepository;
    private final EventSimilarityRepository eventSimilarityRepository;

    @Value("${recommendations.n-interactions}")
    private int nInteractions;

    @Value("${recommendations.k-neighbors}")
    private int kNeighbors;

    @Override
    public void getInteractionsCount(InteractionsCountRequestProto request,
                                     StreamObserver<RecommendedEventProto> responseObserver) {
        List<Long> eventIds = request.getEventIdList();
        log.debug("getInteractionsCount: eventIds={}", eventIds);
        List<EventScore> scores = userActionRepository.findInteractionScores(eventIds);
        log.debug("getInteractionsCount: findInteractionScores returned {} rows", scores.size());
        for (EventScore s : scores) {
            Long eid = s.getEventId();
            Double sc = s.getScore();
            log.debug("  row: eventId={} (type={}) score={} (type={})",
                    eid, eid == null ? "null" : eid.getClass().getSimpleName(),
                    sc, sc == null ? "null" : sc.getClass().getSimpleName());
        }
        Map<Long, Double> scoresMap = scores.stream()
                .collect(Collectors.toMap(EventScore::getEventId, EventScore::getScore));

        log.debug("getInteractionsCount: scoresMap={}, mapKeyType={}", scoresMap,
                scoresMap.isEmpty() ? "empty" : scoresMap.keySet().iterator().next().getClass().getSimpleName());
        for (Long eventId : eventIds) {
            boolean contains = scoresMap.containsKey(eventId);
            double score = scoresMap.getOrDefault(eventId, 0.0);
            log.debug("getInteractionsCount: eventId={} (type={}) containsKey={} score={} floatScore={}",
                    eventId, eventId.getClass().getSimpleName(), contains, score, (float) score);
            responseObserver.onNext(RecommendedEventProto.newBuilder()
                    .setEventId(eventId)
                    .setScore(score)
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getSimilarEvents(SimilarEventsRequestProto request,
                                 StreamObserver<RecommendedEventProto> responseObserver) {
        long eventId = request.getEventId();
        long userId = request.getUserId();
        int maxResults = request.getMaxResults();
        log.debug("getSimilarEvents: eventId={} userId={} maxResults={}", eventId, userId, maxResults);

        Set<Long> userEvents = getUserEventIds(userId);
        log.debug("getSimilarEvents: user {} has {} known events", userId, userEvents.size());

        List<EventSimilarity> sims = eventSimilarityRepository.findByEventId(eventId);
        log.debug("getSimilarEvents: found {} similarities for event {}", sims.size(), eventId);

        sims.stream()
                .map(sim -> Map.entry(getOtherEventId(sim, eventId), sim.getScore()))
                .filter(e -> !userEvents.contains(e.getKey()))
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .forEach(e -> responseObserver.onNext(RecommendedEventProto.newBuilder()
                        .setEventId(e.getKey())
                        .setScore(e.getValue())
                        .build()));
        log.debug("getSimilarEvents: completed for eventId={}", eventId);
        responseObserver.onCompleted();
    }

    @Override
    public void getRecommendationsForUser(UserPredictionsRequestProto request,
                                          StreamObserver<RecommendedEventProto> responseObserver) {
        long userId = request.getUserId();
        int maxResults = request.getMaxResults();
        log.debug("getRecommendationsForUser: userId={} maxResults={}", userId, maxResults);

        // 1а. Последние N взаимодействий пользователя
        List<UserAction> recentInteractions = userActionRepository
                .findTopNByUser(userId, PageRequest.of(0, nInteractions));
        log.debug("getRecommendationsForUser: recentInteractions={}", recentInteractions.size());

        if (recentInteractions.isEmpty()) {
            log.debug("getRecommendationsForUser: no interactions for user {}, returning empty", userId);
            responseObserver.onCompleted();
            return;
        }

        Set<Long> viewedEventIds = recentInteractions.stream()
                .map(ua -> ua.getId().getEventId())
                .collect(Collectors.toSet());

        // 1в. Кандидаты: похожие на просмотренные, ещё не просмотренные
        Map<Long, Double> candidateScores = new HashMap<>();
        for (UserAction interaction : recentInteractions) {
            long viewedId = interaction.getId().getEventId();
            for (EventSimilarity sim : eventSimilarityRepository.findByEventId(viewedId)) {
                long candidateId = getOtherEventId(sim, viewedId);
                if (!viewedEventIds.contains(candidateId)) {
                    candidateScores.merge(candidateId, sim.getScore(), Math::max);
                }
            }
        }

        log.debug("getRecommendationsForUser: candidateScores={}", candidateScores.size());
        if (candidateScores.isEmpty()) {
            log.debug("getRecommendationsForUser: no candidates for user {}, returning empty", userId);
            responseObserver.onCompleted();
            return;
        }

        // 1г. Дедупликация, сортировка, топ-N кандидатов
        List<Long> candidates = candidateScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(nInteractions)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<Long, Double> userWeightsMap = recentInteractions.stream()
                .collect(Collectors.toMap(ua -> ua.getId().getEventId(), UserAction::getMaxWeight));

        // 2. Предсказание оценки для каждого кандидата
        List<RecommendedEventProto> results = new ArrayList<>();
        for (Long candidateId : candidates) {
            List<double[]> neighbors = eventSimilarityRepository.findByEventId(candidateId).stream()
                    .map(sim -> new double[]{getOtherEventId(sim, candidateId), sim.getScore()})
                    .filter(pair -> viewedEventIds.contains((long) pair[0]))
                    .sorted((a, b) -> Double.compare(b[1], a[1]))
                    .limit(kNeighbors)
                    .collect(Collectors.toList());

            double numerator = 0;
            double denominator = 0;
            for (double[] neighbor : neighbors) {
                double simScore = neighbor[1];
                double weight = userWeightsMap.getOrDefault((long) neighbor[0], 0.0);
                numerator += simScore * weight;
                denominator += simScore;
            }

            if (denominator == 0) continue;

            results.add(RecommendedEventProto.newBuilder()
                    .setEventId(candidateId)
                    .setScore(numerator / denominator)
                    .build());
        }

        log.debug("getRecommendationsForUser: computed {} results, sending top {}", results.size(), maxResults);
        results.stream()
                .sorted(Comparator.comparingDouble(RecommendedEventProto::getScore).reversed())
                .limit(maxResults)
                .forEach(responseObserver::onNext);
        responseObserver.onCompleted();
    }

    private long getOtherEventId(EventSimilarity sim, long eventId) {
        return sim.getId().getEventAId().equals(eventId)
                ? sim.getId().getEventBId()
                : sim.getId().getEventAId();
    }

    private Set<Long> getUserEventIds(long userId) {
        return userActionRepository.findTopNByUser(userId, Pageable.unpaged()).stream()
                .map(ua -> ua.getId().getEventId())
                .collect(Collectors.toSet());
    }
}
