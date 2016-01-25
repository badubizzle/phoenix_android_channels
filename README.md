# phoenix_android_channels
Pheonix android channel (websocket) client based on https://github.com/eoinsha/JavaPhoenixChannels and https://github.com/koush/AndroidAsync

The original lib (https://github.com/eoinsha/JavaPhoenixChannels) uses com.squareup.okhttp:okhttp
which was giving me issues so I substituted https://github.com/koush/AndroidAsync for handling the socket connection

I added ISocket Interface to allow you to plug the new socket implementation easily into existing code.

All other files remain same as from the original lib, more or less.
