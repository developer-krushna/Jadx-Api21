
package com.custom;

import java.util.Objects;

// by developer-krushna

@CustFunctionalInterface
public interface BiFunction<T, U, R> {

    
    R apply(T t, U u);

  
    default <V> BiFunction<T, U, V> andThen(Function<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (T t, U u) -> after.apply(apply(t, u));
    }
}