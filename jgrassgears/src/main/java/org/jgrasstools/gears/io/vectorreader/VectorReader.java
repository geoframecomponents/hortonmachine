/*
 * JGrass - Free Open Source Java GIS http://www.jgrass.org 
 * (C) HydroloGIS - www.hydrologis.com 
 * 
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Library General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the Free Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jgrasstools.gears.io.vectorreader;

import java.io.File;
import java.io.IOException;

import oms3.annotations.Author;
import oms3.annotations.Description;
import oms3.annotations.Execute;
import oms3.annotations.In;
import oms3.annotations.Keywords;
import oms3.annotations.Label;
import oms3.annotations.License;
import oms3.annotations.Out;
import oms3.annotations.Status;
import oms3.annotations.UI;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.jgrasstools.gears.io.shapefile.ShapefileFeatureReader;
import org.jgrasstools.gears.libs.modules.JGTConstants;
import org.jgrasstools.gears.libs.modules.JGTModel;
import org.jgrasstools.gears.libs.monitor.IJGTProgressMonitor;
import org.jgrasstools.gears.libs.monitor.LogProgressMonitor;

@Description("Utility class for reading vector files to geotools featurecollections.")
@Author(name = "Andrea Antonello", contact = "www.hydrologis.com")
@Keywords("IO, Shapefile, Feature, Vector, Reading")
@Label(JGTConstants.FEATUREREADER)
@Status(Status.CERTIFIED)
@License("http://www.gnu.org/licenses/gpl-3.0.html")
public class VectorReader extends JGTModel {
    @Description("The shapefile to read.")
    @UI(JGTConstants.FILEIN_UI_HINT)
    @In
    public String file = null;
    
    @Description("The vector type to read (Supported is: shp).")
    @In
    // currently not used, for future compatibility
    public String pType = null;

    @Description("The progress monitor.")
    @In
    public IJGTProgressMonitor pm = new LogProgressMonitor();

    @Description("The read feature collection.")
    @Out
    public SimpleFeatureCollection geodata = null;

    @Execute
    public void process() throws IOException {
        if (!concatOr(geodata == null, doReset)) {
            return;
        }

        checkNull(file);

        File vectorFile = new File(file);
        String name = vectorFile.getName();
        if (name.toLowerCase().endsWith("shp")) {
            geodata = ShapefileFeatureReader.readShapefile(vectorFile.getAbsolutePath());
        } else {
            throw new IOException("Format is currently not supported for file: " + name);
        }
    }

    /**
     * Fast read access mode. 
     * 
     * @param path the vector file path.
     * @return the read {@link FeatureCollection}.
     * @throws IOException
     */
    public static SimpleFeatureCollection readVector( String path ) throws IOException {

        VectorReader reader = new VectorReader();
        reader.file = path;
        reader.process();

        return reader.geodata;
    }

}
