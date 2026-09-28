package com.bukcase.identity;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Identity of the user executing the current unit of work, equivalent to {@code User::GetCurrent}.
 * Bound per thread: web requests bind it in {@link CurrentUserFilter}; async jobs store the
 * enqueuing user's id and wrap their execution in {@link #runAs(long, Supplier)}.
 */
public final class CurrentUser {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static long id() {
        return find().orElseThrow(NoCurrentUserException::new);
    }

    public static Optional<Long> find() {
        return Optional.ofNullable(USER_ID.get());
    }

    public static Scope bind(long userId) {
        Long previous = USER_ID.get();
        USER_ID.set(userId);
        return () -> {
            if (previous == null) {
                USER_ID.remove();
            } else {
                USER_ID.set(previous);
            }
        };
    }

    public static <T> T runAs(long userId, Supplier<T> work) {
        try (Scope ignored = bind(userId)) {
            return work.get();
        }
    }

    public static void runAs(long userId, Runnable work) {
        try (Scope ignored = bind(userId)) {
            work.run();
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
