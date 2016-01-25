package org.phoenixframework.channels;

import android.net.Uri;
import android.util.Log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.AsyncHttpRequest;
import com.koushikdutta.async.http.WebSocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by badu on 1/25/16.
 */
public class Socket implements ISocket {
    private static final Logger LOG = Logger.getLogger(Socket.class.getName());

    public static final int RECONNECT_INTERVAL_MS = 5000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AsyncHttpRequest request;
    private com.koushikdutta.async.http.WebSocket webSocket = null;

    private String endpointUri = null;
    private final List<Channel> channels = new ArrayList<>();

    private Timer timer = null;
    private TimerTask reconnectTimerTask = null;

    private Set<ISocketOpenCallback> socketOpenCallbacks = Collections.newSetFromMap(new WeakHashMap<ISocketOpenCallback, Boolean>());
    private Set<ISocketCloseCallback> socketCloseCallbacks = Collections.newSetFromMap(new WeakHashMap<ISocketCloseCallback, Boolean>());
    private Set<IErrorCallback> errorCallbacks = Collections.newSetFromMap(new WeakHashMap<IErrorCallback, Boolean>());
    private Set<IMessageCallback> messageCallbacks = Collections.newSetFromMap(new WeakHashMap<IMessageCallback, Boolean>());

    private int refNo = 1;

    /**
     * Annotated WS Endpoint. Private member to prevent confusion with "onConn*" registration methods.
     */

