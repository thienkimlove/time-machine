package com.booking.replication.pipeline.event.handler;

/**
 * Created by edmitriev on 7/14/17.
 */
public class TransactionException extends Exception {
    public TransactionException(String string) {
        super(string);
    }
    public TransactionException(String string, Throwable throwable) {
        super(string, throwable);
    }
}
