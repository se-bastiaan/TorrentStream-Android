/*
 *
 *  * This file is part of TorrentStreamer-Android.
 *  *
 *  * TorrentStreamer-Android is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * TorrentStreamer-Android is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with TorrentStreamer-Android. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package eu.sv244.torrentstreamer.sample;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.github.sv244.torrentstream.StreamStatus;
import com.github.sv244.torrentstream.Torrent;
import com.github.sv244.torrentstream.TorrentOptions;
import com.github.sv244.torrentstream.TorrentStream;
import com.github.sv244.torrentstream.listeners.TorrentListener;

public class MainActivity extends AppCompatActivity implements TorrentListener {

    private Button mButton;
    private ProgressBar mProgressBar;
    private TorrentStream mTorrentStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TorrentOptions torrentOptions = new TorrentOptions();
        torrentOptions.setSaveLocation(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        torrentOptions.setRemoveFilesAfterStop(true);

        mTorrentStream = TorrentStream.init(torrentOptions);
        mTorrentStream.addListener(this);

        mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(mOnClickListener);
        mProgressBar = (ProgressBar) findViewById(R.id.progress);

        mProgressBar.setMax(100);
    }

    View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mProgressBar.setProgress(0);
            if(mTorrentStream.isStreaming()) {
                mTorrentStream.stopStream();
                mButton.setText("Start stream");
                return;
            }
            //mTorrentStream.startStream("https://yts.to/torrent/download/D60795899F8488E7E489BA642DEFBCE1B23C9DA0.torrent");
            mTorrentStream.startStream("magnet:?xt=urn:btih:D60795899F8488E7E489BA642DEFBCE1B23C9DA0&dn=Kingsman%3A+The+Secret+Service+%282014%29+%5B720p%5D&tr=http%3A%2F%2Ftracker.yify-torrents.com%2Fannounce&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.org%3A80&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969&tr=udp%3A%2F%2Fopen.demonii.com%3A1337&tr=udp%3A%2F%2Fp4p.arenabg.ch%3A1337&tr=udp%3A%2F%2Fp4p.arenabg.com%3A1337");
            mButton.setText("Stop stream");
        }
    };

    @Override
    public void onStreamPrepared(Torrent torrent) {
        Log.d("Torrent", "OnStreamPrepared");
        torrent.startDownload();
    }

    @Override
    public void onStreamStarted(Torrent torrent) {
        Log.d("Torrent", "onStreamStarted");
    }

    @Override
    public void onStreamError(Torrent torrent, Exception e) {
        Log.d("Torrent", "onStreamError");
    }

    @Override
    public void onStreamReady(Torrent torrent) {
        mProgressBar.setProgress(100);
        Log.d("Torrent", "onStreamReady: " + torrent.getVideoFile());

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(torrent.getVideoFile().toString()));
        intent.setDataAndType(Uri.parse(torrent.getVideoFile().toString()), "video/mp4");
        startActivity(intent);
    }

    @Override
    public void onStreamProgress(Torrent torrent, StreamStatus status) {
        if(status.bufferProgress <= 100 && mProgressBar.getProgress() < 100 && mProgressBar.getProgress() != status.bufferProgress) {
            Log.d("Torrent", "Progress: " + status.bufferProgress);
            mProgressBar.setProgress(status.bufferProgress);
        }
    }

    @Override
    public void onStreamStopped() {
        Log.d("Torrent", "onStreamStopped");
    }
}
