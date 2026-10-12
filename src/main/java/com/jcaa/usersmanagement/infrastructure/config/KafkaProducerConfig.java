package com.jcaa.usersmanagement.infrastructure.config;

import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Se crea solo si events.publisher=kafka. Todo se lee de variables de entorno. */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "events.publisher", havingValue = "kafka")
public class KafkaProducerConfig {

  @Bean(destroyMethod = "close")
  public KafkaProducer<String, String> kafkaProducer(
      @Value("${kafka.bootstrap-servers}") final String bootstrapServers,
      @Value("${kafka.security-protocol}") final String securityProtocol,
      @Value("${kafka.sasl.mechanism}") final String saslMechanism,
      @Value("${kafka.sasl.username}") final String username,
      @Value("${kafka.sasl.password}") final String password,
      @Value("${kafka.ssl.ca-path:}") final String caPath,
      @Value("${kafka.topic.users}") final String topic,
      @Value("${kafka.ssl.ca-pem:}") final String caPem) {

    final Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    // Si Kafka no responde, no dejar colgada la petición del usuario por mucho tiempo
    props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000L);
    props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
    props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 15_000);

    props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
    if (securityProtocol.startsWith("SASL")) {
      props.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
      props.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig(saslMechanism, username, password));
    }
    if (!caPath.isBlank()) {
      props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
      props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, caPath);
    } else if (!caPem.isBlank()) {
      props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
      props.put(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, caPem.replace("\\n", "\n"));
    }

    log.info(
        "[KafkaProducerConfig] productor Kafka. servers={} protocolo={} mecanismo={}",
        bootstrapServers,
        securityProtocol,
        saslMechanism);
    final KafkaProducer<String, String> producer = new KafkaProducer<>(props);
    warmUp(producer, topic);
    return producer;
  }

  private static String jaasConfig(
      final String mechanism, final String username, final String password) {
    final String module =
        mechanism.toUpperCase().startsWith("SCRAM")
            ? "org.apache.kafka.common.security.scram.ScramLoginModule"
            : "org.apache.kafka.common.security.plain.PlainLoginModule";
    return String.format(
        "%s required username=\"%s\" password=\"%s\";", module, escape(username), escape(password));
  }

  private static String escape(final String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
  /** Abre la conexión y pide los metadatos del tópico en segundo plano, para que el primer envío no tarde. */
  private static void warmUp(final KafkaProducer<String, String> producer, final String topic) {
    final Thread thread =
        new Thread(
            () -> {
              try {
                producer.partitionsFor(topic);
                log.info("[KafkaProducerConfig] conexión calentada para el tópico {}", topic);
              } catch (final RuntimeException exception) {
                log.warn(
                    "[KafkaProducerConfig] no se pudo calentar la conexión: {}", exception.toString());
              }
            },
            "kafka-warmup");
    thread.setDaemon(true);
    thread.start();
  }
}