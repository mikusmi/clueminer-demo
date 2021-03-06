/*
 * Copyright (C) 2011-2017 clueminer.org
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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import javax.swing.JPanel;
import org.clueminer.clustering.api.Algorithm;
import org.clueminer.clustering.api.Cluster;
import org.clueminer.clustering.api.Clustering;
import org.clueminer.clustering.api.EvaluationTable;
import org.clueminer.clustering.api.HierarchicalResult;
import org.clueminer.clustering.api.factory.Clusterings;
import org.clueminer.dataset.api.ColorGenerator;
import org.clueminer.dataset.api.DataProvider;
import org.clueminer.dataset.api.Dataset;
import org.clueminer.dataset.api.Instance;
import org.clueminer.dendrogram.DataProviderMap;
import org.clueminer.eval.utils.HashEvaluationTable;
import org.clueminer.kdtree.KDTree;
import org.clueminer.kdtree.KeyDuplicateException;
import org.clueminer.kdtree.KeySizeException;
import org.clueminer.scatter.ScatterPlot;
import org.clueminer.utils.Props;
import org.openide.util.Exceptions;
import org.openide.util.Task;
import org.openide.util.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author deric
 * @param <E>
 * @param <C>
 */
public class ScatterViewer<E extends Instance, C extends Cluster<E>>
        extends AbstractClusteringViewer<E, C> implements TaskListener, DatasetViewer<E, C> {

    private static final long serialVersionUID = -8355392013651815767L;

    private ScatterPlot viewer;
    private Scatter3DPlot viewer3d;
    private Point startDrag;
    protected Dimension reqSize = new Dimension(0, 0);
    private Shape selection;
    private static final Color DRAWING_RECT_COLOR = new Color(200, 200, 255);
    private Rectangle rect = null;
    private boolean drawing = false;
    private Point mousePress = null;
    private KDTree<E> kdTree;
    private List<E> items;
    private boolean mode2d = true;
    private static Logger LOG = LoggerFactory.getLogger(ScatterViewer.class);

    public ScatterViewer(Map<String, Dataset<? extends Instance>> data) {
        this(new DataProviderMap(data));
    }

    public ScatterViewer(DataProvider provider) {
        super(provider);
    }

    @Override
    protected void initComponets() {
        GridBagLayout gbl = new GridBagLayout();
        setLayout(gbl);
        GridBagConstraints c = new GridBagConstraints();

        ScattMouseListener ml = new ScattMouseListener();
        //panel = new JLayeredPane();
        viewer = new ScatterPlot(ml, ml);
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHEAST;
        c.weightx = c.weighty = 1.0; //ratio for filling the frame space
        gbl.setConstraints((Component) viewer, c);
        this.add((Component) viewer, c);
        setVisible(true);
    }

    public void setClustering(Clustering clusters) {
        if (mode2d) {
            System.out.println("clusters: " + clusters.size());
            viewer.setClustering(clusters);
        } else {
            viewer3d.setClustering(clusters);
        }

        this.clust = clusters;
    }

    @Override
    public void setSize(int w, int h) {
        super.setSize(w, h);
        if (mode2d) {
            viewer.setSize(w, h);
        } else {
            viewer3d.setSize(w, h);
        }
    }

    public void setSimpleMode(boolean b) {
        viewer.setSimpleMode(b);
    }

    /**
     * Instead of running real clustering we'll display dataset with class
     * labels
     *
     * @param params
     */
    @Override
    public void execute(final Props params) {

        task = RP.post(new Runnable() {

            @Override
            public void run() {
                fireClusteringStarted(dataset, params);
                clust = goldenClustering(dataset);

            }

        });
        task.addTaskListener(this);
    }

    public Clustering goldenClustering(Dataset<? extends Instance> dataset) {
        SortedSet set = dataset.getClasses();
        Clustering golden = Clusterings.newList();

        EvaluationTable evalTable = new HashEvaluationTable(golden, dataset);
        golden.lookupAdd(evalTable);
        HashMap<Object, Integer> map = new HashMap<>(set.size());
        Object obj;
        Iterator it = set.iterator();
        int i = 0;
        Cluster c;
        cg.reset();
        int avgSize = (int) Math.sqrt(dataset.size());
        while (it.hasNext()) {
            obj = it.next();
            c = golden.createCluster(i, avgSize, obj.toString());
            c.setAttributes(dataset.getAttributes());
            c.setColor(cg.next());
            map.put(obj, i++);
        }

        int assign;

        Object klass;
        for (Instance inst : dataset) {
            if (map.containsKey(inst.classValue())) {
                assign = map.get(inst.classValue());
                c = golden.get(assign);
            } else {
                c = golden.createCluster(i);
                c.setAttributes(dataset.getAttributes());
                klass = inst.classValue();
                map.put(klass, i++);
            }
            c.add(inst);
        }
        golden.lookupAdd(dataset);

        return golden;
    }

    @Override
    public void taskFinished(Task task) {
        if (clust != null && clust.size() > 0 && clust.instancesCount() > 0) {
            setClustering(clust);
            fireClusteringChanged(clust);

            LOG.info("task finished");
            validate();
            revalidate();
            repaint();
        } else {
            System.err.println("invalid clustering");
        }
    }

    /**
     * Triggered when dataset was changed
     *
     * @param dataset
     * @param params
     */
    @Override
    public void clusteringStarted(Dataset<E> dataset, Props params) {
        //
    }

    @Override
    public void clusteringChanged(Clustering<E, C> clust) {
        if (clust != null) {
            setClustering(clust);
        }
    }

    @Override
    public void resultUpdate(HierarchicalResult hclust) {
        //
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        render((Graphics2D) g);
    }

    public void render(Graphics2D g2) {
        if (drawing) {
            g2.setColor(DRAWING_RECT_COLOR);
            g2.draw(rect);
        }

        if (selection != null) {
            //image = getScreenShot();
            //Graphics2D g2 = bufferedImage.createGraphics();
            //g2.drawImage(image, WIDTH, 0, this);
            Stroke stroke = new BasicStroke(2);
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            //g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.50f));
            g2.setStroke(stroke);
            g2.setColor(new Color(160, 160, 160, 128));
            g2.setPaint(Color.LIGHT_GRAY);

            //draw selection
            g2.setColor(Color.RED);
            if (items != null) {
                for (E item : items) {
                    drawCircle(g2, translate(item), stroke, 4);
                }
            }

            //Area fill = new Area(new Rectangle(new Point(0, 0), getSize()));
            //g2.fill(fill);
            g2.draw(selection);
            g2.dispose();
        }
    }

    public void drawCircle(Graphics2D g, Point2D point, Stroke stroke, int markerSize) {
        g.setStroke(stroke);
        double halfSize = (double) markerSize / 2;
        Shape circle = new Ellipse2D.Double(point.getX() - halfSize, point.getY() - halfSize, markerSize, markerSize);
        g.fill(circle);

    }

    private void findPoints(Shape selection) {
        if (mode2d) {
            Rectangle2D.Double rectangle = viewer.tranlateSelection(selection);
            double[] lowk = new double[dataset.attributeCount()];
            double[] uppk = new double[dataset.attributeCount()];

            lowk[0] = rectangle.x;
            uppk[0] = rectangle.x + rectangle.width;
            lowk[1] = Math.min(rectangle.y, rectangle.y - rectangle.height);
            uppk[1] = Math.max(rectangle.y, rectangle.y - rectangle.height);
            try {
                items = kdTree.range(lowk, uppk);
            } catch (KeySizeException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private Point2D translate(E inst) {
        return viewer.posOnCanvas(inst.get(0), inst.get(1));
    }

    private Rectangle2D.Double makeRectangle(int x1, int y1, int x2, int y2) {
        return new Rectangle2D.Double(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x1 - x2), Math.abs(y1 - y2));
    }

    @Override
    public void datasetChanged(Dataset<E> dataset) {
        //eventuall switch to 3D
        if (dataset.attributeCount() == 3) {
            mode2d = false;
            if (viewer3d == null) {
                viewer3d = new Scatter3DPlot();
            }
            setViewer(viewer3d);
        } else {
            if (!mode2d) {
                setViewer(viewer);
            }
            mode2d = true;
        }
        items = null;
        selection = null;
        //build kd-tree for fast search
        kdTree = new KDTree<>(dataset.attributeCount());
        E inst;
        for (int i = 0; i < dataset.size(); i++) {
            try {
                inst = dataset.get(i);
                kdTree.insert(inst, inst);
            } catch (KeySizeException | KeyDuplicateException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        System.out.println("kdtree size: " + kdTree.size());
    }

    private void setViewer(JPanel view) {
        this.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        GridBagLayout gbl = (GridBagLayout) this.getLayout();

        ScattMouseListener ml = new ScattMouseListener();
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHEAST;
        c.weightx = c.weighty = 1.0; //ratio for filling the frame space
        gbl.setConstraints((Component) viewer, c);
        this.add((Component) view, c);
    }

    public ScatterPlot getViewer() {
        return viewer;
    }

    @Override
    public void colorGeneratorChanged(ColorGenerator cg) {
        cg.reset();
        for (Cluster<E> c : clust) {
            c.setColor(cg.next());
        }
        fireClusteringChanged(clust);
    }

    @Override
    public void assignLabelToSelection(String label) {
        int i = 0;
        if (clust != null) {
            C target = clust.get(label);
            if (target == null) {
                target = clust.createCluster();
                clust.setClusterName(target.getClusterId(), label);
                System.out.println("created cluster: " + target.getName() + ", id: " + target.getClusterId());
            }
            if (items != null) {
                C curr;
                for (E item : items) {
                    curr = clust.assignedCluster(item);
                    if (curr.getClusterId() != target.getClusterId()) {
                        if (!curr.remove(item)) {
                            throw new RuntimeException("failed to remove item: " + item);
                        }
                        target.add(item);
                        i++;
                    }
                }
            } else {
                System.out.println("no items selected!");
            }
        } else {
            throw new RuntimeException("missing clustering");
        }
        if (i > 0) {
            fireClusteringChanged(clust);
            repaint();
        }
        System.out.println("updated " + i + " assignments");
    }

    private class ScattMouseListener extends MouseAdapter implements MouseListener, MouseMotionListener {

        @Override
        public void mouseClicked(MouseEvent e) {
            double[] pos = viewer.translate(e.getPoint());
            E inst = dataset.builder().create(pos);
            inst.setClassValue(Algorithm.OUTLIER_LABEL);
            try {
                kdTree.insert(inst, inst);
            } catch (KeySizeException | KeyDuplicateException ex) {
                Exceptions.printStackTrace(ex);
            }
            System.out.println("data size: " + dataset.size());
            //System.out.println("[" + pos[0] + ", " + pos[1] + "]");
        }

        @Override
        public void mousePressed(MouseEvent e) {
            startDrag = new Point(e.getX(), e.getY());
            mousePress = e.getPoint();
            repaint();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            drawing = true;
            int x = Math.min(mousePress.x, e.getPoint().x);
            int y = Math.min(mousePress.y, e.getPoint().y);
            int width = Math.abs(mousePress.x - e.getPoint().x);
            int height = Math.abs(mousePress.y - e.getPoint().y);

            rect = new Rectangle(x, y, width, height);
            repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            selection = makeRectangle(startDrag.x, startDrag.y, e.getX(), e.getY());
            startDrag = null;
            drawing = false;
            repaint();
            findPoints(selection);
        }

        @Override
        public void mouseEntered(MouseEvent e) {

        }

        @Override
        public void mouseExited(MouseEvent e) {

        }
    }

}
