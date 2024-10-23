package com.custom;

import java.util.Objects;
// by developer-krushna
@CustFunctionalInterface
public interface IntConsumer {
    void accept(int value);

    default IntConsumer andThen(IntConsumer after) {
        Objects.requireNonNull(after);
        return (int t) -> { accept(t); after.accept(t); };
    }
}