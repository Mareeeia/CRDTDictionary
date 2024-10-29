import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Hashtable;
import java.util.Map;
import java.util.stream.Collectors;

public class LWWHashTable<K, V> implements LWWDictionary<K, V> {

    private final Hashtable<K, LWWTableValue<V>> addDictionary;
    private final Hashtable<K, LocalDateTime> removeDictionary;

    /**
     * The following could be parametrised externally.
     * We need these biases in order to resolve conflicts consistently.
     * Whether to favour concurrent removals over adds when looking up the value
     * (e.g. should removes cancel re-adds if they happen at the same time):
     */
    private final static boolean REMOVE_BIAS = true;

    /**
     * Whether to favour concurrent updates over adds when updating the state:
     */
    private final static boolean UPDATE_BIAS = true;

    public LWWHashTable() {
        this.addDictionary = new Hashtable<>();
        this.removeDictionary = new Hashtable<>();
    }

    public LWWHashTable(Map<K, V> map, LocalDateTime timestamp) {
        var lwwMap = map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> new LWWTableValue<>(entry.getValue(), timestamp)));
        this.addDictionary = new Hashtable<>(lwwMap);
        this.removeDictionary = new Hashtable<>();
    }

    public void add(K key, V value, LocalDateTime timestamp) {
        if (canAddKey(key, timestamp)) {
            this.addDictionary.put(key, new LWWTableValue<>(value, timestamp));
        }
    }

    public V lookup(K key) {
        if (this.addDictionary.containsKey(key) && !isKeyRemoved(key)) {
            return this.addDictionary.get(key).getValue();
        }
        return null;
    }

    /**
     * Will merge a new LWWHashTable into the current one if they are both LWWHashTables
     * as opposed to different implementations of LWWDictionary.
     * @param other another LWWDictionary to merge with.
     */
    public void merge(LWWDictionary<K, V> other) {
        if (other instanceof LWWHashTable<K, V>) {
            this.merge((LWWHashTable<K, V>)other);
        }
        else {
            throw new RuntimeException("Can only apply merge to other LWWHashTable objects");
        }
    }

    public void remove(K key, LocalDateTime timestamp) {
        if (canRemoveKey(key, timestamp)) {
            this.removeDictionary.put(key, timestamp);
        }
    }

    public void update(K key, V value, LocalDateTime timestamp) {
        if (canUpdateKey(key, timestamp)) {
            this.addDictionary.put(key, new LWWTableValue<>(value, timestamp));
        }
    }

    public Map<K, V> getElements() {
        return this.addDictionary.entrySet().stream()
                .filter(entry -> !isKeyRemoved(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
    }

    /**
     * Merge with a different LWWHashTable. When encountering collisions, keep the later value.
     * @param other - a different LWWHashTable.
     */
    private void merge(LWWHashTable<K, V> other) {
        other.addDictionary.forEach(
                (key, value) -> this.addDictionary.merge(key, value,
                        (thisValue, otherValue) -> otherValue.isBefore(thisValue) ? thisValue : otherValue));
        other.removeDictionary.forEach(
                (key, value) -> this.removeDictionary.merge(key, value,
                        (thisValue, otherValue) -> otherValue.isBefore(thisValue) ? thisValue : otherValue));
    }

    private boolean isKeyRemoved(K key) {
        if (!this.addDictionary.containsKey(key)) {
            return this.removeDictionary.containsKey(key);
        }
        final var timeOfKey = this.addDictionary.get(key).getTimestamp();
        final var timeOfRemove = this.removeDictionary.get(key);
        return timeOfRemove != null
                && isBeforeWithBias(timeOfKey, timeOfRemove, REMOVE_BIAS);
    }

    /**
     * Used in context of updating/removing entries. Returns true if addTime is before updateTime,
     * or if they are equal but the bias favors the update/remove operation.
     * @param addTime - time an entry has been added.
     * @param updateTime - time an entry has been updated or deleted
     * @param bias - either REMOVE_BIAS or UPDATE_BIAS depending on what operation we are performing.
     * @return which operation wins - the add one or the update/remove one.
     */
    private boolean isBeforeWithBias(LocalDateTime addTime, LocalDateTime updateTime, boolean bias) {
        return addTime.isBefore(updateTime) || (bias && addTime.equals(updateTime));
    }

    private boolean canAddKey(K key, LocalDateTime timestamp) {
        return !this.addDictionary.containsKey(key) || this.addDictionary.get(key).isBefore(timestamp);
    }

    private boolean canUpdateKey(K key, LocalDateTime timestamp) {
        if (!this.addDictionary.containsKey(key)) {
            return false;
        }
        return (isBeforeWithBias(this.addDictionary.get(key).getTimestamp(), timestamp, UPDATE_BIAS))
                && !isKeyRemoved(key); // choose to check if key is removed so updates can't cancel removes.
    }

    private boolean canRemoveKey(K key, LocalDateTime timestamp) {
        return !this.removeDictionary.containsKey(key) || this.removeDictionary.get(key).isBefore(timestamp);
    }
}
