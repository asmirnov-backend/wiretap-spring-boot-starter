package io.wiretap.kafka.message;

/**
 * SPI deciding whether a Kafka record reaches the log at all. Register a
 * Spring bean implementing this interface to activate; without one every
 * record allowed by {@code exclude-topic-patterns} is logged.
 *
 * <p>The record arrives raw — before masking, truncation and visibility
 * settings — so a filter reads the original key and value, and a rejected
 * record pays for neither masking nor serialisation. Headers are the one
 * exception: they are already narrowed to the names listed under
 * {@code headers} and are {@code null} when none matched, so a predicate
 * cannot branch on a header the configuration does not collect. Their
 * values are still unmasked. The whole snapshot is passed in, so the
 * predicate can also branch on {@code direction}, {@code topic},
 * {@code groupId} or {@code status}; treat it as read-only — it is the
 * same instance the log line is built from.
 *
 * <p>One bean serves both directions. A filter that throws is treated as
 * "unknown": the record is logged and the failure is counted as
 * {@code wiretap.kafka.skipped{reason="filter_error"}}, so a broken filter
 * never silences the log.
 *
 * <pre>
 * &#64;Component
 * public class RequestSourceFilter implements KafkaRecordLogFilter {
 *     private final ObjectMapper mapper;
 *
 *     public RequestSourceFilter(ObjectMapper mapper) {
 *         this.mapper = mapper;
 *     }
 *
 *     &#64;Override public boolean shouldLog(KafkaMessageInfo record) {
 *         try {
 *             return "system-1".equals(mapper.readTree(record.getKey()).path("requestSource").asText());
 *         } catch (Exception e) {
 *             return true;
 *         }
 *     }
 * }
 * </pre>
 */
public interface KafkaRecordLogFilter {

    /**
     * @param record raw record snapshot, before masking and truncation
     * @return {@code true} to write the log line, {@code false} to drop it
     */
    boolean shouldLog(KafkaMessageInfo record);
}
