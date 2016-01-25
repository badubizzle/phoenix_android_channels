package org.phoenixframework.channels;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Created by badu on 1/25/16.
 */
public interface ISocket {
    void disconnect() throws IOException;

    void connect() throws IOException;

    boolean isConnected();

    Channel chan(String topic, JsonNode payload);

    void remove(Channel channel);

    ISocket push(Envelope envelope) throws IOException;

    ISocket onOpen(ISocketOpenCallback callback);

    ISocket onClose(ISocketCloseCallback callback);

    ISocket onError(IErrorCallback callback);

    ISocket onMessage(IMessageCallback callback);

    @Override
    String toString();

    public String makeRef();
}
