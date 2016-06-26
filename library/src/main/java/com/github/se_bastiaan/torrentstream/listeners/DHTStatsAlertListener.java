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

package com.github.se_bastiaan.torrentstream.listeners;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.DhtRoutingBucket;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.DhtStatsAlert;

import java.util.ArrayList;

public abstract class DHTStatsAlertListener implements AlertListener {
    @Override
    public int[] types() {
        return new int[]{AlertType.DHT_STATS.swig()};
    }

    public void alert(Alert<?> alert) {
        if (alert instanceof DhtStatsAlert) {
            DhtStatsAlert dhtAlert = (DhtStatsAlert) alert;
            stats(countTotalDHTNodes(dhtAlert));
        }
    }

    public abstract void stats(int totalDhtNodes);

    private int countTotalDHTNodes(DhtStatsAlert alert) {
        final ArrayList<DhtRoutingBucket> routingTable = alert.routingTable();

        int totalNodes = 0;
        if (routingTable != null) {
            for (DhtRoutingBucket bucket : routingTable) {
                totalNodes += bucket.numNodes();
            }
        }

        return totalNodes;
    }
}