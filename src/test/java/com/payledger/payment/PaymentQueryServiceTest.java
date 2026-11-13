package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payledger.common.BadRequestException;
import com.payledger.common.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link PaymentQueryService}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final UUID MERCHANT = UUID.randomUUID();

    @Mock
    PaymentRepository payments;

    @InjectMocks
    PaymentQueryService service;

    @Test
    void getReturnsPaymentWithTimeline() {
        Payment p = new Payment(UUID.randomUUID(), MERCHANT, 100, "INR", "c1", NOW);
        when(payments.findByIdAndMerchantId(p.getId(), MERCHANT)).thenReturn(Optional.of(p));

        PaymentResponse r = service.get(MERCHANT, p.getId());

        assertThat(r.id()).isEqualTo(p.getId());
        assertThat(r.timeline()).singleElement().extracting(PaymentResponse.TimelineEntry::to).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void getOtherMerchantsPaymentIsNotFound() {
        UUID id = UUID.randomUUID();
        when(payments.findByIdAndMerchantId(id, MERCHANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(MERCHANT, id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSortsNewestFirstAndMapsPage() {
        Payment p = new Payment(UUID.randomUUID(), MERCHANT, 100, "INR", "c1", NOW);
        when(payments.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(p)));

        var page = service.list(MERCHANT, PaymentStatus.CREATED, NOW.minusSeconds(60), NOW.plusSeconds(60), 0, 20);

        assertThat(page.content()).extracting(PaymentSummary::id).containsExactly(p.getId());
        assertThat(page.totalElements()).isEqualTo(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(payments).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void listRejectsBadPagingAndDateRange() {
        assertThatThrownBy(() -> service.list(MERCHANT, null, null, null, -1, 20)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(MERCHANT, null, null, null, 0, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(MERCHANT, null, null, null, 0, 101)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(MERCHANT, null, NOW, NOW, 0, 20)).isInstanceOf(BadRequestException.class);
    }
}
