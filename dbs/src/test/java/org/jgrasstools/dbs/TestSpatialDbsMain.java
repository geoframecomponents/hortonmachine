package org.jgrasstools.dbs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.jgrasstools.dbs.compat.ASpatialDb;
import org.jgrasstools.dbs.compat.EDb;
import org.jgrasstools.dbs.compat.ISpatialTableNames;
import org.jgrasstools.dbs.compat.objects.ForeignKey;
import org.jgrasstools.dbs.compat.objects.QueryResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;
import static org.jgrasstools.dbs.TestUtilities.*;

/**
 * Main tests for spatial dbs
 */
public class TestSpatialDbsMain {

    /**
     * The db type to test (set to h2gis for online tests).
     */
    public static final EDb DB_TYPE = EDb.H2GIS;
    private static ASpatialDb db;

    private static int tablesCount = 0;

    @BeforeClass
    public static void createDb() throws Exception {
        String tempDir = System.getProperty("java.io.tmpdir");
        String dbPath = tempDir + File.separator + "jgt-dbs-testspatialdbsmain" + DB_TYPE.getExtensionOnCreation();
        String dbPathDelete = tempDir + File.separator + "jgt-dbs-testspatialdbsmain." + DB_TYPE.getExtension();
        File file = new File(dbPathDelete);
        file.delete();

        db = DB_TYPE.getSpatialDb();
        db.open(dbPath);
        db.initSpatialMetadata("'WGS84'");

        createGeomTables(db);

        tablesCount = 6;
        if (DB_TYPE == EDb.SPATIALITE) {
            tablesCount = 7;

            String gCollWKT = "GEOMETRYCOLLECTION (" //
                    + " POLYGON ((10 42, 11.9 42, 11.9 40, 10 40, 10 42)), "
                    + " POLYGON ((11.1 43.2, 11.3 41.3, 13.9 41, 13.8 43.2, 11.1 43.2)), "
                    + " LINESTRING (11.3 44.3, 8.3 41.4, 11.4 38.1, 14.9 41.3), " //
                    + " POINT (12.7 44.2), " //
                    + " POINT (15.1 43.3), " //
                    + " POINT (15 40.4), " //
                    + " POINT (13.2 38.4), "
                    + " MULTIPOLYGON (((6.9 45.9, 8.4 45.9, 8.4 44.3, 6.9 44.3, 6.9 45.9)), ((9.1 46.3, 10.8 46.3, 10.8 44.6, 9.1 44.6, 9.1 46.3))), "
                    + " MULTILINESTRING ((7.4 42.6, 7.4 39, 8.6 38.5), (8 40.3, 9.5 38.6, 8.4 37.5)), "
                    + " MULTIPOINT ((6.8 42.5), (6.8 41.4), (6.6 40.2)))";
            String[] geomCollectionInserts = new String[]{//
                    "INSERT INTO " + GEOMCOLL_TABLE
                            + " (id, name, temperature, the_geom) VALUES(1, 'Tscherms', 36.0, ST_GeomFromText('" + gCollWKT
                            + "', 4326));", //
            };

            db.createSpatialTable(GEOMCOLL_TABLE, 4326, "the_geom GEOMETRYCOLLECTION",
                    arr("id INT PRIMARY KEY", "name VARCHAR(255)", "temperature REAL"), null);
            for( String insert : geomCollectionInserts ) {
                db.executeInsertUpdateDeleteSql(insert);
            }
            db.executeInsertUpdateDeleteSql("SELECT UpdateLayerStatistics();");
        }
    }

    @AfterClass
    public static void closeDb() throws Exception {
        if (db != null) {
            db.close();
            new File(db.getDatabasePath() + "." + DB_TYPE.getExtension()).delete();
        }
    }

    @Test
    public void testTableOps() throws Exception {
        assertTrue(db.hasTable(MPOLY_TABLE));

        List<String[]> tableColumns = db.getTableColumns(MPOLY_TABLE);
        assertTrue(tableColumns.size() == 4);
        assertEquals("id", tableColumns.get(0)[0].toLowerCase());
        assertEquals("name", tableColumns.get(1)[0].toLowerCase());
        assertEquals("temperature", tableColumns.get(2)[0].toLowerCase());
        assertEquals("the_geom", tableColumns.get(3)[0].toLowerCase());

        HashMap<String, List<String>> tablesMap = db.getTablesMap(false);
        List<String> tables = tablesMap.get(ISpatialTableNames.USERDATA);
        assertTrue(tables.size() == tablesCount);

        List<ForeignKey> foreignKeys = db.getForeignKeys(MPOLY_TABLE);
        assertEquals(0, foreignKeys.size());
        foreignKeys = db.getForeignKeys(MPOINTS_TABLE);
        assertEquals(1, foreignKeys.size());
    }

    @Test
    public void testContents() throws Exception {
        assertEquals(3, db.getCount(MPOLY_TABLE));

        String sql = "select id, name, temperature from " + MPOLY_TABLE + " order by temperature";
        QueryResult result = db.getTableRecordsMapFromRawSql(sql, 2);
        assertEquals(2, result.data.size());

        result = db.getTableRecordsMapFromRawSql(sql, -1);
        assertEquals(3, result.data.size());

        assertEquals(-1, result.geometryIndex);
        double temperature = ((Number) result.data.get(0)[2]).doubleValue();
        assertEquals(34.0, temperature, 0.00001);
    }

