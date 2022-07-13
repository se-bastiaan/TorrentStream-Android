/*
 *
 *  * This file is part of TorrentStreamer-Android.
 *  *
 *  * TorrentStreamer-Android is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU Lesser General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * TorrentStreamer-Android is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU Lesser General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU Lesser General Public License
 *  * along with TorrentStreamer-Android. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.github.se_bastiaan.torrentstreamer.sample

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.viewbinding.BuildConfig
import com.github.se_bastiaan.torrentstream.StreamStatus
import com.github.se_bastiaan.torrentstream.Torrent
import com.github.se_bastiaan.torrentstream.TorrentOptions
import com.github.se_bastiaan.torrentstream.TorrentStream
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity(), TorrentListener {
    private lateinit var button: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var torrentStream: TorrentStream
    private var streamUrl = "magnet:?xt=urn:btih:88594aaacbde40ef3e2510c47374ec0aa396c08e&dn=bbb%5Fsunflower%5F1080p%5F30fps%5Fnormal.mp4&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80%2Fannounce&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80%2Fannounce&ws=http%3A%2F%2Fdistribution.bbb3d.renderfarming.net%2Fvideo%2Fmp4%2Fbbb%5Fsunflower%5F1080p%5F30fps%5Fnormal.mp4"
    var onClickListener = View.OnClickListener {
        progressBar.progress = 0
        if (torrentStream.isStreaming) {
            torrentStream.stopStream()
            button.text = "Start stream"
            return@OnClickListener
        }
        torrentStream.startStream(streamUrl)
        button.text = "Stop stream"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val action = intent.action
        val data = intent.data
        if (action != null && action == Intent.ACTION_VIEW && data != null) {
            try {
                streamUrl = URLDecoder.decode(data.toString(), "utf-8")
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }
        }
        val torrentOptions = TorrentOptions.Builder()
                .saveLocation(filesDir)
                .removeFilesAfterStop(true)
                .build()
        torrentStream = TorrentStream.init(torrentOptions)
        torrentStream.addListener(this)
        button = findViewById(R.id.button)
        button.setOnClickListener(onClickListener)
        progressBar = findViewById(R.id.progress)
        progressBar.max = 100
    }

    override fun onStreamPrepared(torrent: Torrent) {
        Log.d(TORRENT, "onStreamPrepared")
        // If you set TorrentOptions#autoDownload(false) then this is probably the place to call
        // torrent.startDownload();
    }

    override fun onStreamStarted(torrent: Torrent) {
        Log.d(TORRENT, "onStreamStarted")
    }

    override fun onStreamError(torrent: Torrent, e: Exception) {
        Log.e(TORRENT, "onStreamError", e)
        button.text = "Start stream"
    }

    override fun onStreamReady(torrent: Torrent) {
        progressBar.progress = 100
        val mediaFile = torrent.videoFile
        Log.d(TORRENT, "onStreamReady: $mediaFile")

        // Create a sharing intent
        startActivity(Intent().apply {
            action = Intent.ACTION_VIEW
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(mediaFile.extension)
            val authority = "com.github.se_bastiaan.torrentstreamer.sample.provider"
            data = FileProvider.getUriForFile(this@MainActivity, authority, mediaFile)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
    }

    override fun onStreamProgress(torrent: Torrent, status: StreamStatus) {
        if (status.bufferProgress <= 100 && progressBar.progress < 100 && progressBar.progress != status.bufferProgress) {
            Log.d(TORRENT, "Progress: " + status.bufferProgress)
            progressBar.progress = status.bufferProgress
        }
    }

    override fun onStreamStopped() {
        Log.d(TORRENT, "onStreamStopped")
    }

    companion object {
        private const val TORRENT = "Torrent"
    }
}