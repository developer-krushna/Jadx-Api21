package com.custom;

import java.util.Objects;

// by developer-krushna
@CustFunctionalInterface
public interface Consumer<T> {

    void accept(T t);

    
    default Consumer<T> andThen(Consumer<? super T> after) {
        Objects.requireNonNull(after);
        return (T t) -> { accept(t); after.accept(t); };
    }
}