    @Test
    public void testAllTablesCount() throws Exception {
        assertEquals(3, db.getCount(MPOLY_TABLE));
        String sql = "select * from " + MPOLY_TABLE;
        QueryResult result = db.getTableRecordsMapFromRawSql(sql, -1);
        assertEquals(3, result.data.size());
        assertTrue(result.geometryIndex != -1);

        assertEquals(3, db.getCount(POLY_TABLE));
        sql = "select * from " + POLY_TABLE;
        result = db.getTableRecordsMapFromRawSql(sql, -1);
        assertEquals(3, result.data.size());
        assertTrue(result.geometryIndex != -1);

        assertEquals(2, db.getCount(MPOINTS_TABLE));
        sql = "select * from " + MPOINTS_TABLE;
        result = db.getTableRecordsMapFromRawSql(sql, -1);
        assertEquals(2, result.data.size());
        assertTrue(result.geometryIndex != -1);

        assertEquals(3, db.getCount(POINTS_TABLE));
        sql = "select * from " + POINTS_TABLE;
        result = db.getTableRecordsMapFromRawSql(sql, -1);
        assertEquals(3, result.data.size());
        assertTrue(result.geometryIndex != -1);

        assertEquals(1, db.getCount(MLINES_TABLE));
        sql = "select * from " + MLINES_TABLE;
        result = db.getTableRecordsMapFromRawSql(sql, -1);
        assertEquals(1, result.data.size());
        assertTrue(result.geometryIndex != -1);

        assertEquals(2, db.getCount(LINES_TABLE));
        sql = "select * from " + LINES_TABLE;
        result = db.getTableRecordsMapFromRawSql(sql, -1);
        assertEquals(2, result.data.size());
        assertTrue(result.geometryIndex != -1);

        if (DB_TYPE == EDb.SPATIALITE) {
            assertEquals(1, db.getCount(GEOMCOLL_TABLE));
            sql = "select * from " + GEOMCOLL_TABLE;
            result = db.getTableRecordsMapFromRawSql(sql, -1);
            assertEquals(1, result.data.size());
            assertTrue(result.geometryIndex != -1);

            Geometry geom = (Geometry) result.data.get(0)[3];
            assertEquals(14, geom.getNumGeometries());
        }
    }

    @Test
    public void testBounds() throws Exception {
        Envelope tableBounds = db.getTableBounds(MPOLY_TABLE);
        Envelope expected = new Envelope(0, 100, 0, 80);
        assertEquals(expected, tableBounds);

        tableBounds = db.getTableBounds(POINTS_TABLE);
        expected = new Envelope(5, 75, 5, 75);
        assertEquals(expected, tableBounds);
    }

    @Test
    public void testGeometries() throws Exception {
        String sql = "select id, name, temperature, the_geom from " + MPOLY_TABLE + " order by temperature";
        QueryResult result = db.getTableRecordsMapFromRawSql(sql, -1);
        assertFalse(result.geometryIndex == -1);
        List<Geometry> geomsList = new ArrayList<>();
        for( Object[] objs : result.data ) {
            assertTrue(objs[result.geometryIndex] instanceof Geometry);
            geomsList.add((Geometry) objs[result.geometryIndex]);
        }
        assertEquals(3, geomsList.size());
    }

    @Test
    public void testGetGeometries() throws Exception {
        List<Geometry> intersecting = db.getGeometriesIn(MPOLY_TABLE, (Envelope) null);
        assertEquals(3, intersecting.size());
    }

    @Test
    public void testIntersectsEnvelope() throws Exception {
        Envelope bounds = new Envelope(5, 80, 5, 80);
        List<Geometry> intersecting = db.getGeometriesIn(MPOLY_TABLE, bounds);
        assertEquals(3, intersecting.size());
    }

    @Test
    public void testIntersectsPolygon() throws Exception {
        String polygonStr = "POLYGON ((71 70, 40 70, 40 40, 5 40, 5 15, 15 15, 15 4, 50 4, 71 70))";
        Geometry geom = new WKTReader().read(polygonStr);
        List<Geometry> intersecting = db.getGeometriesIn(MPOLY_TABLE, geom);
        assertEquals(2, intersecting.size());
    }

    @Test
    public void testGeoJson() throws Exception {
        String geoJson = db.getGeojsonIn(POINTS_TABLE, null, "id=1", 6);
        geoJson = geoJson.replaceAll("\\s+", "");

        String expected;
        if (DB_TYPE == EDb.SPATIALITE) {
            expected = "{\"type\":\"Point\",\"coordinates\":[5,5]}";
        } else {
            expected = "{\"type\":\"MultiPoint\",\"coordinates\":[[5.0,5.0]]}";
        }
        assertEquals(expected, geoJson);

        if (DB_TYPE == EDb.SPATIALITE) {
            expected = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[5,5]},\"properties\":{\"id\":\"1\"}}]}";
        } else {
            expected = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[5.0,5.0]},\"properties\":{\"id\":\"1\"}}]}";
        }
        geoJson = db.getGeojsonIn(POINTS_TABLE, new String[]{"id"}, "id=1", 6);
        geoJson = geoJson.replaceAll("\\s+", "");
        assertEquals(expected, geoJson);
    }

}
