package com.bukcase.identity;

/**
 * Deliberately not an {@link IllegalStateException}: Spring's persistence exception translation
 * would rewrap that into a data access exception when thrown while building a query.
 */
public class NoCurrentUserException extends RuntimeException {

    public NoCurrentUserException() {
        super("No user bound to the current execution context");
    }
}
