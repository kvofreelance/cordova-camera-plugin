/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova.camera;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * This class launches the camera view, allows the user to take a picture, closes the camera view,
 * and returns the captured image.  When the camera view is closed, the screen displayed before
 * the camera view was shown is redisplayed.
 */
public class CameraLauncher extends CordovaPlugin implements MediaScannerConnectionClient {

    private static final int DATA_URL = 0;              // Return base64 encoded string
    private static final int FILE_URI = 1;              // Return file uri (content://media/external/images/media/2 for Android)
    private static final int NATIVE_URI = 2;			// On Android, this is the same as FILE_URI

    private static final int PHOTOLIBRARY = 0;          // Choose image from picture library (same as SAVEDPHOTOALBUM for Android)
    private static final int CAMERA = 1;                // Take picture from camera
    private static final int SAVEDPHOTOALBUM = 2;       // Choose image from picture library (same as PHOTOLIBRARY for Android)

    private static final int PICTURE = 0;               // allow selection of still pictures only. DEFAULT. Will return format specified via DestinationType
    private static final int VIDEO = 1;                 // allow selection of video only, ONLY RETURNS URL
    private static final int ALLMEDIA = 2;              // allow selection from all media types

    private static final int JPEG = 0;                  // Take a picture of type JPEG
    private static final int PNG = 1;                   // Take a picture of type PNG
    private static final String GET_PICTURE = "Get Picture";
    private static final String GET_VIDEO = "Get Video";
    private static final String GET_All = "Get All";
    
    private static final String LOG_TAG = "CameraLauncher";

    private int mQuality;                   // Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
    private int targetWidth;                // desired width of the image
    private int targetHeight;               // desired height of the image
    private Uri imageUri;                   // Uri of captured image
    private int encodingType;               // Type of encoding to use
    private int mediaType;                  // What type of media to retrieve
    private boolean saveToPhotoAlbum;       // Should the picture be saved to the device's photo album
    private boolean correctOrientation;     // Should the pictures orientation be corrected
    //private boolean allowEdit;              // Should we allow the user to crop the image. UNUSED.
    private int maxSelectedLimit;  

    public CallbackContext callbackContext;
    private int numPics;

    private MediaScannerConnection conn;    // Used to update gallery app with newly-written files
    private Uri scanMe;                     // Uri of image to be added to content store

    public String selectedVideo = "";

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action        	The action to execute.
     * @param args          	JSONArry of arguments for the plugin.
     * @param callbackContext   The callback id used when calling back into JavaScript.
     * @return              	A PluginResult object with a status and message.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        if (action.equals("takePicture")) {
            int srcType = CAMERA;
            int destType = FILE_URI;
            this.saveToPhotoAlbum = false;
            this.targetHeight = 0;
            this.targetWidth = 0;
            this.encodingType = JPEG;
            this.mediaType = PICTURE;
            this.mQuality = 80;
            this.maxSelectedLimit = 1;

            this.mQuality = args.getInt(0);
            destType = args.getInt(1);
            srcType = args.getInt(2);
            this.targetWidth = args.getInt(3);
            this.targetHeight = args.getInt(4);
            this.encodingType = args.getInt(5);
            this.mediaType = args.getInt(6);
            //this.allowEdit = args.getBoolean(7); // This field is unused.
            this.correctOrientation = args.getBoolean(8);
            this.saveToPhotoAlbum = args.getBoolean(9);
            this.maxSelectedLimit = args.getInt(12);
            if(this.maxSelectedLimit <= 0) {
                this.maxSelectedLimit = 1;
            }

            // If the user specifies a 0 or smaller width/height
            // make it -1 so later comparisons succeed
            if (this.targetWidth < 1) {
                this.targetWidth = -1;
            }
            if (this.targetHeight < 1) {
                this.targetHeight = -1;
            }

             try {
                if (srcType == CAMERA) {
                    this.takePicture(destType, encodingType);
                }
                else if ((srcType == PHOTOLIBRARY) || (srcType == SAVEDPHOTOALBUM)) {
                    this.getImage(srcType, destType);
                }
            }
            catch (IllegalArgumentException e)
            {
                callbackContext.error("Illegal Argument Exception");
                PluginResult r = new PluginResult(PluginResult.Status.ERROR);
                callbackContext.sendPluginResult(r);
                return true;
            }
             
            PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
            r.setKeepCallback(true);
            callbackContext.sendPluginResult(r);
            
            return true;
        }
        return false;
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------

