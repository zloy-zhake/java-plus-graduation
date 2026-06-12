package ru.practicum.explorewithme.analyzer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.explorewithme.analyzer.repository.EventSimilarityRepository;
import ru.practicum.explorewithme.analyzer.repository.UserActionRepository;

@Component
@RequiredArgsConstructor
public class AnalyzerService {

    private final UserActionRepository userActionRepository;
    private final EventSimilarityRepository eventSimilarityRepository;

    @KafkaListener(topics = "stats.user-actions.v1")
    public void handleUserAction(UserActionAvro action) {
        double weight = getWeight(action.getActionType());
        userActionRepository.upsertUserAction(
                action.getUserId(),
                action.getEventId(),
                weight,
                action.getTimestamp());
    }

    @KafkaListener(topics = "stats.events-similarity.v1",
            containerFactory = "eventSimilarityListenerContainerFactory")
    public void handleEventSimilarity(EventSimilarityAvro similarity) {
        eventSimilarityRepository.upsertEventSimilarity(
                similarity.getEventA(),
                similarity.getEventB(),
                similarity.getScore());
    }

    private double getWeight(ActionTypeAvro actionType) {
        return switch (actionType) {
            case VIEW -> 0.4;
            case REGISTER -> 0.8;
            case LIKE -> 1.0;
        };
    }
}
