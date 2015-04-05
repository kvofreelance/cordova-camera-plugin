package org.apache.cordova.camera;

/**
 * Created by adventis on 3/25/15.
 */
public class AlbumInfo {
    public String path = "";
    public String name = "";
    public String image = "";
    public long id = 0;

    public AlbumInfo(String path, String name, String image, long id) {
        this.path = path;
        this.name = name;
        this.image = image;
        this.id =id;
    }
}
