/*
 * This file is part of TorrentStreamer-Android.
 *
 * TorrentStreamer-Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TorrentStreamer-Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TorrentStreamer-Android. If not, see <http://www.gnu.org/licenses/>.
 */

package pct.droid.torrentstream.listeners;

import pct.droid.torrentstream.StreamStatus;
import pct.droid.torrentstream.Torrent;

public interface TorrentListener {
    void onStreamPrepared(Torrent torrent);

    void onStreamStarted(Torrent torrent);

    void onStreamError(Torrent torrent, Exception e);

    void onStreamReady(Torrent torrent);

    void onStreamProgress(Torrent torrent, StreamStatus status);

    void onStreamStopped();
}
