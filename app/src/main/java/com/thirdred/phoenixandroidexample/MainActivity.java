package com.thirdred.phoenixandroidexample;

import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

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
    EditText messageText;
    Button sendButton;
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sendButton = (Button) findViewById(R.id.send);
        textView= (TextView) findViewById(R.id.text);
        messageText = (EditText) findViewById(R.id.message);
        sendButton.setEnabled(false);
        messageText.setEnabled(false);
        textView.setText("Connecting...");
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(messageText.getText().toString().trim().length()>0) {
                    String msg = messageText.getText().toString();
                    messageText.setText("");
                    ObjectNode node = new ObjectNode(JsonNodeFactory.instance)
                            .put("user", "bizzle")
                            .put("body", msg)
                            .put("ref", socket.makeRef());

                    try {
                        //Log.d("TAG","Sending message to socket");
                        channel.push("new:msg", node);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
        });

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
                    Log.d("TAG", "Socket opened");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textView.setText(textView.getText() + "\n" + "Connected!");
                            sendButton.setEnabled(true);
                            messageText.setEnabled(true);
                        }
                    });
                    joinChannel();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        socket.onClose(new ISocketCloseCallback() {
            @Override
            public void onClose() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText(textView.getText() + "\n" + "Closed!");
                        sendButton.setEnabled(false);
                        messageText.setEnabled(false);
                    }
                });
                Log.d("TAG", "Socket closed");

            }
        });
        socket.onMessage(new IMessageCallback() {
            @Override
            public void onMessage(Envelope envelope) {
                Log.d("TAG", "Received: "+envelope.toString());
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
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                textView.setText(textView.getText().toString() + "\n" + "Joined rooms:lobby!");
                            }
                        });
                        System.out.println("JOINED with " + envelope.toString());
                        //sendMessage();
                    }
                });

        channel.on("new:msg", new IMessageCallback() {
            @Override
            public void onMessage(Envelope envelope) {
                showMessage(envelope);
                System.out.println("NEW MESSAGE: " + envelope.toString());
            }
        });
        channel.on("new:message", new IMessageCallback() {
            @Override
            public void onMessage(Envelope envelope) {
                showMessage(envelope);
            }
        });
        channel.on("shout", new IMessageCallback() {
            @Override
            public void onMessage(Envelope envelope) {
                showMessage(envelope);
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
                Log.d("TAG", "Error: " + reason);
                System.out.println("ERROR: " + reason);
            }
        });
    }

    private void showMessage(final Envelope envelope) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addMessage("\nReceived: "+envelope.toString());
            }
        });
    }

    void addMessage(String text){
        textView.setText(textView.getText().toString()+ text);//"\nReceived: "+envelope.toString());
    }


}
