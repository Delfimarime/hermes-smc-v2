package com.raitonbl.hermes.smsc.sdk;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class ConfigurationUtils {

    public static <T> void setParameter(StringBuilder builder, AtomicBoolean isFirst, String name, T value) {
        setParameter(builder, isFirst, name, value, null);
    }

    public static <T> void setParameter(StringBuilder builder, AtomicBoolean isFirst, String name, T value, T otherwise) {
        setParameter(builder, isFirst, name, value, otherwise, null);
    }


    @SuppressWarnings({"unchecked"})
    public static <T, Y> void setParameter(StringBuilder builder, AtomicBoolean isFirst, String name,
                                           T value, T otherwise, Function<T, Y> mapper) {
        T target = value != null ? value : otherwise;
        if (target == null) {
            return;
        }
        Y targetObject;
        if (mapper != null) {
            targetObject = mapper.apply(target);
        } else {
            targetObject = (Y) target;
        }
        builder.append(isFirst.get() ? "?" : "&");
        builder.append(name).append("=").append(targetObject);
        isFirst.set(false);
    }
}
