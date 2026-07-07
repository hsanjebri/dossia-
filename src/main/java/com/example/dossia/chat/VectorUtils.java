package com.example.dossia.chat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class VectorUtils {

    private VectorUtils() {}

    public static String toPgVector(float[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Embedding values must not be empty");
        }
        return IntStream.range(0, values.length)
                .mapToObj(i -> Float.toString(values[i]))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