    private String getTempDirectoryPath() {
        File cache = null;

        // SD Card Mounted
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            cache = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/Android/data/" + cordova.getActivity().getPackageName() + "/cache/");
        }
        // Use internal storage
        else {
            cache = cordova.getActivity().getCacheDir();
        }

        // Create the cache directory if it doesn't exist
        cache.mkdirs();
        return cache.getAbsolutePath();
    }

    /**
     * Take a picture with the camera.
     * When an image is captured or the camera view is cancelled, the result is returned
     * in CordovaActivity.onActivityResult, which forwards the result to this.onActivityResult.
     *
     * The image can either be returned as a base64 string or a URI that points to the file.
     * To display base64 string in an img tag, set the source to:
     *      img.src="data:image/jpeg;base64,"+result;
     * or to display URI in an img tag
     *      img.src=result;
     *
     * @param quality           Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
     * @param returnType        Set the type of image to return.
     */
    public void takePicture(int returnType, int encodingType) {
        // Save the number of images currently on disk for later
        this.numPics = queryImgDB(whichContentStore()).getCount();

        // Display camera
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");

        // Specify file so that large image is captured and returned
        File photo = createCaptureFile(encodingType);
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, Uri.fromFile(photo));
        this.imageUri = Uri.fromFile(photo);

        if (this.cordova != null) {
            this.cordova.startActivityForResult((CordovaPlugin) this, intent, (CAMERA + 1) * 16 + returnType + 1);
        }
