package com.npcwars.chat;

/** The chat backend could not produce a reply (network, authentication, refusal...). */
public final class ChatException extends Exception {

    public ChatException(String message) {
        super(message);
    }

    public ChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
