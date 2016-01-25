package org.phoenixframework.channels;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Push {
    private static final Logger LOG = Logger.getLogger(Push.class.getName());

    private Channel channel = null;
    private String event = null;
    private String refEvent = null;
    private JsonNode payload = null;
    private Envelope receivedEnvelope = null;
    private Map<String, List<IMessageCallback>> recHooks = new HashMap<>();
    private boolean sent = false;
    private AfterHook afterHook;

    Push(final Channel channel, final String event, final JsonNode payload) {
        this.channel = channel;
        this.event = event;
        this.payload = payload;
    }

    /**
     * Registers for notifications on status messages
     *
     * @param status The message status to register callbacks on
     * @param callback The callback handler
     *
     * @return This instance's self
     */
    public Push receive(final String status, final IMessageCallback callback) {
        if(this.receivedEnvelope != null) {
            final String receivedStatus = this.receivedEnvelope.getResponseStatus();
            if(receivedStatus != null && receivedStatus.equals(status)) {
                callback.onMessage(this.receivedEnvelope);
            }
        }
        synchronized(recHooks) {
            List<IMessageCallback> statusHooks = this.recHooks.get(status);
            if(statusHooks == null) {
                statusHooks = new ArrayList<>();
                this.recHooks.put(status, statusHooks);
            }
            statusHooks.add(callback);
        }

        return this;
    }

    public Push after(final long ms, final Runnable callback) {
        if(this.afterHook != null)
            throw new IllegalStateException("Only a single after hook can be applied to a Push");

        TimerTask timerTask = null;
        if(this.sent) {
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    callback.run();
                }
            };
            final Timer timer = new Timer("Phx after hook", true);
            this.channel.scheduleTask(timerTask, ms);
        }
        this.afterHook = new AfterHook(ms, callback, timerTask);
        return this;
    }




    void send() throws IOException {
        final String ref = channel.getSocket().makeRef();
        LOG.log(Level.FINE, "Push send, ref={0}", ref);

        this.refEvent = Socket.replyEventName(ref);
        this.receivedEnvelope = null;

        this.channel.on(this.refEvent, new IMessageCallback() {
            @Override
            public void onMessage(final Envelope envelope) {
                Push.this.receivedEnvelope = envelope;
                Push.this.matchReceive(receivedEnvelope.getResponseStatus(), envelope, ref);
                Push.this.cancelRefEvent();
                Push.this.cancelAfter();
            }
        });

        this.startAfter();
        this.sent = true;
        final Envelope envelope = new Envelope(this.channel.getTopic(), this.event, this.payload, ref);
        this.channel.getSocket().push(envelope);
    }

    private void cancelAfter() {
        if(this.afterHook != null) {
            this.afterHook.getTimerTask().cancel();
            this.afterHook.setTimerTask(null);
        }
    }

    private void startAfter() {
        if(this.afterHook != null) {
            final Runnable callback = new Runnable() {
                @Override
                public void run() {
                    Push.this.cancelRefEvent();
                    Push.this.afterHook.getCallback().run();
                }
            };
            this.afterHook.setTimerTask(new TimerTask() {
                @Override
                public void run() {
                    callback.run();
                }
            });
            this.channel.scheduleTask(this.afterHook.getTimerTask(), this.afterHook.getMs());
        }
    }

    private void matchReceive(final String status, final Envelope envelope, final String ref) {
        synchronized (recHooks) {
            final List<IMessageCallback> statusCallbacks = this.recHooks.get(status);
            if(statusCallbacks != null) {
                for (final IMessageCallback callback : statusCallbacks) {
                    callback.onMessage(envelope);
                }
            }
        }
    }

    Channel getChannel() {
        return channel;
    }

    String getEvent() {
        return event;
    }

    JsonNode getPayload() {
        return payload;
    }

    Envelope getReceivedEnvelope() {
        return receivedEnvelope;
    }

    Map<String, List<IMessageCallback>> getRecHooks() {
        return recHooks;
    }

    boolean isSent() {
        return sent;
    }

    private void cancelRefEvent() {
        this.channel.off(this.refEvent);
    }

    private class AfterHook {
        private final long ms;
        private final Runnable callback;
        private TimerTask timerTask;

        public AfterHook(final long ms, final Runnable callback, final TimerTask timerTask) {
            this.ms = ms;
            this.callback = callback;
            this.timerTask = timerTask;
        }

        public long getMs() {
            return ms;
        }

        public Runnable getCallback() {
            return callback;
        }

        public TimerTask getTimerTask() {
            return timerTask;
        }
        public void setTimerTask(final TimerTask timerTask) {
            this.timerTask = timerTask;
        }
    }
}