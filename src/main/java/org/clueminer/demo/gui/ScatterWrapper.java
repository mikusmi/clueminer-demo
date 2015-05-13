/*
 * Copyright (C) 2011-2015 clueminer.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.clueminer.demo.gui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;
import org.clueminer.clustering.api.Cluster;
import org.clueminer.clustering.api.Clustering;
import org.clueminer.clustering.api.ClusteringListener;
import org.clueminer.dataset.api.DataProvider;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.dendrogram.DataProviderMap;
import org.clueminer.distance.api.DistanceFactory;
import org.clueminer.distance.api.DistanceMeasure;
import org.clueminer.report.MemInfo;
import org.clueminer.scatter.ScatterPlot;
import org.clueminer.utils.Props;
import org.openide.util.RequestProcessor;
import org.openide.util.Task;
import org.openide.util.TaskListener;

/**
 *
 * @author deric
 */
public class ScatterWrapper extends AbstractClusteringViewer implements TaskListener, DatasetViewer {

    private static final long serialVersionUID = -8355392013651815767L;

    private ScatterPlot viewer;
    private static final RequestProcessor RP = new RequestProcessor("Clustering");
    private RequestProcessor.Task task;
    private Clustering<? extends Cluster> clust;

    public ScatterWrapper(Map<String, Dataset<? extends Instance>> data) {
        this(new DataProviderMap(data));
    }

    public ScatterWrapper(DataProvider provider) {
        super(provider);
    }

    @Override
    protected void initComponets() {
        GridBagLayout gbl = new GridBagLayout();
        setLayout(gbl);
        GridBagConstraints c = new GridBagConstraints();

        viewer = new ScatterPlot();
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 1;
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.NORTHEAST;
        c.weightx = c.weighty = 1.0; //ratio for filling the frame space
        gbl.setConstraints((Component) viewer, c);
        this.add((Component) viewer, c);
        setVisible(true);
    }

    public void setClustering(Clustering clusters) {
        viewer.setClustering(clusters);
    }


    public void execute(final Props params) {
        if (algorithm == null) {
            throw new RuntimeException("no algorithm was set");
        }
        task = RP.create(new Runnable() {

            @Override
            public void run() {
                System.out.println("algorithm: " + algorithm.getName());
                params.put("name", getAlgorithm().getName());
                System.out.println(params.toString());
                DistanceFactory df = DistanceFactory.getInstance();
                DistanceMeasure func = df.getProvider("Euclidean");
                algorithm.setDistanceFunction(func);

                MemInfo memInfo = new MemInfo();
                exec.setAlgorithm(algorithm);
                clust = exec.clusterRows(dataset, params);
                memInfo.report();
                System.out.println("------");
            }

        });
        task.addTaskListener(this);
        task.schedule(0);
    }

    @Override
    public void taskFinished(Task task) {
        if (clust != null && clust.size() > 0 && clust.instancesCount() > 0) {
            viewer.setClustering(clust);
            fireClusteringChanged(clust);

            validate();
            revalidate();
            repaint();
        } else {
            System.err.println("invalid clustering");
        }
    }

    public void addClusteringListener(ClusteringListener listener) {
        clusteringListeners.add(ClusteringListener.class, listener);
    }

    public void fireClusteringChanged(Clustering clust) {
        for (ClusteringListener listener : clusteringListeners.getListeners(ClusteringListener.class)) {
            listener.clusteringChanged(clust);
        }
    }
}
