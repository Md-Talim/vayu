package me.talim.vayu.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import me.talim.vayu.dto.CreateEventRequest;
import me.talim.vayu.dto.CreateEventResult;
import me.talim.vayu.models.Delivery;
import me.talim.vayu.models.Endpoint;
import me.talim.vayu.models.Event;
import me.talim.vayu.models.Subscription;
import me.talim.vayu.repositories.DeliveryRepository;
import me.talim.vayu.repositories.EventRepository;
import me.talim.vayu.repositories.SubscriptionRepository;

@ExtendWith(MockitoExtension.class)
class EventWriterTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private DeliveryRepository deliveryRepository;

    @InjectMocks
    private EventWriter eventWriter;

    @Test
    void createsOneDeliveryPerSubscribedEndpoint() {
        CreateEventRequest request = new CreateEventRequest(
                "payment.succeeded",
                "{\"amount\":100}"
        );
        Endpoint first = new Endpoint("https://one.example.com/webhook", "secret-1");
        Endpoint second = new Endpoint("https://two.example.com/webhook", "secret-2");

        when(subscriptionRepository.findAllByEventTypeAndEndpoint_ActiveTrue("payment.succeeded"))
                .thenReturn(List.of(
                        new Subscription(first, "payment.succeeded"),
                        new Subscription(second, "payment.succeeded")
                ));
        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateEventResult result = eventWriter.createEvent(request, "idem-1");

        assertThat(result.replay()).isFalse();
        assertThat(result.response().deliveriesCreated()).isEqualTo(2);

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getType()).isEqualTo("payment.succeeded");
        assertThat(eventCaptor.getValue().getPayload()).isEqualTo("{\"amount\":100}");
        assertThat(eventCaptor.getValue().getIdempotencyKey()).isEqualTo("idem-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Delivery>> deliveriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(deliveriesCaptor.capture());
        assertThat(deliveriesCaptor.getValue())
                .extracting(delivery -> delivery.getSubscription().getEndpoint().getUrl())
                .containsExactlyInAnyOrder(
                        "https://one.example.com/webhook",
                        "https://two.example.com/webhook"
                );
    }

    @Test
    void onlyLooksUpActiveSubscriptions() {
        CreateEventRequest request = new CreateEventRequest(
                "payment.succeeded",
                "{\"amount\":100}"
        );

        when(subscriptionRepository.findAllByEventTypeAndEndpoint_ActiveTrue("payment.succeeded"))
                .thenReturn(List.of());
        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateEventResult result = eventWriter.createEvent(request, "idem-1");

        assertThat(result.response().deliveriesCreated()).isZero();
        verify(subscriptionRepository)
                .findAllByEventTypeAndEndpoint_ActiveTrue("payment.succeeded");
        verify(deliveryRepository).saveAll(List.of());
    }
}