//        else
//            LOG.d(LOG_TAG, "ERROR: You must use the CordovaInterface for this to work correctly. Please implement it in your activity");
    }

    /**
     * Create a file in the applications temporary directory based upon the supplied encoding.
     *
     * @param encodingType of the image to be taken
     * @return a File object pointing to the temporary picture
     */
    private File createCaptureFile(int encodingType) {
        File photo = null;
        if (encodingType == JPEG) {
            photo = new File(getTempDirectoryPath(), ".Pic.jpg");
        } else if (encodingType == PNG) {
            photo = new File(getTempDirectoryPath(), ".Pic.png");
        } else {
            throw new IllegalArgumentException("Invalid Encoding Type: " + encodingType);
        }
        return photo;
    }

    static public Bitmap decodeSampledBitmapFromResourceMemOpt(InputStream inputStream, int reqWidth, int reqHeight) {

        byte[] byteArr = new byte[0];
        byte[] buffer = new byte[1024];
        int len;
        int count = 0;

        try {
            while ((len = inputStream.read(buffer)) > -1) {
                if (len != 0) {
                    if (count + len > byteArr.length) {
                        byte[] newbuf = new byte[(count + len) * 2];
                        System.arraycopy(byteArr, 0, newbuf, 0, count);
                        byteArr = newbuf;
                    }

                    System.arraycopy(buffer, 0, byteArr, count, len);
                    count += len;
                }
            }

            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(byteArr, 0, count, options);

            if(!(reqHeight ==0 && reqWidth == 0)) {
                options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight);
            }

            options.inPurgeable = true;
            options.inInputShareable = true;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

            return BitmapFactory.decodeByteArray(byteArr, 0, count, options);

        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public int getAndroidVersion() {
        return Integer.parseInt(Build.VERSION.RELEASE.split("\\.")[0]);
    }

    /**
     * Get image from photo library.
     *
     * @param quality           Compression quality hint (0-100: 0=low quality & high compression, 100=compress of max quality)
     * @param srcType           The album to get image from.
     * @param returnType        Set the type of image to return.
     */
    // TODO: Images selected from SDCARD don't display correctly, but from CAMERA ALBUM do!
    public void getImage(int srcType, int returnType) {

            Intent intent = new Intent();
            String title = GET_PICTURE;
            if (this.mediaType == PICTURE) {
                //if(this.maxSelectedLimit != 0) {
                    intent = new Intent(this.cordova.getActivity(), AlbumActivity.class);
                    intent.putExtra("maxSelectedLimit", this.maxSelectedLimit);
                //} else {
                //    intent.setType("image/*");
                //}

                if (this.cordova != null) {
                    this.cordova.startActivityForResult((CordovaPlugin) this, intent, (srcType + 1) * 16 + returnType + 1);
                }
                return;
            }
            else if (this.mediaType == VIDEO) {
                /*intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("video/*");
                title = GET_VIDEO;*/
                //if((getAndroidVersion() >= 4)) {
                    // If ANDROID version >= 4
                    intent = new Intent(this.cordova.getActivity(), AlbumVideoActivity.class);

                //} else {
                    // If ANDROID version < 4
                //    intent = new Intent(this.cordova.getActivity(), CustomVideoGallery.class);
                //}

                //intent = new Intent(this.cordova.getActivity(), CustomVideoGallery.class);
                intent.putExtra("maxSelectedLimit", 1);
                if (this.cordova != null) {
                    this.cordova.startActivityForResult((CordovaPlugin) this, intent, (srcType + 1) * 16 + returnType + 1);
                }
                return;
            }
            else if (this.mediaType == ALLMEDIA) {
                // I wanted to make the type 'image/*, video/*' but this does not work on all versions
                // of android so I had to go with the wildcard search.
                intent.setType("*/*");
                title = GET_All;
            }

            intent.setAction(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (this.cordova != null) {
                this.cordova.startActivityForResult((CordovaPlugin) this, Intent.createChooser(intent,
                        new String(title)), (srcType + 1) * 16 + returnType + 1);
            }
    }

    public String getResponseString(ArrayList<Uri> imagesList) {
        String result = "";
        for (Uri uri : imagesList) {
            result = result + getPath(cordova.getActivity(), uri) + ";";
        }
        
        return result;
    }

    public String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        //try {
            String[] proj = { MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATA };
            cursor = context.getContentResolver().query(contentUri,  proj, null, null, null);
        Log.d("VIDEO_PATH", cursor.getCount()+"");
        Log.d("VIDEO_PATH", MediaStore.Video.Media.EXTERNAL_CONTENT_URI.getPath()+"");
        Log.d("VIDEO_PATH", MediaStore.Video.Media.DISPLAY_NAME+"");
            int column_index = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
        int column_index2 = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
        Log.d("VIDEO_PATH", column_index+"");
            cursor.moveToFirst();
        Log.d("VIDEO_PATH", cursor.getString(column_index)+"");
        Log.d("VIDEO_PATH", cursor.getString(column_index2)+"");
            return cursor.getString(column_index);
        //} finally {
          //  if (cursor != null) {
            //    cursor.close();
            //}
        //}
    }

    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/"
                            + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"),
                        Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] { split[1] };

                return getDataColumn(context, contentUri, selection,
                        selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context
     *            The context.
     * @param uri
     *            The Uri to query.
     * @param selection
     *            (Optional) Filter used in the query.
     * @param selectionArgs
     *            (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri,
                                       String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = { column };

        try {
            cursor = context.getContentResolver().query(uri, projection,
                    selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri
     *            The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri
                .getAuthority());
    }

    /**
     * @param uri
     *            The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri
                .getAuthority());
    }

    /**
     * @param uri
     *            The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri
                .getAuthority());
    }

    /**
     * Called when the camera view exits.
     *
     * @param requestCode       The request code originally supplied to startActivityForResult(),
     *                          allowing you to identify who this result came from.
     * @param resultCode        The integer result code returned by the child activity through its setResult().
     * @param intent            An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.d("EXPEREMENT", "onActivityResult");
        // Get src and dest types from request code
        int srcType = (requestCode / 16) - 1;
        int destType = (requestCode % 16) - 1;
        int rotate = 0;

        // If CAMERA
        if (srcType == CAMERA) {
            // If image available
            if (resultCode == Activity.RESULT_OK) {
                try {
                    // Create an ExifHelper to save the exif data that is lost during compression
                    ExifHelper exif = new ExifHelper();
                    try {
                        if (this.encodingType == JPEG) {
                            exif.createInFile(getTempDirectoryPath() + "/.Pic.jpg");
                            exif.readExifData();
                            rotate = exif.getOrientation();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    Bitmap bitmap = null;
                    Uri uri = null;

                    // If sending base64 image back
                    if (destType == DATA_URL) {
                        bitmap = getScaledBitmap(FileHelper.stripFileProtocol(imageUri.toString()));
                        if (bitmap == null) {
                            // Try to get the bitmap from intent.
                            bitmap = (Bitmap)intent.getExtras().get("data");
                        }
                        
                        // Double-check the bitmap.
                        if (bitmap == null) {
                            Log.d(LOG_TAG, "I either have a null image path or bitmap");
                            this.failPicture("Unable to create bitmap!");
                            return;
                        }

                        if (rotate != 0 && this.correctOrientation) {
                            bitmap = getRotatedBitmap(rotate, bitmap, exif);
                        }

                        this.processPicture(bitmap);
                        checkForDuplicateImage(DATA_URL);
                    }

                    // If sending filename back
                    else if (destType == FILE_URI || destType == NATIVE_URI) {
                        if (this.saveToPhotoAlbum) {
                            Uri inputUri = getUriFromMediaStore();
                            //Just because we have a media URI doesn't mean we have a real file, we need to make it
                            uri = Uri.fromFile(new File(FileHelper.getRealPath(inputUri, this.cordova)));
                        } else {
                            uri = Uri.fromFile(new File(getTempDirectoryPath(), System.currentTimeMillis() + ".jpg"));
                        }

                        if (uri == null) {
                            this.failPicture("Error capturing image - no media storage found.");
                        }

                        // If all this is true we shouldn't compress the image.
                        if (this.targetHeight == -1 && this.targetWidth == -1 && this.mQuality == 100 && 
                                !this.correctOrientation) {
                            writeUncompressedImage(uri);

                            this.callbackContext.success(uri.toString());
                        } else {
                            bitmap = getScaledBitmap(FileHelper.stripFileProtocol(imageUri.toString()));

                            if (rotate != 0 && this.correctOrientation) {
                                bitmap = getRotatedBitmap(rotate, bitmap, exif);
                            }

                            // Add compressed version of captured image to returned media store Uri
                            OutputStream os = this.cordova.getActivity().getContentResolver().openOutputStream(uri);
                            bitmap.compress(Bitmap.CompressFormat.JPEG, this.mQuality, os);
                            os.close();

                            // Restore exif data to file
                            if (this.encodingType == JPEG) {
                                String exifPath;
                                if (this.saveToPhotoAlbum) {
                                    exifPath = FileHelper.getRealPath(uri, this.cordova);
                                } else {
                                    exifPath = uri.getPath();
                                }
                                exif.createOutFile(exifPath);
                                exif.writeExifData();
                            }

                        }
                        // Send Uri back to JavaScript for viewing image
                        this.callbackContext.success(uri.toString());
                    }

                    this.cleanup(FILE_URI, this.imageUri, uri, bitmap);
                    bitmap = null;

                } catch (IOException e) {
                    e.printStackTrace();
                    this.failPicture("Error capturing image.");
                }
            }

            // If cancelled
            else if (resultCode == Activity.RESULT_CANCELED) {
                this.failPicture("Camera cancelled.");
            }

            // If something else
            else {
                this.failPicture("Did not complete!");
            }
        }

        // If retrieving photo from library
        else if ((srcType == PHOTOLIBRARY) || (srcType == SAVEDPHOTOALBUM)) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = intent.getData();

                // If you ask for video or all media type you will automatically get back a file URI
                // and there will be no attempt to resize any returned data
                if (this.mediaType == VIDEO) {
                    /*if(this.maxSelectedLimit != 0) {
                        ArrayList<Uri> imagesListUri = intent.getParcelableArrayListExtra("selectedImagesArray");
                        String imagesList = getResponseString(imagesListUri);
                        this.callbackContext.success(imagesList);
                    } else {*/
                        //selectedVideo = getPath(cordova.getActivity(), uri);
                        ArrayList<Uri> imagesListUri = intent.getParcelableArrayListExtra("selectedVideosArray");
                        String imagesList = getResponseString(imagesListUri);
                        this.callbackContext.success(imagesList);
                        //this.callbackContext.success(selectedVideo);
                   // }
                }
                else {
                    // This is a special case to just return the path as no scaling,
                    // rotating, nor compressing needs to be done
                    if (this.targetHeight == -1 && this.targetWidth == -1 &&
                            (destType == FILE_URI || destType == NATIVE_URI) && !this.correctOrientation) {
                        if(this.maxSelectedLimit != 0) {
                            ArrayList<Uri> imagesListUri = intent.getParcelableArrayListExtra("selectedImagesArray");
                            String imagesList = getResponseString(imagesListUri);
                            this.callbackContext.success(imagesList);
                        } else {
                            this.callbackContext.success(uri.toString());
                        }
                    } else {
                        ArrayList<Uri> listOfSelectedImages = new ArrayList<Uri>();
                        if(this.maxSelectedLimit != 0) {
                            listOfSelectedImages = intent.getParcelableArrayListExtra("selectedImagesArray");
                        } else {
                            listOfSelectedImages.add(uri);
                        }

                        ArrayList<String> returnArrayList = new ArrayList<String>();
                        for(int i=0; i < listOfSelectedImages.size(); i++) {    
                            if(listOfSelectedImages.get(i) != null) {
                                uri = listOfSelectedImages.get(i);

                                String uriString = "";
                                if(this.maxSelectedLimit != 0) {
                                    uriString = listOfSelectedImages.get(i).getPath();
                                } else {
                                    uriString = listOfSelectedImages.get(i).toString();
                                }
                                
                                // Get the path to the image. Makes loading so much easier.
                                String mimeType = FileHelper.getMimeType(uriString, this.cordova);
                                // If we don't have a valid image so quit.
                                if (!("image/jpeg".equalsIgnoreCase(mimeType) || "image/png".equalsIgnoreCase(mimeType))) {
                            	   Log.d(LOG_TAG, "I either have a null image path or bitmap");
                                    this.failPicture("Unable to retrieve path to picture!");
                                    return;
                                }
                                Bitmap bitmap = null;
                                try {
                                    bitmap = getScaledBitmap(uriString);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                if (bitmap == null) {
                            	   Log.d(LOG_TAG, "I either have a null image path or bitmap");
                                    this.failPicture("Unable to create bitmap!");
                                    return;
                                }

                                if (this.correctOrientation) {
                                    if(this.maxSelectedLimit != 0) {
                                        rotate = getImageOrientationManySelected(uri);
                                    } else {
                                        rotate = getImageOrientation(uri);
                                    }
                                    if (rotate != 0) {
                                        Matrix matrix = new Matrix();
                                        matrix.setRotate(rotate);
                                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                    }
                                }

                                // If sending base64 image back
                                if (destType == DATA_URL) {
                                    if(this.maxSelectedLimit != 0) {
                                        returnArrayList.add(getBase64Picture(bitmap));
                                    } else {
                                        this.processPicture(bitmap);
                                    }
                                }

                                // If sending filename back
                                else if (destType == FILE_URI || destType == NATIVE_URI) {
                                    // Do we need to scale the returned file
                                    if (this.targetHeight > 0 && this.targetWidth > 0) {
                                        try {
                                            // Create an ExifHelper to save the exif data that is lost during compression
                                            String resizePath = getTempDirectoryPath() + "/resize"+i+".jpg";
                                            // Some content: URIs do not map to file paths (e.g. picasa).
                                            String realPath = FileHelper.getRealPath(uri, this.cordova);
                                            ExifHelper exif = new ExifHelper();
                                            if (realPath != null && this.encodingType == JPEG) {
                                                try {
                                                    exif.createInFile(realPath);
                                                    exif.readExifData();
                                                    rotate = exif.getOrientation();
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }
                                            }

                                            OutputStream os = new FileOutputStream(resizePath);
                                            bitmap.compress(Bitmap.CompressFormat.JPEG, this.mQuality, os);
                                            os.close();

                                            // Restore exif data to file
                                            if (realPath != null && this.encodingType == JPEG) {
                                                exif.createOutFile(resizePath);
                                                exif.writeExifData();
                                            }

                                            // The resized image is cached by the app in order to get around this and not have to delete you
                                            // application cache I'm adding the current system time to the end of the file url.
                                            returnArrayList.add("file://" + resizePath + "?" + System.currentTimeMillis());
                                            //this.callbackContext.success("file://" + resizePath + "?" + System.currentTimeMillis());
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            //this.failPicture("Error retrieving image.");
                                        }
                                    }
                                    else {
                                        returnArrayList.add(uri.getPath());
                                        //this.callbackContext.success(uri.toString());
                                    }
                                }
                                if (bitmap != null) {
    	                           bitmap.recycle();
    	                           bitmap = null;
                                }
                                System.gc();
                            }
                        }

                        if(returnArrayList.size() == 0) {
                            Log.d("SelectedImages", "Error retrieving image.");
                            this.failPicture("Error retrieving image.");
                        } else if(returnArrayList.size() == 1) {
                            Log.d("SelectedImages", "One result: " + returnArrayList.get(0));
                            this.callbackContext.success(returnArrayList.get(0));
                        } else {
                            String result = "";
                            for(int i=0; i < returnArrayList.size(); i++) {
                                result += returnArrayList.get(i)+";";
                            }
                            Log.d("SelectedImages", "Many results: "+result);
                            this.callbackContext.success(result);
                        }
                    }
                }
            }
            else if (resultCode == Activity.RESULT_CANCELED) {
                this.failPicture("Selection cancelled.");
            }
            else {
                this.failPicture("Selection did not complete!");
            }
        }
    }

    private int getImageOrientation(Uri uri) {
        String[] cols = { MediaStore.Images.Media.ORIENTATION };
        Cursor cursor = cordova.getActivity().getContentResolver().query(uri,
                cols, null, null, null);
        int rotate = 0;
        if (cursor != null) {
            cursor.moveToPosition(0);
            rotate = cursor.getInt(0);
            cursor.close();
        }
        return rotate;
    }

     private int getImageOrientationManySelected(Uri uri) {
        File imageFile = new File(uri.getPath());
        int orientation = 0;

        ExifInterface exif = null;
        try {
            exif = new ExifInterface(imageFile.getAbsolutePath());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
       
        orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        if(orientation == 1) {
            orientation = 0;
        } else if (orientation == 3) {
            orientation = 180;
        } else if (orientation == 6) {
            orientation = 90;
        } else if (orientation == 8) {
            orientation = -90;
        }
        
        
        return orientation;
    }

    /**
     * Figure out if the bitmap should be rotated. For instance if the picture was taken in
     * portrait mode
     *
     * @param rotate
     * @param bitmap
     * @return rotated bitmap
     */
    private Bitmap getRotatedBitmap(int rotate, Bitmap bitmap, ExifHelper exif) {
        Matrix matrix = new Matrix();
        if (rotate == 180) {
            matrix.setRotate(rotate);
        } else {
            matrix.setRotate(rotate, (float) bitmap.getWidth() / 2, (float) bitmap.getHeight() / 2);
        }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        exif.resetOrientation();
        return bitmap;
    }

    /**
     * In the special case where the default width, height and quality are unchanged
     * we just write the file out to disk saving the expensive Bitmap.compress function.
     *
     * @param uri
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void writeUncompressedImage(Uri uri) throws FileNotFoundException,
            IOException {
        FileInputStream fis = new FileInputStream(FileHelper.stripFileProtocol(imageUri.toString()));
        OutputStream os = this.cordova.getActivity().getContentResolver().openOutputStream(uri);
        byte[] buffer = new byte[4096];
        int len;
        while ((len = fis.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        os.flush();
        os.close();
        fis.close();
    }

    /**
     * Create entry in media store for image
     *
     * @return uri
     */
    private Uri getUriFromMediaStore() {
        ContentValues values = new ContentValues();
        values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        Uri uri;
        try {
            uri = this.cordova.getActivity().getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (UnsupportedOperationException e) {
            LOG.d(LOG_TAG, "Can't write to external media storage.");
            try {
                uri = this.cordova.getActivity().getContentResolver().insert(android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI, values);
            } catch (UnsupportedOperationException ex) {
                LOG.d(LOG_TAG, "Can't write to internal media storage.");
                return null;
            }
        }
        return uri;
    }

    /**
     * Return a scaled bitmap based on the target width and height
     *
     * @param imagePath
     * @return
     * @throws IOException 
     */
    private Bitmap getScaledBitmap(String imageUrl) throws IOException {
        // If no new width or height were specified return the original bitmap
        if (this.targetWidth <= 0 && this.targetHeight <= 0) {
            return BitmapFactory.decodeStream(FileHelper.getInputStreamFromUriString(imageUrl, cordova));
        }

        // figure out the original width and height of the image
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(FileHelper.getInputStreamFromUriString(imageUrl, cordova), null, options);
        
        //CB-2292: WTF? Why is the width null?
        if(options.outWidth == 0 || options.outHeight == 0)
        {
            return null;
        }
        
        // determine the correct aspect ratio
        int[] widthHeight = calculateAspectRatio(options.outWidth, options.outHeight);

        // Load in the smallest bitmap possible that is closest to the size we want
        options.inJustDecodeBounds = false;
        options.inSampleSize = calculateSampleSize(options.outWidth, options.outHeight, this.targetWidth, this.targetHeight);
        Bitmap unscaledBitmap = BitmapFactory.decodeStream(FileHelper.getInputStreamFromUriString(imageUrl, cordova), null, options);
        if (unscaledBitmap == null) {
            return null;
        }

        return Bitmap.createScaledBitmap(unscaledBitmap, widthHeight[0], widthHeight[1], true);
    }

    /**
     * Maintain the aspect ratio so the resulting image does not look smooshed
     *
     * @param origWidth
     * @param origHeight
     * @return
     */
    public int[] calculateAspectRatio(int origWidth, int origHeight) {
        int newWidth = this.targetWidth;
        int newHeight = this.targetHeight;

        // If no new width or height were specified return the original bitmap
        if (newWidth <= 0 && newHeight <= 0) {
            newWidth = origWidth;
            newHeight = origHeight;
        }
        // Only the width was specified
        else if (newWidth > 0 && newHeight <= 0) {
            newHeight = (newWidth * origHeight) / origWidth;
        }
        // only the height was specified
        else if (newWidth <= 0 && newHeight > 0) {
            newWidth = (newHeight * origWidth) / origHeight;
        }
        // If the user specified both a positive width and height
        // (potentially different aspect ratio) then the width or height is
        // scaled so that the image fits while maintaining aspect ratio.
        // Alternatively, the specified width and height could have been
        // kept and Bitmap.SCALE_TO_FIT specified when scaling, but this
        // would result in whitespace in the new image.
        else {
            double newRatio = newWidth / (double) newHeight;
            double origRatio = origWidth / (double) origHeight;

            if (origRatio > newRatio) {
                newHeight = (newWidth * origHeight) / origWidth;
            } else if (origRatio < newRatio) {
                newWidth = (newHeight * origWidth) / origHeight;
            }
        }

        int[] retval = new int[2];
        retval[0] = newWidth;
        retval[1] = newHeight;
        return retval;
    }

    /**
     * Figure out what ratio we can load our image into memory at while still being bigger than
     * our desired width and height
     *
     * @param srcWidth
     * @param srcHeight
     * @param dstWidth
     * @param dstHeight
     * @return
     */
    public static int calculateSampleSize(int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
        final float srcAspect = (float)srcWidth / (float)srcHeight;
        final float dstAspect = (float)dstWidth / (float)dstHeight;

        if (srcAspect > dstAspect) {
            return srcWidth / dstWidth;
        } else {
            return srcHeight / dstHeight;
        }
      }

    /**
     * Creates a cursor that can be used to determine how many images we have.
     *
     * @return a cursor
     */
    private Cursor queryImgDB(Uri contentStore) {
        return this.cordova.getActivity().getContentResolver().query(
                contentStore,
                new String[] { MediaStore.Images.Media._ID },
                null,
                null,
                null);
    }

    /**
     * Cleans up after picture taking. Checking for duplicates and that kind of stuff.
     * @param newImage
     */
    private void cleanup(int imageType, Uri oldImage, Uri newImage, Bitmap bitmap) {
        if (bitmap != null) {
            bitmap.recycle();
        }

        // Clean up initial camera-written image file.
        (new File(FileHelper.stripFileProtocol(oldImage.toString()))).delete();

        checkForDuplicateImage(imageType);
        // Scan for the gallery to update pic refs in gallery
        if (this.saveToPhotoAlbum && newImage != null) {
            this.scanForGallery(newImage);
        }

        System.gc();
    }

    /**
     * Used to find out if we are in a situation where the Camera Intent adds to images
     * to the content store. If we are using a FILE_URI and the number of images in the DB
     * increases by 2 we have a duplicate, when using a DATA_URL the number is 1.
     *
     * @param type FILE_URI or DATA_URL
     */
    private void checkForDuplicateImage(int type) {
        int diff = 1;
        Uri contentStore = whichContentStore();
        Cursor cursor = queryImgDB(contentStore);
        int currentNumOfImages = cursor.getCount();

        if (type == FILE_URI && this.saveToPhotoAlbum) {
            diff = 2;
        }

        // delete the duplicate file if the difference is 2 for file URI or 1 for Data URL
        if ((currentNumOfImages - numPics) == diff) {
            cursor.moveToLast();
            int id = Integer.valueOf(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media._ID)));
            if (diff == 2) {
                id--;
            }
            Uri uri = Uri.parse(contentStore + "/" + id);
            this.cordova.getActivity().getContentResolver().delete(uri, null, null);
            cursor.close();
        }
    }

    /**
     * Determine if we are storing the images in internal or external storage
     * @return Uri
     */
    private Uri whichContentStore() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else {
            return android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI;
        }
    }

    /**
     * Compress bitmap using jpeg, convert to Base64 encoded string, and return to JavaScript.
     *
     * @param bitmap
     */
    public void processPicture(Bitmap bitmap) {
        ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();
        try {
            if (bitmap.compress(CompressFormat.JPEG, mQuality, jpeg_data)) {
                byte[] code = jpeg_data.toByteArray();
                byte[] output = Base64.encode(code, Base64.NO_WRAP);
                String js_out = new String(output);
                this.callbackContext.success(js_out);
                js_out = null;
                output = null;
                code = null;
            }
        } catch (Exception e) {
            this.failPicture("Error compressing image.");
        }
        jpeg_data = null;
    }

    public String getBase64Picture(Bitmap bitmap) {
        ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();
        try {
            if (bitmap.compress(CompressFormat.JPEG, mQuality, jpeg_data)) {
                byte[] code = jpeg_data.toByteArray();
                byte[] output = Base64.encode(code, Base64.NO_WRAP);
                String js_out = new String(output);
                output = null;
                code = null;
                jpeg_data = null;
                return js_out;
            }
        } catch (Exception e) {
            jpeg_data = null;
            return "";
        }
        return "";
    }

    /**
     * Send error message to JavaScript.
     *
     * @param err
     */
    public void failPicture(String err) {
        this.callbackContext.error(err);
    }

    private void scanForGallery(Uri newImage) {
        this.scanMe = newImage;
        if(this.conn != null) {
            this.conn.disconnect();
        }
        this.conn = new MediaScannerConnection(this.cordova.getActivity().getApplicationContext(), this);
        conn.connect();
    }

    public void onMediaScannerConnected() {
        try{
            this.conn.scanFile(this.scanMe.toString(), "image/*");
        } catch (java.lang.IllegalStateException e){
            LOG.e(LOG_TAG, "Can't scan file in MediaScanner after taking picture");
        }

    }

    public void onScanCompleted(String path, Uri uri) {
        this.conn.disconnect();
    }
}
