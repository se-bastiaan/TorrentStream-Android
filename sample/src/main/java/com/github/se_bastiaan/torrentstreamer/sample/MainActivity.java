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

package com.github.se_bastiaan.torrentstreamer.sample;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.github.se_bastiaan.torrentstream.StreamStatus;
import com.github.se_bastiaan.torrentstream.Torrent;
import com.github.se_bastiaan.torrentstream.TorrentOptions;
import com.github.se_bastiaan.torrentstream.TorrentStream;
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity implements TorrentListener {

    private Button button;
    private ProgressBar progressBar;
    private TorrentStream torrentStream;

    private String mStreamUrl = "magnet:?xt=urn:btih:88594aaacbde40ef3e2510c47374ec0aa396c08e&dn=bbb%5Fsunflower%5F1080p%5F30fps%5Fnormal.mp4&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80%2Fannounce&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80%2Fannounce&ws=http%3A%2F%2Fdistribution.bbb3d.renderfarming.net%2Fvideo%2Fmp4%2Fbbb%5Fsunflower%5F1080p%5F30fps%5Fnormal.mp4";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String action = getIntent().getAction();
        Uri data = getIntent().getData();
        if (action != null && action.equals(Intent.ACTION_VIEW) && data != null) {
            try {
                mStreamUrl = URLDecoder.decode(data.toString(), "utf-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        TorrentOptions torrentOptions = new TorrentOptions.Builder()
                .saveLocation(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                .removeFilesAfterStop(true)
                .build();

        torrentStream = TorrentStream.init(torrentOptions);
        torrentStream.addListener(this);

        button = (Button) findViewById(R.id.button);
        button.setOnClickListener(mOnClickListener);
        progressBar = (ProgressBar) findViewById(R.id.progress);

        progressBar.setMax(100);
    }

    View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            progressBar.setProgress(0);
            if(torrentStream.isStreaming()) {
                torrentStream.stopStream();
                button.setText("Start stream");
                return;
            }
            torrentStream.startStream(mStreamUrl);
            button.setText("Stop stream");
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
        Log.e("Torrent", "onStreamError", e);
        button.setText("Start stream");
    }

    @Override
    public void onStreamReady(Torrent torrent) {
        progressBar.setProgress(100);
        Log.d("Torrent", "onStreamReady: " + torrent.getVideoFile());

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(torrent.getVideoFile().toString()));
        intent.setDataAndType(Uri.parse(torrent.getVideoFile().toString()), "video/mp4");
        startActivity(intent);
    }

    @Override
    public void onStreamProgress(Torrent torrent, StreamStatus status) {
        if(status.bufferProgress <= 100 && progressBar.getProgress() < 100 && progressBar.getProgress() != status.bufferProgress) {
            Log.d("Torrent", "Progress: " + status.bufferProgress);
            progressBar.setProgress(status.bufferProgress);
        }
    }

    @Override
    public void onStreamStopped() {
        Log.d("Torrent", "onStreamStopped");
    }
}
