package ru.practicum.explorewithme.service.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.explorewithme.service.event.dal.EventRepository;
import ru.practicum.explorewithme.service.event.dto.EventForRequestDto;
import ru.practicum.explorewithme.service.exception.NotFoundException;

@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class EventInternalController {

    private final EventRepository eventRepository;

    @GetMapping("/{eventId}")
    public EventForRequestDto getEventById(@PathVariable Long eventId) {
        return eventRepository.findById(eventId)
                .map(e -> new EventForRequestDto(
                        e.getId(),
                        e.getInitiatorId(),
                        e.getState().name(),
                        e.getParticipantLimit(),
                        e.getRequestModeration()))
                .orElseThrow(() -> new NotFoundException("Событие " + eventId + " не найдено"));
    }
}
