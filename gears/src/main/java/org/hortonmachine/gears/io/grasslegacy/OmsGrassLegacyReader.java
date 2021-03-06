/*
 * This file is part of HortonMachine (http://www.hortonmachine.org)
 * (C) HydroloGIS - www.hydrologis.com 
 * 
 * The HortonMachine is free software: you can redistribute it and/or modify
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
package org.hortonmachine.gears.io.grasslegacy;

import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_AUTHORCONTACTS;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_AUTHORNAMES;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_DESCRIPTION;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_DOACTIVE_DESCRIPTION;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_FILE_DESCRIPTION;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_GEO_DATA_DESCRIPTION;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_IN_WINDOW_DESCRIPTION;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_KEYWORDS;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_LABEL;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_LICENSE;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_NAME;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_OUT_GC_DESCRIPTION;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_STATUS;
import static org.hortonmachine.gears.i18n.GearsMessages.OMSGRASSLEGACYREADER_UI;

import java.io.File;

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
import oms3.annotations.UI;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.grassraster.JGrassMapEnvironment;
import org.geotools.gce.grassraster.JGrassRegion;
import org.hortonmachine.gears.io.grasslegacy.io.GrassRasterReader;
import org.hortonmachine.gears.io.grasslegacy.io.MapReader;
import org.hortonmachine.gears.io.grasslegacy.utils.GrassLegacyUtilities;
import org.hortonmachine.gears.io.grasslegacy.utils.Window;
import org.hortonmachine.gears.libs.modules.HMConstants;
import org.hortonmachine.gears.libs.modules.HMModel;
import org.hortonmachine.gears.libs.monitor.IHMProgressMonitor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Coordinate;

@Description(OMSGRASSLEGACYREADER_DESCRIPTION)
@Author(name = OMSGRASSLEGACYREADER_AUTHORNAMES, contact = OMSGRASSLEGACYREADER_AUTHORCONTACTS)
@Keywords(OMSGRASSLEGACYREADER_KEYWORDS)
@Label(OMSGRASSLEGACYREADER_LABEL)
@Name(OMSGRASSLEGACYREADER_NAME)
@Status(OMSGRASSLEGACYREADER_STATUS)
@License(OMSGRASSLEGACYREADER_LICENSE)
@UI(OMSGRASSLEGACYREADER_UI)
public class OmsGrassLegacyReader extends HMModel {

    @Description(OMSGRASSLEGACYREADER_FILE_DESCRIPTION)
    @UI(HMConstants.FILEIN_UI_HINT_RASTER)
    @In
    public String file = null;

    @Description(OMSGRASSLEGACYREADER_DOACTIVE_DESCRIPTION)
    @In
    public boolean doActive = true;

    @Description(OMSGRASSLEGACYREADER_IN_WINDOW_DESCRIPTION)
    @In
    public Window inWindow = null;

    @Description(OMSGRASSLEGACYREADER_OUT_GC_DESCRIPTION)
    @Out
    public GridCoverage2D outGC = null;

    @Description(OMSGRASSLEGACYREADER_GEO_DATA_DESCRIPTION)
    @Out
    public double[][] geodata = null;

    @Execute
    public void readCoverage() throws Exception {
        if (!concatOr(geodata == null, doReset)) {
            return;
        }
        JGrassMapEnvironment mapEnvironment = new JGrassMapEnvironment(new File(file));
        JGrassRegion jGrassRegion = null;
        if (inWindow == null) {
            if (doActive) {
                jGrassRegion = mapEnvironment.getActiveRegion();
            } else {
                jGrassRegion = mapEnvironment.getFileRegion();
            }
            inWindow = new Window(jGrassRegion.getWest(), jGrassRegion.getEast(), jGrassRegion.getSouth(),
                    jGrassRegion.getNorth(), jGrassRegion.getWEResolution(), jGrassRegion.getNSResolution());
        }

        GrassRasterReader reader = new GrassRasterReader();
        try {
            reader.setReaderType(MapReader.RASTER_READER);
            reader.setOutputDataObject(new double[0][0]);
            reader.setDataWindow(inWindow);

            reader.open(mapEnvironment.getCELL().getAbsolutePath());
            if (reader.hasMoreData(pm)) {
                geodata = (double[][]) reader.getNextData();
            }
        } finally {
            reader.close();
        }

        CoordinateReferenceSystem crs = mapEnvironment.getCoordinateReferenceSystem();
        outGC = new GrassLegacyGridCoverage2D(inWindow, geodata, crs);
    }

    /**
     * Get a single value in a position of the raster.
     * 
     * @param coordinate the coordinate in which the value is read.
     * @return the value read in the given coordinate.
     */
    public double getValueAt( Coordinate coordinate ) {
        if (geodata == null) {
            throw new IllegalArgumentException("The data have first to be read!");
        }
        int[] coordinateToNearestRowCol = GrassLegacyUtilities.coordinateToNearestRowCol(inWindow, coordinate);
        if (coordinateToNearestRowCol != null) {
            int row = coordinateToNearestRowCol[0];
            int col = coordinateToNearestRowCol[1];
            if (row > 0 && row < geodata.length) {
                if (col > 0 && col < geodata[0].length) {
                    return geodata[row][col];
                }
            }
        }
        return HMConstants.doubleNovalue;
    }

    /**
     * Get a single value in a position of the raster.
     * 
     * <p>This opens and closes the raster every time it is called. Bad performance on many calls.
     * 
     * @param window the grid on which to base on (if <code>null</code>, the active region is picked).
     * @param coordinate the coordinate in which the value is read.
     * @param filePath the path to the map.
     * @param pm the progress monitor or null.
     * @return the value read in the given coordinate.
     * @throws Exception
     */
    public static double getValueAt( Window window, Coordinate coordinate, String filePath, IHMProgressMonitor pm )
            throws Exception {
        JGrassMapEnvironment mapEnvironment = new JGrassMapEnvironment(new File(filePath));
        if (window == null) {
            JGrassRegion jgr = mapEnvironment.getActiveRegion();
            window = new Window(jgr.getWest(), jgr.getEast(), jgr.getSouth(), jgr.getNorth(), jgr.getWEResolution(),
                    jgr.getNSResolution());
        }
        Window rectangleAroundPoint = GrassLegacyUtilities.getRectangleAroundPoint(window, coordinate.x, coordinate.y);

        OmsGrassLegacyReader reader = new OmsGrassLegacyReader();
        reader.file = filePath;
        reader.inWindow = rectangleAroundPoint;
        if (pm != null)
            reader.pm = pm;
        reader.readCoverage();
        double[][] data = reader.geodata;

        if (data.length != 1 || data[0].length != 1) {
            throw new IllegalAccessException("Wrong region extracted for picking a single point.");
        }

        return data[0][0];
    }

    // public static void main( String[] args ) throws Exception {
    // // 660205.062241|5116931.07884||932.92|
    // PrintStreamProgressMonitor pm = new PrintStreamProgressMonitor(System.out, System.err);
    // double value = getValueAt(null, new Coordinate(660205.062241, 5116931.07884),
    // "/home/moovida/DTM_TRENTINO/grassdb/trentino/solo/cell/dtm_all_wgs", pm);
    // System.out.println(value);
    //
    // }

}
