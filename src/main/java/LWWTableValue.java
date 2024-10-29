import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class LWWTableValue<V> {

    private final V value;
    /**
     * Using LocalDateTime due to lack of readily available VectorClock implementations.
     */
    private final LocalDateTime timestamp;

    public LWWTableValue(V value, LocalDateTime timestamp) {
        this.value = value;
        this.timestamp = timestamp;
    }

    public boolean isBefore(LWWTableValue<V> value) {
        return this.timestamp.isBefore(value.getTimestamp());
    }

    public boolean isBefore(LocalDateTime timestamp) {
        return this.timestamp.isBefore(timestamp);
    }
}
