package dev.rabbit.pojo;

@FunctionalInterface
public interface QuadConsumer<T, S, U, V> {
    void accept(T t, S s, U u, V v);
}
