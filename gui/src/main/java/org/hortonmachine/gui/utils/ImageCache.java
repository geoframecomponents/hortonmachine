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
package org.hortonmachine.gui.utils;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.HashMap;

import javax.swing.ImageIcon;

/**
 * A singleton cache for images.
 * 
 * @author Andrea Antonello (www.hydrologis.com)
 */
@SuppressWarnings("nls")
public class ImageCache {
    public static final String HOME = "home.gif";
    public static final String TRASH = "trash.gif";

    public static final String CATEGORY = "category.gif";
    public static final String MODULE = "module.gif";
    public static final String MODULEEXP = "module_exp.gif";
    public static final String MODULE_TEMPLATE = "module_template.gif";
    public static final String TEMPLATE = "template.gif";
    public static final String RUN = "run_module.gif";
    public static final String RUN_TO_FILE = "run_script.gif";
    public static final String RUN_TO_SHAPEFILE = "run_to_shp.gif";
    public static final String STOP = "stop_module.gif";
    public static final String GRID = "grid_obj.gif";
    public static final String PROGRESS_STOP = "progress_stop.gif";

    public static final String NEW = "new.gif";
    public static final String OPEN = "prj_obj.gif";
    public static final String SAVE = "save_edit.gif";
    public static final String COPY = "copy_edit.gif";

    public static final String MEMORY = "memory.gif";
    public static final String DEBUG = "debug.gif";
    public static final String FONT = "font.gif";
    public static final String FILE = "file.gif";
    public static final String FOLDER = "folder.gif";
    public static final String REFRESH = "refresh.gif";

    public static final String CONNECT = "connect.gif";
    public static final String CONNECT_REMOTE = "connect_remote.gif";
    public static final String DISCONNECT = "disconnect.gif";
    public static final String HISTORY_DB = "history_db.gif";
    public static final String NEW_DATABASE = "new_database.gif";
    public static final String DATABASE = "database.gif";
    public static final String TABLE_FOLDER = "table_folder.gif";
    
    public static final String TABLE = "table.gif";
    public static final String VIEW = "view.gif";
    public static final String TABLE_SPATIAL = "table_spatial.gif";
    public static final String TABLE_SPATIAL_VIRTUAL = "table_spatial_virtual.gif";
    
    public static final String TABLE_COLUMN = "table_column.gif";
    public static final String TABLE_COLUMN_PRIMARYKEY = "table_column_pk.gif";
    public static final String TABLE_COLUMN_INDEX = "table_column_index.gif";
    public static final String DBIMAGE = "db_image.gif";
    public static final String LOG = "log.gif";

    public static final String INFO = "information.png";
    public static final String PHOTO = "photo.png";
    public static final String NOTE = "note.gif";

    public static final String EXPORT = "export_wiz.gif";

    public static final String INFOTOOL_ON = "info_on.gif";
    public static final String INFOTOOL_OFF = "info_off.png";

    public static final String GLOBE = "globe.gif";
    public static final String ZOOM_TO_ALL = "zoom_to_all.png";

    public static final String BROWSER = "browser.gif";

    public static final String VECTOR = "vector.png";

    public static final String GEOM_POINT = "geom_point.png";
    public static final String GEOM_LINE = "geom_line.png";
    public static final String GEOM_POLYGON = "geom_polygon.png";

    public static final String TREE_OPEN = "tree_open.png";
    public static final String TREE_CLOSED = "tree_closed.png";

    public static final String SPATIALITE32 = "spatialite32.png";
    public static final String H2GIS32 = "h2gis32.png";
    
    private static ImageCache imageCache;

    private HashMap<String, ImageIcon> imageMap = new HashMap<String, ImageIcon>();
    private Class<ImageCache> class1;

    private ImageCache() {
        class1 = ImageCache.class;
    }

    public static ImageCache getInstance() {
        if (imageCache == null) {
            imageCache = new ImageCache();
        }
        return imageCache;
    }

    /**
     * Get an image for a certain key.
     * 
     * <p>
     * <b>The only keys to be used are the static strings in this class!!</b>
     * </p>
     * 
     * @param key
     *            a file key, as for example {@link ImageCache#DATABASE_VIEW}.
     * @return the image.
     */
    public ImageIcon getImage( String key ) {
        ImageIcon image = imageMap.get(key);
        if (image == null) {
            image = createImage(key);
            imageMap.put(key, image);
        }
        return image;
    }

    public BufferedImage getBufferedImage( String key ) {
        ImageIcon icon = getImage(key);
        BufferedImage bi = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics g = bi.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return bi;
    }

    private ImageIcon createImage( String key ) {
        ImageIcon icon = new ImageIcon(class1.getResource("/org/hortonmachine/images/" + key));
        return icon;
    }

    /**
     * Clears the internal map.
     */
    public void dispose() {
        imageMap.clear();
    }

}
