/*
 * Copyright (C) 2015-2018 Sébastiaan (github.com/se-bastiaan)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.se_bastiaan.torrentstream.listeners;

import com.github.se_bastiaan.torrentstream.StreamStatus;
import com.github.se_bastiaan.torrentstream.Torrent;

public interface TorrentListener {
    void onStreamPrepared(Torrent torrent);

    void onStreamStarted(Torrent torrent);

    void onStreamError(Torrent torrent, Exception e);

    void onStreamReady(Torrent torrent);

    void onStreamProgress(Torrent torrent, StreamStatus status);

    void onStreamStopped();
}
