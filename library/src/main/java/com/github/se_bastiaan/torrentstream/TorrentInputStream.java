package com.github.se_bastiaan.torrentstream;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

class TorrentInputStream extends FilterInputStream {
    private Torrent torrent;
    private boolean interrupted;
    private long location;

    TorrentInputStream(Torrent torrent, InputStream inputStream) {
        super(inputStream);

        this.torrent = torrent;
    }

    @Override
    protected void finalize() throws Throwable {
        synchronized (this) {
            interrupted = true;
        }

        super.finalize();
    }

    private synchronized boolean waitForPiece(long offset) {
        while (!Thread.currentThread().isInterrupted() && !interrupted) {
            try {
                if (torrent.hasBytes(offset)) {
                    return true;
                }

                wait(10);
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
            interrupted = true;
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
}