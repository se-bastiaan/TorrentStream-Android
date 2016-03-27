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

package com.github.se_bastiaan.torrentstream;

import java.io.File;

public class TorrentOptions {

    protected String saveLocation = "/";
    protected String proxyHost;
    protected String proxyUsername;
    protected String proxyPassword;
    protected String peerFingerprint;
    protected Integer maxDownloadSpeed = 0;
    protected Integer maxUploadSpeed = 0;
    protected Integer maxConnections = 200;
    protected Integer maxDht = 88;
    protected Integer listeningPort = -1;
    protected Boolean removeFiles = false;
    protected Boolean anonymousMode = false;
    protected Long prepareSize = 10 * 1024L * 1024L;

    private TorrentOptions() {
        // Unused
    }

    private TorrentOptions(TorrentOptions torrentOptions) {
        this.saveLocation = torrentOptions.saveLocation;
        this.proxyHost = torrentOptions.proxyHost;
        this.proxyUsername = torrentOptions.proxyUsername;
        this.proxyPassword = torrentOptions.proxyPassword;
        this.peerFingerprint = torrentOptions.peerFingerprint;
        this.maxDownloadSpeed = torrentOptions.maxDownloadSpeed;
        this.maxUploadSpeed = torrentOptions.maxUploadSpeed;
        this.maxConnections = torrentOptions.maxConnections;
        this.maxDht = torrentOptions.maxDht;
        this.listeningPort = torrentOptions.listeningPort;
        this.removeFiles = torrentOptions.removeFiles;
        this.anonymousMode = torrentOptions.anonymousMode;
        this.prepareSize = torrentOptions.prepareSize;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static class Builder {

        private TorrentOptions torrentOptions;

        public Builder() {
            torrentOptions = new TorrentOptions();
        }

        private Builder(TorrentOptions torrentOptions) {
            torrentOptions = new TorrentOptions(torrentOptions);
        }

        public Builder saveLocation(String saveLocation) {
            torrentOptions.saveLocation = saveLocation;
            return this;
        }

        public Builder saveLocation(File saveLocation) {
            torrentOptions.saveLocation = saveLocation.getAbsolutePath();
            return this;
        }

        public Builder maxUploadSpeed(Integer maxUploadSpeed) {
            torrentOptions.maxUploadSpeed = maxUploadSpeed;
            return this;
        }

        public Builder maxDownloadSpeed(Integer maxDownloadSpeed) {
            torrentOptions.maxDownloadSpeed = maxDownloadSpeed;
            return this;
        }

        public Builder maxConnections(Integer maxConnections) {
            torrentOptions.maxConnections = maxConnections;
            return this;
        }

        public Builder maxActiveDHT(Integer maxActiveDHT) {
            torrentOptions.maxDht = maxActiveDHT;
            return this;
        }

        public Builder removeFilesAfterStop(Boolean b) {
            torrentOptions.removeFiles = b;
            return this;
        }

        public Builder prepareSize(Long prepareSize) {
            torrentOptions.prepareSize = prepareSize;
            return this;
        }

        public Builder listeningPort(Integer port) {
            torrentOptions.listeningPort = port;
            return this;
        }

        public Builder proxy(String host, String username, String password) {
            torrentOptions.proxyHost = host;
            torrentOptions.proxyUsername = username;
            torrentOptions.proxyPassword = password;
            return this;
        }

        public Builder peerFingerprint(String peerId) {
            torrentOptions.peerFingerprint = peerId;
            torrentOptions.anonymousMode = false;
            return this;
        }

        public Builder anonymousMode(Boolean enable) {
            torrentOptions.anonymousMode = enable;
            if (torrentOptions.anonymousMode)
                torrentOptions.peerFingerprint = null;
            return this;
        }

        public TorrentOptions build() {
            return torrentOptions;
        }

    }

}
