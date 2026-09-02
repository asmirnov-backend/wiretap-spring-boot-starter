package io.wiretap.kafka.message.settings;

import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Compiled form of the {@code key-json-include} option: JSON Pointers into the
 * record key, each carrying the values that admit a record to the log.
 *
 * <p>Pointers are combined with AND, the values behind one pointer with OR.
 * An empty condition admits every record, which keeps the default behaviour of
 * logging the whole topic. Pointers and patterns are compiled here, once per
 * bound configuration, so the hot path only runs the matchers — an illegal
 * pointer or pattern fails the context startup rather than every record.
 *
 * <p>The compiled state is the only place the Kafka settings touch Jackson,
 * which is what lets Spring Boot 4 override this single class against the
 * Jackson 3 API instead of the whole settings tree.
 */
public final class KeyJsonInclude {

    private final Map<JsonPointer, List<Pattern>> conditions;

    public KeyJsonInclude(Map<String, List<String>> conditions) {
        this.conditions = compile(conditions);
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }

    /**
     * @return {@code true} when every configured pointer resolves inside
     *         {@code key} to a value matching at least one of its patterns.
     *         A key that is absent or not JSON never satisfies a non-empty
     *         condition.
     */
    public boolean matches(JsonNode key) {
        if (conditions.isEmpty()) return true;
        if (key == null) return false;
        for (Map.Entry<JsonPointer, List<Pattern>> condition : conditions.entrySet()) {
            if (!matches(key.at(condition.getKey()), condition.getValue())) return false;
        }
        return true;
    }

    private boolean matches(JsonNode field, List<Pattern> allowed) {
        if (field.isMissingNode() || field.isNull()) return false;
        return allowed.stream().anyMatch(pattern -> pattern.matcher(field.asString()).matches());
    }

    private Map<JsonPointer, List<Pattern>> compile(Map<String, List<String>> raw) {
        final Map<JsonPointer, List<Pattern>> compiled = new LinkedHashMap<>(raw.size());
        for (Map.Entry<String, List<String>> condition : raw.entrySet()) {
            compiled.put(
                    JsonPointer.compile(condition.getKey()),
                    condition.getValue().stream().map(Pattern::compile).collect(Collectors.toList())
            );
        }
        return compiled;
    }
}
