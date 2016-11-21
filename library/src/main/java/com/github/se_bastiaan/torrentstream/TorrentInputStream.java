package com.github.se_bastiaan.torrentstream;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

class TorrentInputStream extends FilterInputStream implements AlertListener {
    private Torrent torrent;
    private boolean stopped;
    private long location;

    TorrentInputStream(Torrent torrent, InputStream inputStream) {
        super(inputStream);

        this.torrent = torrent;
    }

    @Override
    protected void finalize() throws Throwable {
        synchronized (this) {
            stopped = true;
            notifyAll();
        }

        super.finalize();
    }

    private synchronized boolean waitForPiece(long offset) {
        while (!Thread.currentThread().isInterrupted() && !stopped) {
            try {
                if (torrent.hasBytes(offset)) {
                    return true;
                }

                wait();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        return false;
    }

    @Override
    public synchronized int read() throws IOException {
        if (!waitForPiece(location)) {
            return -1;
        }

        location++;

        return super.read();
    }

    @Override
    public synchronized int read(byte[] buffer, int offset, int length) throws IOException {
        int pieceLength = torrent.getTorrentHandle().torrentFile().pieceLength();

        for (int i = 0; i < length; i += pieceLength) {
            if (!waitForPiece(location + i)) {
                return -1;
            }
        }

        location += length;

        return super.read(buffer, offset, length);
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            stopped = true;
            notifyAll();
        }

        super.close();
    }

    @Override
    public synchronized long skip(long n) throws IOException {
        location += n;
        return super.skip(n);
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    private synchronized void pieceFinished() {
        notifyAll();
    }

    @Override
    public int[] types() {
        return new int[]{
                AlertType.PIECE_FINISHED.swig(),
        };
    }

    @Override
    public void alert(Alert<?> alert) {
        switch (alert.type()) {
            case PIECE_FINISHED:
                pieceFinished();
                break;
            default:
                break;
        }
    }
}