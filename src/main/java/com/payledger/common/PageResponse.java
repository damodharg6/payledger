package com.payledger.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * A stable JSON shape for paginated results. Serialising Spring's {@code Page} directly would tie
 * the API to Spring's internal classes.
 *
 * @param <T> item type
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
