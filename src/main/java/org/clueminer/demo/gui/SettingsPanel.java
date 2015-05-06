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

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.clueminer.clustering.api.Cluster;
import org.clueminer.clustering.api.Clustering;
import org.clueminer.clustering.api.ClusteringAlgorithm;
import org.clueminer.clustering.api.ClusteringFactory;
import org.clueminer.clustering.api.ClusteringListener;
import org.clueminer.clustering.api.ExternalEvaluator;
import org.clueminer.clustering.api.HierarchicalResult;
import org.clueminer.clustering.api.factory.ExternalEvaluatorFactory;
import org.clueminer.clustering.gui.ClusteringDialog;
import org.clueminer.clustering.gui.ClusteringDialogFactory;
import org.clueminer.utils.Props;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

/**
 *
 * @author deric
 */
public class SettingsPanel extends JPanel implements ClusteringListener {

    private static final long serialVersionUID = 4694033662557233989L;

    private JButton btnOptions;
    private JComboBox dataBox;
    private ClusteringFactory cf;
    private JComboBox algBox;
    private JComboBox validationBox;
    private final ScatterWrapper panel;
    private ClusteringDialog optPanel;
    private JLabel lbValidation;
    private ExternalEvaluator evaluator;
    private static final DecimalFormat decimalFormat = new DecimalFormat("#0.00");
    private Clustering<? extends Cluster> clustering;

    public SettingsPanel(ScatterWrapper panel) {
        this.panel = panel;
        panel.addClusteringListener(this);
        initComponents();
        setDatasets(panel.getDatasets());
    }

    private void initComponents() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));

        dataBox = new JComboBox();
        dataBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                panel.dataChanged((String) dataBox.getSelectedItem());
            }
        });
        add(dataBox);

        cf = ClusteringFactory.getInstance();
        algBox = new JComboBox(cf.getProvidersArray());

        algBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String alg = (String) algBox.getSelectedItem();
                //if algorithm was really changed, trigger execution
                if (!alg.equals(panel.getAlgorithm().getName())) {
                    panel.setAlgorithm(cf.getProvider(alg));
                    panel.execute();
                }
            }
        });
        add(algBox);

        btnOptions = new JButton("Settings");
        btnOptions.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                DialogDescriptor dd = new DialogDescriptor(getUI(getAlgorithm()), "Settings");
                if (DialogDisplayer.getDefault().notify(dd).equals(NotifyDescriptor.OK_OPTION)) {
                    updateAlgorithm();

                    panel.execute(getProps());
                }
            }
        });
        add(btnOptions);

        validationBox = new JComboBox(ExternalEvaluatorFactory.getInstance().getProvidersArray());
        validationBox.setSelectedItem("NMI");
        updateEvaluator("NMI");
        validationBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                String validator = (String) validationBox.getSelectedItem();
                if (!validator.equals(evaluator.getName())) {
                    updateEvaluator(validator);
                    clusteringChanged(clustering);
                }
            }
        });

        add(new JLabel("Validation:"));

        lbValidation = new JLabel("");
        add(lbValidation);
        add(validationBox);
    }

    private void updateEvaluator(String validator) {
        evaluator = ExternalEvaluatorFactory.getInstance().getProvider(validator);
    }

    private JPanel getUI(ClusteringAlgorithm alg) {
        for (ClusteringDialog dlg : ClusteringDialogFactory.getInstance().getAll()) {
            if (dlg.isUIfor(alg)) {
                optPanel = dlg;
                return dlg.getPanel();
            }
        }
        //last resort
        return new JPanel();
    }

    public void updateAlgorithm() {
        ClusteringAlgorithm algorithm = getAlgorithm();
        optPanel.updateAlgorithm(algorithm);
    }

    public final void setDatasets(String[] datasets) {
        for (String str : datasets) {
            dataBox.addItem(str);
        }
    }

    public ClusteringAlgorithm getAlgorithm() {
        String algName = (String) algBox.getSelectedItem();
        ClusteringAlgorithm algorithm = ClusteringFactory.getInstance().getProvider(algName);
        return algorithm;
    }

    public void selectAlgorithm(String algorithm) {
        algBox.setSelectedItem(algorithm);
    }

    public Props getProps() {
        if (optPanel != null) {
            return optPanel.getParams();
        } else {
            throw new RuntimeException("missing dialog");
        }
    }

    @Override
    public void clusteringChanged(Clustering clust) {
        if (clust != null && evaluator != null) {
            clustering = clust;
            double score = evaluator.score(clust);
            lbValidation.setText(decimalFormat.format(score));
        }
    }

    @Override
    public void resultUpdate(HierarchicalResult hclust) {
        //not much to do
    }

}
