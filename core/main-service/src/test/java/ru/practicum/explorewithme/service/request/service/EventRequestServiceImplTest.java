package ru.practicum.explorewithme.service.request.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.explorewithme.service.exception.ConflictException;
import ru.practicum.explorewithme.service.request.client.EventClient;
import ru.practicum.explorewithme.service.request.client.UserClient;
import ru.practicum.explorewithme.service.request.dal.EventRequestRepository;
import ru.practicum.explorewithme.service.request.dto.EventForRequestDto;
import ru.practicum.explorewithme.service.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.explorewithme.service.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.explorewithme.service.request.dto.ParticipationRequestDto;
import ru.practicum.explorewithme.service.request.enums.ParticipationRequestStatus;
import ru.practicum.explorewithme.service.request.model.ParticipationRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventRequestServiceImplTest {

    @Mock
    private EventRequestRepository eventRequestRepository;
    @Mock
    private UserClient userClient;
    @Mock
    private EventClient eventClient;

    @InjectMocks
    private EventRequestServiceImpl requestService;

    @Test
    void getEventRequests_Success() {
        when(eventClient.getEventById(1L)).thenReturn(new EventForRequestDto(1L, 1L, "PUBLISHED", 2, true));
        when(eventRequestRepository.findAllByEventId(1L)).thenReturn(List.of());

        List<ParticipationRequestDto> result = requestService.getEventRequests(1L, 1L);
        assertThat(result).isEmpty();
    }

    @Test
    void updateEventRequests_ConfirmWithRemainingLimit() {
        when(eventClient.getEventById(1L)).thenReturn(new EventForRequestDto(1L, 1L, "PUBLISHED", 2, true));

        ParticipationRequest req = new ParticipationRequest();
        req.setId(10L);
        req.setRequesterId(1L);
        req.setStatus(ParticipationRequestStatus.PENDING);
        req.setEventId(1L);

        when(eventRequestRepository.findAllByIdInAndStatus(List.of(10L), ParticipationRequestStatus.PENDING))
                .thenReturn(List.of(req));
        when(eventRequestRepository.countByEventIdAndStatus(1L, ParticipationRequestStatus.CONFIRMED)).thenReturn(0);
        when(eventRequestRepository.saveAll(anyList())).thenReturn(List.of());

        EventRequestStatusUpdateRequest updateReq = EventRequestStatusUpdateRequest.builder()
                .requestIds(List.of(10L))
                .status(ParticipationRequestStatus.CONFIRMED)
                .build();

        EventRequestStatusUpdateResult result = requestService.updateEventRequests(1L, 1L, updateReq);

        assertThat(result.getConfirmedRequests()).hasSize(1);
        assertThat(result.getRejectedRequests()).isEmpty();
    }

    @Test
    void updateEventRequests_ConflictWhenPrerequisitesNotMet() {
        when(eventClient.getEventById(1L)).thenReturn(new EventForRequestDto(1L, 1L, "PUBLISHED", 0, true));

        EventRequestStatusUpdateRequest updateReq = EventRequestStatusUpdateRequest.builder()
                .requestIds(List.of(1L))
                .status(ParticipationRequestStatus.CONFIRMED)
                .build();
        assertThatThrownBy(() -> requestService.updateEventRequests(1L, 1L, updateReq))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Подтверждение заявок не требуется");
    }
}
