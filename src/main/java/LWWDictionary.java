import java.time.LocalDateTime;

public interface LWWDictionary<K, V> {
    public void add(K key, V value, LocalDateTime timestamp);
    public void remove(K key, LocalDateTime timestamp);
    public void update(K key, V value, LocalDateTime timestamp);
    public V lookup(K key);
    public void merge(LWWDictionary<K, V> other);
}
