package io.wiretap.kafka.message.settings;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Compiled form of the {@code key-json-include} option: JSON Pointers into the
 * record key, each carrying the values that admit a record to the log.
 *
 * <p>Pointers are combined with AND, the values behind one pointer with OR.
 * An empty condition admits every record, which keeps the default behaviour of
 * logging the whole topic. Patterns are compiled once, at property binding time,
 * so the hot path only runs the matchers.
 */
public final class KeyJsonInclude {

    private final Map<JsonPointer, List<Pattern>> conditions;

    public KeyJsonInclude(Map<JsonPointer, List<Pattern>> conditions) {
        this.conditions = conditions;
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
        return allowed.stream().anyMatch(pattern -> pattern.matcher(field.asText()).matches());
    }
}
