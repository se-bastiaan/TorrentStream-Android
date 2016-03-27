TorrentStream-Android [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.se_bastiaan/torrentstream-android/badge.svg)](https://maven-badges.herokuapp
.com/maven-central/com.github.se_bastiaan/torrentstream-android)
======

A torrent streamer library for Android based on [jlibtorrent](https://github.com/frostwire/frostwire-jlibtorrent).

Built for the [Butterproject](https://github.com/butterproject/butter-android).

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
    compile "com.github.se_bastiaan:TorrentStream-Android:2.0.0"
}
```

###Code samples

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

##License

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.