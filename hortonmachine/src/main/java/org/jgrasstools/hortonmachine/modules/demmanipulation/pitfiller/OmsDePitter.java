/*
 * This file is part of JGrasstools (http://www.jgrasstools.org)
 * (C) HydroloGIS - www.hydrologis.com 
 * 
 * JGrasstools is free software: you can redistribute it and/or modify
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
package org.jgrasstools.hortonmachine.modules.demmanipulation.pitfiller;

import static org.jgrasstools.gears.libs.modules.JGTConstants.DEMMANIPULATION;

import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.media.jai.iterator.WritableRandomIter;

import org.geotools.coverage.grid.GridCoverage2D;
import org.jgrasstools.gears.io.rasterreader.OmsRasterReader;
import org.jgrasstools.gears.io.rasterwriter.OmsRasterWriter;
import org.jgrasstools.gears.libs.modules.FlowNode;
import org.jgrasstools.gears.libs.modules.GridNode;
import org.jgrasstools.gears.libs.modules.JGTConstants;
import org.jgrasstools.gears.libs.modules.JGTModel;
import org.jgrasstools.gears.libs.monitor.IJGTProgressMonitor;
import org.jgrasstools.gears.utils.BitMatrix;
import org.jgrasstools.gears.utils.RegionMap;
import org.jgrasstools.gears.utils.coverage.CoverageUtilities;
import org.jgrasstools.hortonmachine.i18n.HortonMessageHandler;
import org.jgrasstools.hortonmachine.modules.geomorphology.draindir.OmsDrainDir;

import oms3.annotations.Author;
import oms3.annotations.Description;
import oms3.annotations.Execute;
import oms3.annotations.In;
import oms3.annotations.Keywords;
import oms3.annotations.Label;
import oms3.annotations.License;
import oms3.annotations.Name;
import oms3.annotations.Out;
import oms3.annotations.Status;

@Description(OmsDePitter.OMSDEPITTER_DESCRIPTION)
@Author(name = OmsDePitter.OMSDEPITTER_AUTHORNAMES, contact = OmsDePitter.OMSDEPITTER_AUTHORCONTACTS)
@Keywords(OmsDePitter.OMSDEPITTER_KEYWORDS)
@Label(OmsDePitter.OMSDEPITTER_LABEL)
@Name(OmsDePitter.OMSDEPITTER_NAME)
@Status(OmsDePitter.OMSDEPITTER_STATUS)
@License(OmsDePitter.OMSDEPITTER_LICENSE)
public class OmsDePitter extends JGTModel {
    @Description(OMSDEPITTER_inElev_DESCRIPTION)
    @In
    public GridCoverage2D inElev;

    @Description(OMSDEPITTER_outPit_DESCRIPTION)
    @Out
    public GridCoverage2D outPit = null;

    @Description(OMSDEPITTER_outFlow_DESCRIPTION)
    @Out
    public GridCoverage2D outFlow = null;

    public static final String OMSDEPITTER_DESCRIPTION = "The module fills the depression points present within a DEM and generates a map of flowdirections that also handles flat areas.";
    public static final String OMSDEPITTER_DOCUMENTATION = "";
    public static final String OMSDEPITTER_KEYWORDS = "Dem manipulation, Geomorphology";
    public static final String OMSDEPITTER_LABEL = DEMMANIPULATION;
    public static final String OMSDEPITTER_NAME = "depit";
    public static final int OMSDEPITTER_STATUS = Status.EXPERIMENTAL;
    public static final String OMSDEPITTER_LICENSE = "General Public License Version 3 (GPLv3)";
    public static final String OMSDEPITTER_AUTHORNAMES = "Andrea Antonello, Silvia Franceschi";
    public static final String OMSDEPITTER_AUTHORCONTACTS = "http://www.hydrologis.com";
    public static final String OMSDEPITTER_inElev_DESCRIPTION = "The map of digital elevation model (DEM).";
    public static final String OMSDEPITTER_outPit_DESCRIPTION = "The depitted elevation map.";
    public static final String OMSDEPITTER_outFlow_DESCRIPTION = "The map of D8 flowdirections.";

    private HortonMessageHandler msg = HortonMessageHandler.getInstance();

    private final double delta = 2E-6;

    @Execute
    public void process() throws Exception {
        checkNull(inElev);
        RegionMap regionMap = CoverageUtilities.getRegionParamsFromGridCoverage(inElev);
        int nCols = regionMap.getCols();
        int nRows = regionMap.getRows();
        double xRes = regionMap.getXres();
        double yRes = regionMap.getYres();

        // output raster
        WritableRaster pitRaster = CoverageUtilities.renderedImage2WritableRaster(inElev.getRenderedImage(), false);
        WritableRandomIter pitIter = CoverageUtilities.getWritableRandomIterator(pitRaster);
        try {

            ConcurrentLinkedQueue<GridNode> pitsList = getPitsList(nCols, nRows, xRes, yRes, pitIter);
            int iteration = 1;
            while( pitsList.size() > 0 ) {
                if (pm.isCanceled()) {
                    return;
                }
                pm.message("Iteration number: " + iteration++);

                int size = pitsList.size();
                pm.message(msg.message("pitfiller.numpit") + size);

                List<GridNode> allNodesInPit = new ArrayList<>();
                List<PitInfo> pitInfoList = new ArrayList<>();
                pm.beginTask("Processing pits...", size);
                int count = 0;
                for( GridNode originalPitNode : pitsList ) {
                    if (allNodesInPit.contains(originalPitNode)) {
                        pm.worked(1);
                        continue;
                    }
                    count++;
                    if (pm.isCanceled()) {
                        return;
                    }

                    List<GridNode> nodesInPit = new ArrayList<>();
                    nodesInPit.add(originalPitNode);

                    double maxValue = Double.NEGATIVE_INFINITY;
                    GridNode maxValueNode = null;
                    int workingIndex = 0;
                    while( workingIndex < nodesInPit.size() ) {
                        if (pm.isCanceled()) {
                            return;
                        }
                        GridNode currentPitNode = nodesInPit.get(workingIndex);

                        List<GridNode> surroundingNodes = new ArrayList<>(currentPitNode.getValidSurroundingNodes());
                        surroundingNodes.removeAll(nodesInPit);
                        GridNode minNode = getMinElevNode(surroundingNodes);
                        if (minNode == null) {
                            workingIndex++;
                            continue;
                        }
                        List<GridNode> minElevSurroundingNodes = new ArrayList<>(minNode.getValidSurroundingNodes());
                        minElevSurroundingNodes.removeAll(nodesInPit);
                        if (!minNode.isPitFor(minElevSurroundingNodes)) {
                            break;
                        }

                        for( GridNode tmpNode : surroundingNodes ) {
                            if (tmpNode.touchesBound()) {
                                continue;
                            }
                            List<GridNode> subSurroundingNodes = new ArrayList<>(tmpNode.getValidSurroundingNodes());
                            subSurroundingNodes.removeAll(nodesInPit);

                            if (tmpNode.isPitFor(subSurroundingNodes)) {
                                nodesInPit.add(tmpNode);

                                GridNode subMinNode = getMinElevNode(subSurroundingNodes);
                                if (subMinNode != null && subMinNode.elevation > maxValue) {
                                    maxValue = subMinNode.elevation;
                                    maxValueNode = subMinNode;
                                }
                            }
                        }
                        workingIndex++;
                    }

                    if (nodesInPit.size() == 1) {
                        GridNode gridNode = nodesInPit.get(0);
                        List<GridNode> validSurroundingNodes = gridNode.getValidSurroundingNodes();
                        GridNode minNode = getMinElevNode(validSurroundingNodes);
                        if (minNode != null && minNode.elevation > maxValue) {
                            maxValue = minNode.elevation;
                            maxValueNode = minNode;
                        } else {
                            throw new RuntimeException();
                        }
                    }

                    if (Double.isInfinite(maxValue) || Double.isNaN(maxValue)) {
                        throw new RuntimeException("Found invalid value at: " + count);
                    }

                    PitInfo info = new PitInfo();
                    // info.originalPitNode = originalPitNode;
                    info.pitfillExitNode = maxValueNode;
                    info.nodes = nodesInPit;
                    pitInfoList.add(info);
                    allNodesInPit.addAll(nodesInPit);

                    pm.worked(1);
                }
                pm.done();

                BitMatrix markedPositions = new BitMatrix(nCols, nRows);
                pm.beginTask("Handle flats...", pitInfoList.size());
                pitInfoList.parallelStream().forEach(pitInfo -> {
                    if (pm.isCanceled()) {
                        return;
                    }
                    GridNode pitfillExitNode = pitInfo.pitfillExitNode;
                    double exitElevation = pitfillExitNode.elevation;
                    List<GridNode> allPitsOfCurrent = pitInfo.nodes;
                    for( GridNode gridNode : allPitsOfCurrent ) {
                        gridNode.setValueInMap(pitIter, exitElevation);
                        markedPositions.mark(gridNode.col, gridNode.row);
                    }

                    List<GridNode> cellsToMakeFlowReady = new ArrayList<>();
                    GridNode startNode = new GridNode(pitIter, nCols, nRows, xRes, yRes, pitfillExitNode.col,
                            pitfillExitNode.row);
                    cellsToMakeFlowReady.add(startNode);
                    makeCellsFlowReady(0, startNode, cellsToMakeFlowReady, markedPositions, pitIter, delta);
                    pm.worked(1);
                });
                pm.done();

                // only re-check the cells that are adiacent to what has been modified
                pitsList = getPitsList(allNodesInPit);

                pm.message("Left pits: " + pitsList.size());
                pm.message("---------------------------------------------------------------------");

            }

            outPit = CoverageUtilities.buildCoverage("pitfiller", pitRaster, regionMap, inElev.getCoordinateReferenceSystem());

            WritableRaster flowRaster = CoverageUtilities.createWritableRaster(nCols, nRows, Integer.class, null, null);
            WritableRandomIter flowIter = CoverageUtilities.getWritableRandomIterator(flowRaster);
            try {
                pm.beginTask("Calculating flowdirections...", nRows);
                for( int r = 0; r < nRows; r++ ) {
                    if (pm.isCanceled()) {
                        return;
                    }
                    for( int c = 0; c < nCols; c++ ) {
                        GridNode node = new GridNode(pitIter, nCols, nRows, xRes, yRes, c, r);
                        if (node.isOutlet()) {
                            flowIter.setSample(c, r, 0, FlowNode.OUTLET);
                        } else if (node.isValid() && !node.touchesBound() && !node.touchesNovalue()) {
                            int flow = node.getFlow();
                            flowIter.setSample(c, r, 0, flow);
                        } else {
                            flowIter.setSample(c, r, 0, JGTConstants.intNovalue);
                        }
                    }
                    pm.worked(1);
                }
                pm.done();

                outFlow = CoverageUtilities.buildCoverage("flow", flowRaster, regionMap, inElev.getCoordinateReferenceSystem());
            } finally {
                flowIter.done();
            }

        } finally {
            pitIter.done();
        }
    }

    private GridNode getMinElevNode( List<GridNode> surroundingNodes ) {
        double minElev = Double.POSITIVE_INFINITY;
        GridNode minNode = null;
        for( GridNode gridNode : surroundingNodes ) {
            if (gridNode.elevation < minElev) {
                minElev = gridNode.elevation;
                minNode = gridNode;
            }
        }
        return minNode;
    }

    /**
     * Make cells flow ready by creating a slope starting from the output cell.
     * 
     * @param iteration the iteration.
     * @param pitfillExitNode the exit node.
     * @param cellsToMakeFlowReady the cells to check and change at each iteration.
     * @param markedPositions the marked positions of all existing pits. Necessary to pick only those that 
     *              really are part of the pit pool.
     * @param pitIter elevation data.
     * @param delta the elevation delta to add to the cells to create the slope.
     */
    private void makeCellsFlowReady( int iteration, GridNode pitfillExitNode, List<GridNode> cellsToMakeFlowReady,
            BitMatrix markedPositions, WritableRandomIter pitIter, double delta ) {
        iteration++;

        double exitElevation = pitfillExitNode.elevation;
        List<GridNode> connected = new ArrayList<>();
        for( GridNode checkNode : cellsToMakeFlowReady ) {
            List<GridNode> validSurroundingNodes = checkNode.getValidSurroundingNodes();
            for( GridNode gridNode : validSurroundingNodes ) {
                if (!pitfillExitNode.equals(gridNode) && markedPositions.isMarked(gridNode.col, gridNode.row)
                        && gridNode.elevation == exitElevation) {
                    if (!connected.contains(gridNode))
                        connected.add(gridNode);
                }
            }
        }
        if (connected.size() == 0) {
            return;
        }
        for( GridNode gridNode : connected ) {
            gridNode.setValueInMap(pitIter, gridNode.elevation + delta * iteration);
        }
        makeCellsFlowReady(iteration, pitfillExitNode, connected, markedPositions, pitIter, delta);
    }

    private ConcurrentLinkedQueue<GridNode> getPitsList( int nCols, int nRows, double xRes, double yRes,
            WritableRandomIter pitIter ) {
        ConcurrentLinkedQueue<GridNode> pitsList = new ConcurrentLinkedQueue<>();
        pm.beginTask("Extract pits from DTM...", nRows);
        for( int row = 0; row < nRows; row++ ) {
            for( int col = 0; col < nCols; col++ ) {
                GridNode node = new GridNode(pitIter, nCols, nRows, xRes, yRes, col, row);
                if (node.isPit()) {
                    double surroundingMin = node.getSurroundingMin();
                    if (Double.isInfinite(surroundingMin)) {
                        continue;
                    }
                    pitsList.add(node);
                }
            }
            pm.worked(1);
        }
        pm.done();
        return pitsList;
    }

    private ConcurrentLinkedQueue<GridNode> getPitsList( List<GridNode> nodesToCheckForLeftPits ) {
        ConcurrentLinkedQueue<GridNode> pitsList = new ConcurrentLinkedQueue<>();
        if (nodesToCheckForLeftPits.size() > 0) {
            GridNode tmp = nodesToCheckForLeftPits.get(0);
            BitMatrix existing = new BitMatrix(tmp.cols, tmp.rows);
            pm.beginTask("Extract pits from the cells surrounding the pit pool...", nodesToCheckForLeftPits.size());
            nodesToCheckForLeftPits.stream().forEach(node -> {
                List<GridNode> validSurroundingNodes = node.getValidSurroundingNodes();
                for( GridNode gridNode : validSurroundingNodes ) {
                    if (gridNode.isPit()) {
                        double surroundingMin = gridNode.getSurroundingMin();
                        if (Double.isInfinite(surroundingMin)) {
                            continue;
                        }
                        if (!existing.isMarked(gridNode.col, gridNode.row)) {
                            pitsList.add(gridNode);
                            existing.mark(gridNode.col, gridNode.row);
                        }
                    }
                }
                pm.worked(1);
            });
            pm.done();
        }
        return pitsList;
    }

    private static class PitInfo {
        // GridNode originalPitNode;
        GridNode pitfillExitNode;
        List<GridNode> nodes;
    }

    public static void main( String[] args ) throws Exception {
        String dtm = "/media/hydrologis/Samsung_T3/MAZONE/DTM/dtm_toblino/dtm_toblino.tiff";
        String pit = "/media/hydrologis/Samsung_T3/MAZONE/DTM/dtm_toblino/pit.tiff";
        String flow = "/media/hydrologis/Samsung_T3/MAZONE/DTM/dtm_toblino/flow.tiff";
        String drain = "/media/hydrologis/Samsung_T3/MAZONE/DTM/dtm_toblino/drain.tiff";
        String tca = "/media/hydrologis/Samsung_T3/MAZONE/DTM/dtm_toblino/tca.tiff";

        // String dtm =
        // "/media/hydrologis/Samsung_T3/MAZONE/PITFILLE/DTM_calvello/dtm_all_float.tiff";
        // String pit = "/media/hydrologis/Samsung_T3/MAZONE/PITFILLE/DTM_calvello/pit.tiff";
        // String flow = "/media/hydrologis/Samsung_T3/MAZONE/PITFILLE/DTM_calvello/flow.tiff";

//        OmsDePitter pitfiller = new OmsDePitter();
//        pitfiller.inElev = OmsRasterReader.readRaster(dtm);
//        pitfiller.process();
//        OmsRasterWriter.writeRaster(pit, pitfiller.outPit);
//        OmsRasterWriter.writeRaster(flow, pitfiller.outFlow);

        OmsDrainDir draindir = new OmsDrainDir();
        draindir.inPit = OmsRasterReader.readRaster(pit);
        draindir.inFlow = OmsRasterReader.readRaster(flow);
        // draindir.inFlownet = OmsRasterReader.readRaster(inFlownet);
        draindir.pLambda = 1f;
        draindir.process();
        OmsRasterWriter.writeRaster(drain, draindir.outFlow);
        OmsRasterWriter.writeRaster(tca, draindir.outTca);
    }

}
