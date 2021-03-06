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
package org.hortonmachine.gears.spatialite;

import java.io.File;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.store.ReprojectingFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.h2gis.utilities.SFSUtilities;
import org.h2gis.utilities.TableLocation;
import org.hortonmachine.dbs.compat.ASpatialDb;
import org.hortonmachine.dbs.compat.GeometryColumn;
import org.hortonmachine.dbs.compat.IHMConnection;
import org.hortonmachine.dbs.compat.IHMPreparedStatement;
import org.hortonmachine.dbs.compat.IHMStatement;
import org.hortonmachine.dbs.compat.objects.QueryResult;
import org.hortonmachine.dbs.h2gis.H2GisDb;
import org.hortonmachine.dbs.spatialite.ESpatialiteGeometryType;
import org.hortonmachine.dbs.spatialite.hm.SpatialiteDb;
import org.hortonmachine.gears.libs.monitor.IHMProgressMonitor;
import org.hortonmachine.gears.utils.CrsUtilities;
import org.hortonmachine.gears.utils.files.FileUtilities;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

/**
 * Import utilities.
 * 
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class SpatialDbsImportUtils {
    private static final Logger logger = LoggerFactory.getLogger(SpatialDbsImportUtils.class);

    /**
     * Create a spatial table using a shapefile as schema.
     * 
     * @param db the database to use.
     * @param shapeFile the shapefile to use.
     * @return the name of the created table.
     * @throws Exception
     */
    public static String createTableFromShp( ASpatialDb db, File shapeFile ) throws Exception {
        FileDataStore store = FileDataStoreFinder.getDataStore(shapeFile);
        SimpleFeatureSource featureSource = store.getFeatureSource();
        SimpleFeatureType schema = featureSource.getSchema();
        GeometryDescriptor geometryDescriptor = schema.getGeometryDescriptor();

        String shpName = FileUtilities.getNameWithoutExtention(shapeFile);

        List<String> attrSql = new ArrayList<String>();
        List<AttributeDescriptor> attributeDescriptors = schema.getAttributeDescriptors();
        for( AttributeDescriptor attributeDescriptor : attributeDescriptors ) {
            String attrName = attributeDescriptor.getLocalName();
            if (attributeDescriptor instanceof GeometryDescriptor) {
                continue;
            } else if (attrName.equalsIgnoreCase(ASpatialDb.PK_UID)) {
                continue;
            }
            Class< ? > binding = attributeDescriptor.getType().getBinding();
            if (binding.isAssignableFrom(Double.class) || binding.isAssignableFrom(Float.class)) {
                attrSql.add(attrName + " REAL");
            } else if (binding.isAssignableFrom(Long.class) || binding.isAssignableFrom(Integer.class)) {
                attrSql.add(attrName + " INTEGER");
            } else if (binding.isAssignableFrom(String.class)) {
                attrSql.add(attrName + " TEXT");
            } else {
                attrSql.add(attrName + " TEXT");
            }
        }

        String typeString = null;
        org.opengis.feature.type.GeometryType type = geometryDescriptor.getType();
        Class< ? > binding = type.getBinding();
        if (binding.isAssignableFrom(MultiPolygon.class)) {
            typeString = "MULTIPOLYGON";
        } else if (binding.isAssignableFrom(Polygon.class)) {
            typeString = "POLYGON";
        } else if (binding.isAssignableFrom(MultiLineString.class)) {
            typeString = "MULTILINESTRING";
        } else if (binding.isAssignableFrom(LineString.class)) {
            typeString = "LINESTRING";
        } else if (binding.isAssignableFrom(MultiPoint.class)) {
            typeString = "MULTIPOINT";
        } else if (binding.isAssignableFrom(Point.class)) {
            typeString = "POINT";
        }
        if (typeString != null) {
            String codeFromCrs = CrsUtilities.getCodeFromCrs(schema.getCoordinateReferenceSystem());
            if (codeFromCrs == null || codeFromCrs.toLowerCase().contains("null")) {
                codeFromCrs = "4326"; // fallback on 4326
            }
            codeFromCrs = codeFromCrs.replaceFirst("EPSG:", "");

            if (db instanceof SpatialiteDb) {
                SpatialiteDb spatialiteDb = (SpatialiteDb) db;
                spatialiteDb.createTable(shpName, attrSql.toArray(new String[0]));
                spatialiteDb.addGeometryXYColumnAndIndex(shpName, null, typeString, codeFromCrs, false);
            } else if (db instanceof H2GisDb) {
                H2GisDb spatialiteDb = (H2GisDb) db;
                String typeStringExtra = typeString;
                // String typeStringExtra = "GEOMETRY(" + typeString + "," + codeFromCrs + ")";
                attrSql.add("the_geom " + typeStringExtra);
                String[] array = attrSql.toArray(new String[0]);
                spatialiteDb.createTable(shpName, array);
                spatialiteDb.addSrid(shpName, codeFromCrs);
                spatialiteDb.createSpatialIndex(shpName, null);
            }
        } else {
            db.createTable(shpName, attrSql.toArray(new String[0]));
        }

        return shpName;
    }

    /**
     * Import a shapefile into a table.
     * 
     * @param db the database to use.
     * @param shapeFile the shapefile to import.
     * @param tableName the name of the table to import to.
     * @param limit if > 0, a limit to the imported features is applied.
     * @param pm the progress monitor.
     * @return <code>false</code>, is an error occurred. 
     * @throws Exception
     */
    public static boolean importShapefile( ASpatialDb db, File shapeFile, String tableName, int limit, IHMProgressMonitor pm )
            throws Exception {
        boolean noErrors = true;
        FileDataStore store = FileDataStoreFinder.getDataStore(shapeFile);
        SimpleFeatureSource featureSource = store.getFeatureSource();
        SimpleFeatureType schema = featureSource.getSchema();
        List<AttributeDescriptor> attributeDescriptors = schema.getAttributeDescriptors();

        SimpleFeatureCollection features = featureSource.getFeatures();
        int featureCount = features.size();

        List<String[]> tableInfo = db.getTableColumns(tableName);
        List<String> tableColumns = new ArrayList<>();
        for( String[] item : tableInfo ) {
            tableColumns.add(item[0].toUpperCase());
        }
        GeometryColumn geometryColumns = db.getGeometryColumnsForTable(tableName);
        String gCol = geometryColumns.geometryColumnName;

        int epsg = geometryColumns.srid;
        CoordinateReferenceSystem crs = null;
        try {
            crs = CrsUtilities.getCrsFromEpsg("EPSG:" + epsg);
        } catch (Exception e1) {
            // ignore and try without
        }
        SimpleFeatureIterator featureIterator;
        if (crs != null) {
            ReprojectingFeatureCollection repFeatures = new ReprojectingFeatureCollection(features, crs);
            featureIterator = repFeatures.features();
        } else {
            featureIterator = features.features();
        }

        List<String> attrNames = new ArrayList<>();
        String valueNames = "";
        String qMarks = "";
        for( AttributeDescriptor attributeDescriptor : attributeDescriptors ) {
            String attrName = attributeDescriptor.getLocalName();
            if (attrName.equalsIgnoreCase(ASpatialDb.PK_UID)) {
                continue;
            }
            attrNames.add(attrName);
            if (attributeDescriptor instanceof GeometryDescriptor) {
                valueNames += "," + gCol;
                qMarks += ",ST_GeomFromText(?, " + epsg + ")";
            } else {
                if (!tableColumns.contains(attrName.toUpperCase())) {
                    pm.errorMessage(
                            "The imported shapefile doesn't seem to match the table's schema. Doesn't exist: " + attrName);
                    return false;
                }
                valueNames += "," + attrName;
                qMarks += ",?";
            }
        }
        valueNames = valueNames.substring(1);
        qMarks = qMarks.substring(1);
        String sql = "INSERT INTO " + tableName + " (" + valueNames + ") VALUES (" + qMarks + ")";

        IHMConnection conn = db.getConnection();
        try (IHMPreparedStatement pStmt = conn.prepareStatement(sql)) {
            int count = 0;
            pm.beginTask("Adding data to batch import...", featureCount);
            try {
                while( featureIterator.hasNext() ) {
                    SimpleFeature f = (SimpleFeature) featureIterator.next();
                    for( int i = 0; i < attrNames.size(); i++ ) {
                        Object object = f.getAttribute(attrNames.get(i));
                        if (object == null) {
                            continue;
                        }
                        int iPlus = i + 1;
                        if (object instanceof Double) {
                            pStmt.setDouble(iPlus, (Double) object);
                        } else if (object instanceof Float) {
                            pStmt.setFloat(iPlus, (Float) object);
                        } else if (object instanceof Integer) {
                            pStmt.setInt(iPlus, (Integer) object);
                        } else if (object instanceof String) {
                            pStmt.setString(iPlus, (String) object);
                        } else if (object instanceof Geometry) {
                            pStmt.setString(iPlus, ((Geometry) object).toText());
                        } else if (object instanceof Clob) {
                            String string = ((Clob) object).toString();
                            pStmt.setString(iPlus, string);
                        } else {
                            pStmt.setString(iPlus, object.toString());
                        }
                    }
                    pStmt.addBatch();

                    count++;
                    if (limit > 0 && count >= limit) {
                        break;
                    }
                    pm.worked(1);
                }
            } catch (Exception e) {
                logger.error("error", e);
            } finally {
                pm.done();
                featureIterator.close();
            }

            try {
                pm.beginTask("Execute batch import of " + featureCount + " features...", IHMProgressMonitor.UNKNOWN);
                pStmt.executeBatch();
            } catch (Exception e) {
                logger.error("error", e);
            } finally {
                pm.done();
            }

        }

        try (IHMStatement pStmt = conn.createStatement()) {
            try {
                pStmt.executeQuery("Select updateLayerStatistics();");
            } catch (Exception e) {
                // ignore
            }
        }
        return noErrors;
    }

    /**
     * get a table as featurecollection.
     * 
     * @param db the database.
     * @param tableName the table to use.
     * @param featureLimit limit in feature or -1.
     * @param forceSrid a srid to force to or -1.
     * @return the extracted featurecollection.
     * @throws SQLException
     * @throws Exception
     */
    public static DefaultFeatureCollection tableToFeatureFCollection( ASpatialDb db, String tableName, int featureLimit,
            int forceSrid ) throws SQLException, Exception {
        DefaultFeatureCollection fc = new DefaultFeatureCollection();

        GeometryColumn geometryColumn = db.getGeometryColumnsForTable(tableName);
        CoordinateReferenceSystem crs;
        if (forceSrid == -1) {
            forceSrid = geometryColumn.srid;
        }
        crs = CrsUtilities.getCrsFromEpsg("EPSG:" + geometryColumn.srid);
        QueryResult tableRecords = db.getTableRecordsMapIn(tableName, null, false, featureLimit, forceSrid);
        int geometryIndex = tableRecords.geometryIndex;
        if (geometryIndex == -1) {
            throw new IllegalArgumentException("Not a geometric layer.");
        }
        Geometry sampleGeom = (Geometry) tableRecords.data.get(0)[geometryIndex];

        List<String> names = tableRecords.names;
        List<String> types = tableRecords.types;

        SimpleFeatureTypeBuilder b = new SimpleFeatureTypeBuilder();
        b.setName(tableName);
        b.setCRS(crs);

        for( int i = 0; i < names.size(); i++ ) {
            if (i == geometryIndex) {
                Class< ? > geometryClass = sampleGeom.getClass();
                b.add(geometryColumn.geometryColumnName, geometryClass);
                continue;
            }
            Class< ? > fieldClass = null;
            String typeStr = types.get(i);
            switch( typeStr ) {
            case "DOUBLE":
            case "REAL":
                fieldClass = Double.class;
                break;
            case "FLOAT":
                fieldClass = Float.class;
                break;
            case "INTEGER":
                fieldClass = Integer.class;
                break;
            case "TEXT":
                fieldClass = String.class;
                break;
            default:
                fieldClass = String.class;
                break;
            }
            b.add(names.get(i), fieldClass);
        }
        SimpleFeatureType type = b.buildFeatureType();
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(type);

        int count = tableRecords.data.size();
        for( int i = 0; i < count; i++ ) {
            Object[] objects = tableRecords.data.get(i);

            builder.addAll(objects);
            SimpleFeature feature = builder.buildFeature(null);

            fc.add(feature);
        }
        return fc;
    }
}
