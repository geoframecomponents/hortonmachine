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
package org.hortonmachine.dbs.h2gis;

import java.io.File;

import org.hortonmachine.dbs.compat.ASpatialDb;
import org.hortonmachine.dbs.compat.ASqlTemplates;
import org.hortonmachine.dbs.compat.objects.ColumnLevel;
import org.hortonmachine.dbs.compat.objects.TableLevel;
import org.hortonmachine.dbs.utils.DbsUtilities;

/**
 * Simple queries templates.
 * 
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class H2GisSqlTemplates extends ASqlTemplates {
    @Override
    public boolean hasAddGeometryColumn() {
        return false;
    }

    @Override
    public String addGeometryColumn( String tableName, String columnName, String srid, String geomType, String dimension ) {
        return null;
    }

    @Override
    public String recoverGeometryColumn( String tableName, String columnName, String srid, String geomType, String dimension ) {
        return null;
    }

    @Override
    public String discardGeometryColumn( String tableName, String geometryColumnName ) {
        return null;
    }

    @Override
    public String createSpatialIndex( String tableName, String columnName ) {
        String query = "CREATE SPATIAL INDEX ON " + tableName + "(" + columnName + ")";
        return query;
    }

    @Override
    public String checkSpatialIndex( String tableName, String columnName ) {
        return null;
    }

    @Override
    public String recoverSpatialIndex( String tableName, String columnName ) {
        return null;
    }

    @Override
    public String disableSpatialIndex( String tableName, String columnName ) {
        return null;
    }

    @Override
    public String showSpatialMetadata( String tableName, String columnName ) {
        String query = "SELECT * FROM GEOMETRY_COLUMNS WHERE Lower(f_table_name) = Lower('" + tableName
                + "') AND Lower(f_geometry_column) = Lower('" + columnName + "')";
        return query;
    }

    @Override
    public String dropTable( String tableName, String geometryColumnName ) {
        String query = "drop table if exists " + tableName + ";";
        return query;
    }

    @Override
    public String reprojectTable( TableLevel table, ASpatialDb db, ColumnLevel geometryColumn, String tableName,
            String newTableName, String newSrid ) throws Exception {
        // TODO
        String letter = tableName.substring(0, 1);
        String columnName = letter + "." + geometryColumn.columnName;
        String query = DbsUtilities.getSelectQuery(db, table, false);
        query = query.replaceFirst(columnName, "ST_Transform(" + columnName + ", " + newSrid + ")");
        query = "create table " + newTableName + " as " + query + ";\n";
        query += "SELECT RecoverGeometryColumn('" + newTableName + "', '" + geometryColumn.columnName + "'," + newSrid + ",'"
                + geometryColumn.columnType + "'," + geometryColumn.geomColumn.coordinatesDimension + ");";
        return query;
    }

    @Override
    public String attachShapefile( File file ) {
        String absolutePath = file.getAbsolutePath();
        String name = file.getName();
        name = name.substring(0, name.length() - 4);
        String tableName = name.replace('.', '_');
        String query = "CALL FILE_TABLE('" + absolutePath + "', '" + tableName + "');";
        return query;
    }
}
