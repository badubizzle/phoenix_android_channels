package com.thirdred.phoenixandroidexample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.phoenixframework.channels.Channel;
import org.phoenixframework.channels.Envelope;
import org.phoenixframework.channels.IErrorCallback;
import org.phoenixframework.channels.IMessageCallback;
import org.phoenixframework.channels.ISocket;
import org.phoenixframework.channels.ISocketCloseCallback;
import org.phoenixframework.channels.ISocketOpenCallback;
import org.phoenixframework.channels.Socket;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private ISocket socket;
    private Channel channel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            startWs();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startWs() throws IOException {


        Log.d("TAG", "Starting socket server");

        if(socket !=null && socket.isConnected()){
            socket.disconnect();
        }
        socket = new Socket("ws://10.0.2.2:5000/socket/websocket/");

        socket.onOpen(new ISocketOpenCallback() {
            @Override
            public void onOpen() {
                try {
                    Log.d("TAG","Socket opened");
                    joinChannel();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        socket.onClose(new ISocketCloseCallback() {
            @Override
            public void onClose() {
                Log.d("TAG", "Socket closed");
            }
        });
        socket.connect();




//Sending a message. This library uses Jackson for JSON serialization

    }

    private void joinChannel() throws IOException {
        Log.d("TAG", "Adding channels");
        JsonNode joinPayload = new ObjectNode(JsonNodeFactory.instance)
                .put("user","bizzle");

        channel = socket.chan("rooms:lobby", joinPayload);

        channel.join()
                .receive("ignore", new IMessageCallback() {
                    @Override
                    public void onMessage(Envelope envelope) {
                        System.out.println("IGNORE");
                    }
                })
                .receive("ok", new IMessageCallback() {
                    @Override
                    public void onMessage(Envelope envelope) {
                        System.out.println("JOINED with " + envelope.toString());
                        sendMessage();
                    }
                });

        channel.on("new:msg", new IMessageCallback() {
            @Override
            public void onMessage(Envelope envelope) {
                System.out.println("NEW MESSAGE: " + envelope.toString());
            }
        });

        channel.onClose(new IMessageCallback() {
            @Override
            public void onMessage(Envelope envelope) {
                System.out.println("CLOSED: " + envelope.toString());
            }
        });

        channel.onError(new IErrorCallback() {
            @Override
            public void onError(String reason) {
                Log.d("TAG", "Error: "+reason);
                System.out.println("ERROR: " + reason);
            }
        });
    }

    private void sendMessage() {
        ObjectNode node = new ObjectNode(JsonNodeFactory.instance)
                .put("user", "my_username")
                .put("body", "Hello World");

        try {
            Log.d("TAG","Sending message to socket");
            channel.push("new:msg", node);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
