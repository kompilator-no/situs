package no.testframework.javalibrary.suite;

public interface Registry<K, V> {
    void register(K key, V value);

    V get(K key);

    <T> T getAsOrThrow(K key, Class<T> type);
}
