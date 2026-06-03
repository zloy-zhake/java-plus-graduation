package ru.practicum.explorewithme.service.compilation.mapper;

import ru.practicum.explorewithme.service.compilation.dto.CompilationDto;
import ru.practicum.explorewithme.service.compilation.dto.NewCompilationDto;
import ru.practicum.explorewithme.service.compilation.dto.UpdateCompilationRequestDto;
import ru.practicum.explorewithme.service.compilation.model.Compilation;
import ru.practicum.explorewithme.service.event.dto.EventShortDto;
import ru.practicum.explorewithme.service.event.mapper.EventMapper;
import ru.practicum.explorewithme.service.user.dto.UserShortDto;

import java.util.List;
import java.util.stream.Collectors;

public final class CompilationMapper {

    public static Compilation toEntity(NewCompilationDto dto) {
        Compilation compilation = new Compilation();
        compilation.setTitle(dto.getTitle());
        compilation.setPinned(dto.getPinned() != null ? dto.getPinned() : false);
        return compilation;
    }

    public static CompilationDto toDto(Compilation compilation) {
        // Заглушка: имя инициатора = "N/A". В ШАГ 10 заменяется на EventClient.
        List<EventShortDto> events = compilation.getEvents().stream()
                .map(e -> EventMapper.toShortDto(e, new UserShortDto(e.getInitiatorId(), "N/A"), 0L, 0L))
                .collect(Collectors.toList());

        return CompilationDto.builder().id(compilation.getId()).title(compilation.getTitle()).pinned(compilation.getPinned()).events(events).build();
    }

    public static void updateEntityFromRequest(UpdateCompilationRequestDto request, Compilation compilation) {
        if (request.getTitle() != null) {
            compilation.setTitle(request.getTitle());
        }
        if (request.getPinned() != null) {
            compilation.setPinned(request.getPinned());
        }
    }
}