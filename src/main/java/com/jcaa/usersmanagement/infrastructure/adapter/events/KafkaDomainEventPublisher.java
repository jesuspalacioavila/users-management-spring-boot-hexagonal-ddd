package com.jcaa.usersmanagement.infrastructure.adapter.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.application.port.out.PublishDomainEventPort;
import com.jcaa.usersmanagement.domain.event.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Publicador real: envía el JSON a Kafka. Se activa con events.publisher=kafka. */
@Slf4j
@Component
@ConditionalOnProperty(name = "events.publisher", havingValue = "kafka")
public class KafkaDomainEventPublisher implements PublishDomainEventPort {

  private final KafkaProducer<String, String> producer;
  private final String topic;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public KafkaDomainEventPublisher(
      final KafkaProducer<String, String> producer, @Value("${kafka.topic.users}") final String topic) {
    this.producer = producer;
    this.topic = topic;
  }

  @Override
  public void publish(final DomainEvent event) {
    NotificationEventMapper.from(event).ifPresent(this::send);
  }

  private void send(final NotificationEventMessage message) {
    final String key = message.data().get("userId");
    final String json = toJson(message);
    producer.send(
        new ProducerRecord<>(topic, key, json),
        (metadata, exception) -> {
          if (exception != null) {
            log.error(
                "[KafkaDomainEventPublisher] no se pudo publicar {} en {}: {}",
                message.type(),
                topic,
                exception.toString());
          } else {
            log.info(
                "[KafkaDomainEventPublisher] publicado {} topic={} partition={} offset={}",
                message.type(),
                metadata.topic(),
                metadata.partition(),
                metadata.offset());
          }
        });
  }

  private String toJson(final NotificationEventMessage message) {
    try {
      return objectMapper.writeValueAsString(message);
    } catch (final JsonProcessingException exception) {
      throw new IllegalStateException("No se pudo serializar el evento", exception);
    }
  }
}