    private AsyncHttpClient.WebSocketConnectCallback wsCallback =new AsyncHttpClient.WebSocketConnectCallback(){
        @Override
        public void onCompleted(Exception ex, com.koushikdutta.async.http.WebSocket webSocket) {
            if (ex != null) {
                ex.printStackTrace();
                return;
            }
            LOG.log(Level.FINE, "WebSocket onOpen: {0}", webSocket);
            cancelReconnectTimer();

            // TODO - Heartbeat
            for (final ISocketOpenCallback callback : socketOpenCallbacks) {
                callback.onOpen();
            }

            Socket.this.webSocket = webSocket;
            Socket.this.flushSendBuffer();

            webSocket.setStringCallback(new WebSocket.StringCallback() {
                @Override
                public void onStringAvailable(String payload) {
                    LOG.log(Level.FINE, "Envelope received: {0}", payload);
                    Log.d("TAG", payload);
                    try {
                            final Envelope envelope = objectMapper.readValue(payload, Envelope.class);
                            if(channels!=null) {
                                for (final Channel channel : channels) {
                                    if (channel.isMember(envelope.getTopic())) {
                                        channel.trigger(envelope.getEvent(), envelope);
                                    }
                                }
                            }
                            for (final IMessageCallback callback : messageCallbacks) {
                                callback.onMessage(envelope);
                            }
                    } catch (IOException e) {
                        e.printStackTrace();
                        LOG.log(Level.SEVERE, "Failed to read message payload", e);
                    } finally {

                    }
                }
            });

            webSocket.setEndCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
//                    LOG.log(Level.FINE, "WebSocket onClose {0}/{1}", new Object[]{ex.getMessage(), ex.getLocalizedMessage()});
//                    Socket.this.webSocket = null;
//                    scheduleReconnectTimer();
//                    for (final ISocketCloseCallback callback : socketCloseCallbacks) {
//                        callback.onClose();
//                    }
                }
            });
            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    ex.printStackTrace();
                    LOG.log(Level.FINE, "WebSocket onClose {0}/{1}", new Object[]{ex.getMessage(), ex.getLocalizedMessage()});
                    Socket.this.webSocket = null;
                    scheduleReconnectTimer();
                    for (final ISocketCloseCallback callback : socketCloseCallbacks) {
                        callback.onClose();
                    }
                }
            });
        }
    };

    public Socket(final String endpointUri) throws IOException {
        LOG.log(Level.FINE, "PhoenixSocket({0})", endpointUri);
        this.endpointUri = endpointUri;
        this.timer = new Timer("Reconnect Timer for " + endpointUri);
    }

    private void scheduleReconnectTimer() {
        cancelReconnectTimer();

        // TODO - Clear heartbeat timer

        Socket.this.reconnectTimerTask = new TimerTask() {
            @Override
            public void run() {
                LOG.log(Level.FINE, "reconnectTimerTask run");
                try {
                    Socket.this.connect();
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Failed to reconnect to " + Socket.this.request.getUri().toString(), e);
                }
            }
        };
        timer.schedule(Socket.this.reconnectTimerTask, RECONNECT_INTERVAL_MS);
    }

    public void connect() throws IOException {
        LOG.log(Level.FINE, "connect");
        disconnect();
        // No support for ws:// or ws:// in okhttp. See https://github.com/square/okhttp/issues/1652
        final String httpUrl = this.endpointUri.replaceFirst("^ws:", "http:").replaceFirst("^wss:", "https:");

        this.request = new AsyncHttpRequest(Uri.parse(httpUrl), "GET");
        AsyncHttpClient.getDefaultInstance().websocket(request, "my-protocol",  new AsyncHttpClient.WebSocketConnectCallback(){
            @Override
            public void onCompleted(Exception ex, com.koushikdutta.async.http.WebSocket webSocket) {
                if (ex != null) {
                    return;
                }
                LOG.log(Level.FINE, "WebSocket onOpen: {0}", webSocket);
                cancelReconnectTimer();

                // TODO - Heartbeat
                for (final ISocketOpenCallback callback : socketOpenCallbacks) {
                    callback.onOpen();
                }

                Socket.this.webSocket = webSocket;
                Socket.this.flushSendBuffer();

                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    @Override
                    public void onStringAvailable(String payload) {
                        Log.d("TAG", payload);
                        LOG.log(Level.FINE, "Envelope received: {0}", payload);
                        Log.d("TAG", payload);
                        try {
                            final Envelope envelope = objectMapper.readValue(payload, Envelope.class);
                            if(channels!=null) {
                                for (final Channel channel : channels) {
                                    if (channel.isMember(envelope.getTopic())) {
                                        channel.trigger(envelope.getEvent(), envelope);
                                    }
                                }
                            }
                            for (final IMessageCallback callback : messageCallbacks) {
                                callback.onMessage(envelope);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            LOG.log(Level.SEVERE, "Failed to read message payload", e);
                        } finally {

                        }
                    }
                });

                webSocket.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
//                    LOG.log(Level.FINE, "WebSocket onClose {0}/{1}", new Object[]{ex.getMessage(), ex.getLocalizedMessage()});
//                    Socket.this.webSocket = null;
//                    scheduleReconnectTimer();
//                    for (final ISocketCloseCallback callback : socketCloseCallbacks) {
//                        callback.onClose();
//                    }
                    }
                });
                webSocket.setClosedCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        //ex.printStackTrace();
                        LOG.log(Level.FINE, "WebSocket onClose ");
                        Socket.this.webSocket = null;
                        scheduleReconnectTimer();
                        for (final ISocketCloseCallback callback : socketCloseCallbacks) {
                            callback.onClose();
                        }
                    }
                });
            }
        });

    }

    @Override
    public boolean isConnected() {
        return this.webSocket!=null;// this.webSocket.isOpen();
    }

    @Override
    public Channel chan(String topic, JsonNode payload) {
        LOG.log(Level.FINE, "chan: {0}, {1}", new Object[]{topic, payload});
        final Channel channel = new Channel(topic, payload, Socket.this);
        synchronized (channels) {
            channels.add(channel);
        }
        return channel;
    }

    @Override
    public void remove(Channel channel) {
        synchronized (channels) {
            for (final Iterator chanIter = channels.iterator(); chanIter.hasNext(); ) {
                if (chanIter.next() == channel) {
                    chanIter.remove();
                    break;
                }
            }
        }
    }

    @Override
    public ISocket push(Envelope envelope) throws IOException {

        LOG.log(Level.FINE, "Pushing envelope: {0}", envelope);
        final ObjectNode node = objectMapper.createObjectNode();
        node.put("topic", envelope.getTopic());
        node.put("event", envelope.getEvent());
        node.put("ref", envelope.getRef());
        node.set("payload", envelope.getPayload() == null ? objectMapper.createObjectNode() : envelope.getPayload());
        final String json = objectMapper.writeValueAsString(node);
        LOG.log(Level.FINE, "Sending JSON: {0}", json);


        //RequestBody body = RequestBody.create(com.squareup.okhttp.ws.WebSocket.TEXT, json);



        if (this.isConnected()) {

            try {
                webSocket.send(json);
            } catch(Exception e) {
                Log.d("TAG", "Error: "+e.getMessage());
                LOG.log(Level.SEVERE, "Attempted to send push when socket is not open", e);
            }
        } else {
            this.sendBuffer.add(json);
        }

        return this;
    }

    @Override
    public ISocket onOpen(ISocketOpenCallback callback) {
        this.socketOpenCallbacks.add(callback);
        return this;
    }

    @Override
    public ISocket onClose(ISocketCloseCallback callback) {
        this.socketCloseCallbacks.add(callback);
        return this;
    }

    @Override
    public ISocket onError(IErrorCallback callback) {
        this.errorCallbacks.add(callback);
        return this;
    }

    @Override
    public ISocket onMessage(IMessageCallback callback) {
        this.messageCallbacks.add(callback);
        return this;
    }

    @Override
    public synchronized String makeRef() {
        int val = refNo++;
        if (refNo == Integer.MAX_VALUE) {
            refNo = 0;
        }
        return Integer.toString(val);
    }

    public void disconnect() throws IOException {
        LOG.log(Level.FINE, "disconnect");
        if (webSocket != null) {
            //webSocket.close(1001 /*CLOSE_GOING_AWAY*/, "Disconnected by client");
            webSocket.close();
        }
    }

    private void cancelReconnectTimer() {
        if (Socket.this.reconnectTimerTask != null) {
            Socket.this.reconnectTimerTask.cancel();
        }
    }

    private void flushSendBuffer() {
        while (this.webSocket.isOpen() && !this.sendBuffer.isEmpty()) {
            final String body = this.sendBuffer.removeFirst();
            try {
                this.webSocket.send(body);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to send payload {0}", body);
            }
        }
    }

    private ConcurrentLinkedDeque<String> sendBuffer = new ConcurrentLinkedDeque<>();

    public static String replyEventName(String ref) {
        return "chan_reply_" + ref;
    }
}
