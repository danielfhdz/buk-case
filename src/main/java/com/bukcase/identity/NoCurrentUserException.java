package com.bukcase.identity;

public class NoCurrentUserException extends IllegalStateException {

    public NoCurrentUserException() {
        super("No user bound to the current execution context");
    }
}
