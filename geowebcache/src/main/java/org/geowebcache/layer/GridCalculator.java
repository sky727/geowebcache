package org.geowebcache.layer;

import java.util.Arrays;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class GridCalculator {
    private static Log log = LogFactory
            .getLog(org.geowebcache.layer.GridCalculator.class);

    private BBOX base = null;

    // The following are the width of the actual layer
    private double baseWidth;

    private double baseHeight;

    // The following are for a tile, zoomed out all the way
    private double maxTileWidth;

    private double maxTileHeight;

    // Used for unprojected profiles
    private int gridConstant;

    private int[][] gridLevels = null;

    private LayerProfile profile = null;

    // TODO this code does not handle coordinate systems where the base
    // height
    // is bigger than the width
    // private double layerHeight;

    protected GridCalculator(LayerProfile profile, double maxTileWidth,
            double maxTileHeight) {
        this.profile = profile;
        base = profile.gridBase;

        // Calculate
        baseWidth = base.coords[2] - base.coords[0];
        baseHeight = base.coords[3] - base.coords[1];

        this.maxTileWidth = maxTileWidth;
        this.maxTileHeight = maxTileHeight;
        this.gridConstant = (int) Math.round(baseWidth / baseHeight - 1.0);

        calculateGridBounds();
    }

    private void calculateGridBounds() {
        BBOX layerBounds = profile.bbox;
        int zoomStop = profile.zoomStop;

        // We'll just waste a few bytes, for cheap lookups
        gridLevels = new int[zoomStop + 1][4];

        double tileWidth = maxTileWidth;
        double tileHeight = maxTileHeight;

        int tileCountX = (int) Math.round(baseWidth / maxTileWidth);
        int tileCountY = (int) Math.round(baseHeight / maxTileHeight);

        int metaLarger = (profile.metaHeight > profile.metaWidth) ? profile.metaHeight
                : profile.metaWidth;

        // System.out.println("lb: " +layerBounds+ " base:" + base+
        // " tileWidth: " + tileWidth);

        for (int level = 0; level <= zoomStop; level++) {
            // Min X
            gridLevels[level][0] = (int) Math
                    .floor((layerBounds.coords[0] - base.coords[0]) / tileWidth);
            // Min Y
            gridLevels[level][1] = (int) Math
                    .floor((layerBounds.coords[1] - base.coords[1])
                            / tileHeight);
            // Max X
            gridLevels[level][2] = (int) Math
                    .ceil((layerBounds.coords[2] - base.coords[0]) / tileWidth) - 1;
            // Max Y
            gridLevels[level][3] = (int) Math
                    .ceil((layerBounds.coords[3] - base.coords[1]) / tileHeight) - 1;

            // System.out.println("postOrig: " +
            // Arrays.toString(gridLevels[level]));
            //
            // System.out.println("tileCountX "+tileCountX + " metaLarger: "
            // + metaLarger + " baseWidth: "+baseWidth);

            // Adjust for metatiling if appropriate
            if (tileCountX > metaLarger || tileCountY > metaLarger) {
                // Round down
                gridLevels[level][0] = gridLevels[level][0]
                        - (gridLevels[level][0] % profile.metaWidth);
                // Round down
                gridLevels[level][1] = gridLevels[level][1]
                        - (gridLevels[level][1] % profile.metaHeight);
                // Naive round up
                gridLevels[level][2] = gridLevels[level][2]
                        - (gridLevels[level][2] % profile.metaWidth)
                        + (profile.metaWidth - 1);
                // Naive round up
                gridLevels[level][3] = gridLevels[level][3]
                        - (gridLevels[level][3] % profile.metaHeight)
                        + (profile.metaHeight - 1);

                // System.out.println("postAdjust: " +
                // Arrays.toString(gridLevels[level]));

                // Fix for naive round ups, imagine applying a 3x3 metatile to a
                // 4x4 grid
                if (gridLevels[level][2] >= tileCountX) {
                    gridLevels[level][2] = tileCountX - 1;
                }
                if (gridLevels[level][3] >= tileCountY) {
                    gridLevels[level][3] = tileCountY - 1;
                }
                // System.out.println("postFix: " +
                // Arrays.toString(gridLevels[level]));
            }

            // For the next round
            tileWidth = tileWidth / 2;
            tileHeight = tileHeight / 2;

            tileCountX = tileCountX * 2;
            tileCountY = tileCountY * 2;
        }
    }

    protected int[] getGridBounds(int zoomLevel) {
        return gridLevels[zoomLevel].clone();
    }

    /**
     * Determines the location in a three dimensional grid based on WMS
     * recommendations.
     * 
     * It creates a grid of (2^zoomLevel x 2^zoomLevel) tiles. 0,0 denotes the
     * bottom left corner. The tile's location in this grid is determined as
     * follows:
     * 
     * <ol>
     * <li>Based on the width of the requested tile the desired zoomlevel is
     * determined.</li>
     * <li>The rounded zoomLevel is used to divide the width into 2^zoomLevel
     * segments</li>
     * <li>The min X value is used to determine the X position on this grid</li>
     * <li>The min Y value is used to determine the Y position on this grid</li>
     * </ol>
     * 
     * @param tileBounds
     *            the bounds of the requested tile
     * @return [0] = x coordinate , [1] y coordinate, [2] = zoomLevel
     */
    public int[] gridLocation(BBOX tileBounds) {
        int[] retVals = new int[3];

        double reqTileWidth = tileBounds.coords[2] - tileBounds.coords[0];

        // (Z) Zoom level
        // For EPSG 4326, reqTileWidth = 0.087 log(4096) / log(2) - 1; -> 11
        retVals[2] = (int) Math.round(Math.log(baseWidth / reqTileWidth)
                / (Math.log(2)))
                - gridConstant;

        double tileWidth = baseWidth / (Math.pow(2, retVals[2] + gridConstant));

        // X
        double xdiff = tileBounds.coords[0] - base.coords[0];
        retVals[0] = (int) Math.round(xdiff / tileWidth);
        // Y
        double ydiff = tileBounds.coords[1] - base.coords[1];
        retVals[1] = (int) Math.round(ydiff / tileWidth);

        if (log.isTraceEnabled()) {
            log.trace("x: " + retVals[0] + " y:" + retVals[1] + " z:"
                    + retVals[2]);
        }

        return retVals;
    }

    public String isInRange(int[] location) {
        // Check Z
        if (location[2] < profile.zoomStart) {
            return "zoomlevel (" + location[2] + ")must be at least "
                    + profile.zoomStart;
        }
        if (location[2] >= gridLevels.length) {
            return "zoomlevel (" + location[2] + ") must be at most "
                    + gridLevels.length;
        }

        int[] bounds = gridLevels[location[2]];

        String gridDebug = " Extra debug information bounds: "
                + Arrays.toString(bounds) + " gridLoc: "
                + Arrays.toString(location);

        // Check X
        if (location[0] < bounds[0]) {
            return "gridX (" + location[0] + ") must be at least " + bounds[0]
                    + gridDebug;
        } else if (location[0] > bounds[2]) {
            return "gridX (" + location[0] + ") can be at most " + bounds[2]
                    + gridDebug;
        }

        // Check Y
        if (location[0] < bounds[0]) {
            return "gridY (" + location[1] + ") must be at least " + bounds[1]
                    + gridDebug;
        } else if (location[0] > bounds[2]) {
            return "gridY (" + location[1] + ") can be at most " + bounds[3]
                    + gridDebug;
        }

        return null;
    }

    // /**
    // * Calculates bottom left and top right grid positions for a particular
    // * zoomlevel
    // *
    // * @param bounds
    // * @return
    // */
    // protected int[] gridSeedExtent(int zoomLevel, BBOX bounds) {
    // int[] retVals = new int[4];
    //
    // double tileWidth = baseWidth / (Math.pow(2, zoomLevel));
    // // min X
    // retVals[0] = (int) Math.round((bounds.coords[0] - base.coords[0])
    // / tileWidth);
    // // min Y
    // retVals[1] = (int) Math.round((bounds.coords[1] - base.coords[1])
    // / tileWidth);
    // // max X
    // retVals[2] = (int) Math.round((bounds.coords[2] - base.coords[0])
    // / tileWidth) - 1;
    // // max Y
    // retVals[3] = (int) Math.round((bounds.coords[3] - base.coords[1])
    // / tileWidth) - 1;
    //
    // return retVals;
    // }

    /**
     * Uses the location on the grid to determine bounding box for a single
     * tile.
     * 
     * @param gridLoc
     * @return
     */
    public BBOX bboxFromGridLocation(int[] gridLoc) {
        double tileWidth = baseWidth / Math.pow(2, gridLoc[2] + gridConstant);

        return new BBOX(base.coords[0] + tileWidth * gridLoc[0], base.coords[1]
                + tileWidth * gridLoc[1], base.coords[0] + tileWidth
                * (gridLoc[0] + 1), base.coords[1] + tileWidth
                * (gridLoc[1] + 1));
    }

    /**
     * Uses the grid bounds to determine the bounding box, presumably for a
     * metatile.
     * 
     * Adds one tilewidth to the top and right.
     * 
     * @param gridBounds
     * @return
     */

    public BBOX bboxFromGridBounds(int[] gridBounds) {
        double tileWidth = baseWidth
                / Math.pow(2, gridBounds[4] + gridConstant);

        return new BBOX(base.coords[0] + tileWidth * gridBounds[0],
                base.coords[1] + tileWidth * gridBounds[1], base.coords[0]
                        + tileWidth * (gridBounds[2] + 1), base.coords[1]
                        + tileWidth * (gridBounds[3] + 1));
    }

    // /**
    // * Used for seeding, returns gridExtent but adjusts for meta tile size
    // *
    // * @return
    // */
    // protected int[] metaGridSeedExtent(int zoomLevel, BBOX bounds) {
    // int[] retVals = gridSeedExtent(zoomLevel, bounds);
    // retVals[0] = retVals[0] - (retVals[0] % profile.metaWidth);
    // retVals[1] = retVals[1] - (retVals[1] % profile.metaHeight);
    // retVals[2] = retVals[2] + (retVals[2] % profile.metaWidth);
    // retVals[3] = retVals[3] + (retVals[3] % profile.metaHeight);
    // return retVals;
    // }

}