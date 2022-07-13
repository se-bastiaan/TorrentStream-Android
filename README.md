TorrentStream-Android [![Release](https://jitpack.io/v/TorrentStream/TorrentStream-Android.svg)](https://jitpack.io/#TorrentStream/TorrentStream-Android)
======

A torrent streamer library for Android based on [libtorrent4j](https://github.com/aldenml/libtorrent4j).

Once built for the Popcorn Time and the [Butterproject](https://github.com/butterproject/butter-android). Now just a cool library for anyone to use.

## How to use

Add Jitpack in your root build.gradle at the end of repositories:
```groovy
allprojects {
    repositories {
        ...
        maven { url "https://jitpack.io" }
    }
}
```

Add to your dependencies:

```groovy
dependencies {
    compile "com.github.TorrentStream:TorrentStream-Android:${torrentstreamVersion}"
}
```

Add to your AndroidManifest.xml (only for saving to the external storage):

```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```

### Code samples

Create your own `TorrentOptions` instance using the builder and feed it to a new `TorrentStream`.

```java
TorrentOptions torrentOptions = new TorrentOptions.Builder()
                .saveLocation(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                .removeFilesAfterStop(true)
                .build();

TorrentStream torrentStream = TorrentStream.init(torrentOptions);
torrentStream.startStream("https://butterpoject.org/test.torrent");
```

If you want to get status information about the torrent then you might want to use `addListener` to attach a listener to your `TorrentStream` instance.

## License

    Copyright 2015-2022 Sébastiaan (github.com/se-bastiaan)

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
