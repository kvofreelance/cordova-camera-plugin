package org.apache.cordova.camera;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Created by adventis on 3/25/15.
 */
public class AlbumActivity extends Activity {
    private ArrayList<String> imageUrls;
    private ArrayList<String> imageUrlsID;
    private ArrayList<Long> imagesID;
    private ArrayList<String> albumPath;
    private ArrayList<String> albumName;
    private Bitmap[] thumbnails = null;
    //private DisplayImageOptions options;
    private ImageAdapter imageAdapter;
    private int maxSelectedLimit = 0;
    private GridView gv = null;
    private ProgressDialog pd = null;

    TreeMap<String, String[]> albumList;

    private String LOG_TAG = "PluginTag";

    public static String ALBUM_LIST = "albumList.bin";
    public static String ALBUM_LIST_PICTURE = "albumPic.bin";

    public static void writeAlbumPictureInfo(Object map, Context ctx, String filename) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(ctx.getFilesDir(), filename))); //Select where you wish to save the file...
            oos.writeObject(map); // write the class as an 'object'
            oos.flush(); // flush the stream to insure all of the information was written to 'save.bin'
            oos.close();// close the stream
        } catch(Exception ex) {
            //Log.v(LOG_TAG,ex.getMessage());
            Log.d("PluginTag", filename + " write: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static Object getAlbumPictureInfo(Context ctx, String filename) {
        Object map = null;

        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(new File(ctx.getFilesDir(), filename)));
            Object o = ois.readObject();
            map = o;
        } catch(Exception ex) {
            //Log.v(LOG_TAG,ex.getMessage());
            Log.d("PluginTag", filename + " read: " + ex.getMessage());
            ex.printStackTrace();
        }

        return map;
    }

    public static void scanAlbumsInfo(Context ctx) {

        TreeMap<String, String[]> albumList = (TreeMap<String, String[]>)getAlbumPictureInfo(ctx, AlbumActivity.ALBUM_LIST);
        if(albumList == null) {
            albumList = new TreeMap<String, String[]>();
        }
        TreeMap<Integer, String> albumPictureList = (TreeMap<Integer, String>)getAlbumPictureInfo(ctx, AlbumActivity.ALBUM_LIST_PICTURE);
        if(albumPictureList == null) {
            albumPictureList = new TreeMap<Integer, String>();
        }

        String orderBy = MediaStore.Images.Media._ID + " DESC";
        String[] filePathColumn = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA };
        Cursor images = ctx.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, filePathColumn, null,null, orderBy);

        for (int i = 0; i < images.getCount(); i++) {
            images.moveToPosition(i);

            String path = images.getString(1);
            int imageId = images.getInt(0);

            AlbumInfo item = AlbumActivity.getAlbumInfoFromFile(path, imageId);
            if(albumList.get(item.path) == null) {
                if(i == 0 && albumList.size() == 0) {
                    albumList.put("all", new String[] { "/", "All", item.image, item.id+""});
                }
                Log.d("PluginTag", "Path: "+item.path+"; Image: "+item.name+"; ID: "+item.image + " ImageId: "+ item.id+"");
                albumList.put(item.path, new String[] {item.path, item.name, item.image, item.id+""});
            }



            //albumPictureList.put(Integer.parseInt(imageId), item.path);
            albumPictureList.put(imageId, item.path);
        }

        AlbumActivity.writeAlbumPictureInfo(albumList, ctx, AlbumActivity.ALBUM_LIST);
        AlbumActivity.writeAlbumPictureInfo(albumPictureList, ctx, AlbumActivity.ALBUM_LIST_PICTURE);
    }

    public static AlbumInfo getAlbumInfoFromFile(String path, long id) {

        File file = new File(path);
        String folderPath = file.getParent();

        //Log.d("PluginTag", "Folder path "+folderPath);

        File folder = new File(folderPath);
        String folderName = folder.getName();

        //Log.d("PluginTag", "Folder name "+folderName);

        File staticFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/Camera");

        if(folder.getPath().contains(staticFolder.getPath())) {
            folderPath = staticFolder.getPath();
            folderName = staticFolder.getName();
        }

        return new AlbumInfo(folderPath, folderName, path, id);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RelativeLayout rl = new RelativeLayout(this.getBaseContext());
        rl.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));

        gv = new GridView(this.getBaseContext());
        LinearLayout.LayoutParams gridLP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT);
        gv.setGravity(Gravity.CENTER_HORIZONTAL);
        gv.setGravity(Gravity.CENTER);
        gv.setNumColumns(3);
        gv.setVerticalSpacing(1);
        gv.setHorizontalSpacing(1);
        gv.setLayoutParams(gridLP);
        gv.setOnScrollListener(new AbsListView.OnScrollListener(){
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // TODO Auto-generated method stub
            }
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                // TODO Auto-generated method stub
                try {
                    if(scrollState == 0) {
                        //Stopped
                        //Log.d("itn", "START");
                        runImage();
                        //visibleSetImages.start();
                    } else {
                        //Log.d("itn", "STOP");
                        //visibleSetImages.stop();
                    }
                } catch(Exception e) {

                }
            }
        });

        Button select = new Button(this.getBaseContext());
        select.setMinWidth(200);
        select.setText("Select");
        RelativeLayout.LayoutParams selectLP = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        selectLP.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        selectLP.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        selectLP.addRule(RelativeLayout.CENTER_HORIZONTAL);
        select.setLayoutParams(selectLP);

        Button cancel = new Button(this.getBaseContext());
        cancel.setMinWidth(200);
        cancel.setText("Cancel");
        RelativeLayout.LayoutParams cancelLP = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLP.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        cancelLP.addRule(RelativeLayout.CENTER_HORIZONTAL);
        cancel.setLayoutParams(cancelLP);



        rl.addView(gv);
        rl.addView(cancel);
        //rl.addView(select);

        setContentView(rl.getRootView());

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    //ImageLoader.getInstance().destroy();
                    Intent data = new Intent();
                    setResult(RESULT_CANCELED,data);
                    finish();
                }catch(Exception e) {

                }
            }
        });

        Intent intent = getIntent();
        this.maxSelectedLimit = intent.getIntExtra("maxSelectedLimit", -1);

        final String orderBy = MediaStore.Images.Thumbnails.IMAGE_ID + " DESC";
        final String orderBy2 = MediaStore.Images.Media._ID + " DESC";

        this.imageUrls = new ArrayList<String>();
        this.imageUrlsID = new ArrayList<String>();
        this.imagesID = new ArrayList<Long>();
        this.albumPath = new ArrayList<String>();
        this.albumName = new ArrayList<String>();

        this.albumList = (TreeMap<String, String[]>)AlbumActivity.getAlbumPictureInfo(getBaseContext(), AlbumActivity.ALBUM_LIST);
        if(this.albumList == null) {
            this.pd = ProgressDialog.show(this, "Scanning...", "Scanning file system...", true, false);
            DownloadTask dt =  new DownloadTask();
            dt.reloadAlbumList = true;
            dt.execute("");
        } else {
            DownloadTask dt =  new DownloadTask();
            dt.reloadAlbumList = false;
            dt.execute("");
            this.displayAlbumList();
        }
        Log.d(LOG_TAG, "After Progress Dialog");
    }

    public void displayAlbumList() {
        Set<String> keys = albumList.keySet();

        List<String> keysList = new ArrayList<String>();
        for (String str : keys) {
            keysList.add(str);
        }

        for(int i = keysList.size()-1; i >= 0; i--) {
            String array[] = albumList.get(keysList.get(i));
            imageUrls.add(array[2]);
            imageUrlsID.add(array[2]);
            imagesID.add(Long.parseLong(array[3]));
            //imagesID.add(Long.parseLong(array[2]));
            albumPath.add(array[0]);
            albumName.add(array[1]);
        }

        this.thumbnails = new Bitmap[keys.size()];

			/*Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565);
			bitmap.eraseColor(Color.BLACK);
			BitmapDrawable d = new BitmapDrawable(getResources(), bitmap);
			Log.d("debug", "Create bitmap");*/
			/*options = new DisplayImageOptions.Builder()
				.showImageOnLoading(d) // resource or drawable
				.showImageForEmptyUri(d) // resource or drawable
				.showImageOnFail(d)
				.cacheInMemory(false)
				.cacheOnDisc(false)
				.imageScaleType(ImageScaleType.IN_SAMPLE_POWER_OF_2)
				.build();*/
        imageAdapter = new ImageAdapter(this, imageUrls, this, maxSelectedLimit);
        gv.setAdapter(imageAdapter);

        runImage();
    }

    private class DownloadTask extends AsyncTask<String, Void, Object> {
        public boolean reloadAlbumList;

        protected Object doInBackground(String... args) {
            Log.d(LOG_TAG, "Background thread starting");


            AlbumActivity.scanAlbumsInfo(getBaseContext());

            //albumList = (TreeMap<String, String[]>)AlbumActivity.getAlbumPictureInfo(getBaseContext(), AlbumActivity.ALBUM_LIST);

            return AlbumActivity.getAlbumPictureInfo(getBaseContext(), AlbumActivity.ALBUM_LIST);
        }

        protected void onPostExecute(Object result) {
            // Pass the result data back to the main activity
            AlbumActivity.this.albumList = (TreeMap<String, String[]>)result;
            if(reloadAlbumList) {
                AlbumActivity.this.displayAlbumList();
            }
            if (AlbumActivity.this.pd != null) {
                AlbumActivity.this.pd.dismiss();
            }
        }
    }

    @Override
    protected void onStop() {
        //imageLoader.stop();
        //ImageLoader.getInstance().destroy();
        super.onStop();
    }

    public void btnChoosePhotosClick(View v){
        //ImageLoader.getInstance().destroy();
        ArrayList<String> selectedItems = imageAdapter.getCheckedItems();
        Log.d(CustomGallery.class.getSimpleName(), "Selected Items: " + selectedItems.toString());
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.d(LOG_TAG, "resultCode: "+resultCode);
        Log.d(LOG_TAG, "requestCode: "+requestCode);
        Log.d(LOG_TAG, "RESULT_OK: "+RESULT_OK);
        Log.d(LOG_TAG, "RESULT_CANCELED: "+RESULT_CANCELED);
        if(resultCode == RESULT_OK) {
            setResult(RESULT_OK,intent);
            finish();
        } /*else {
				//setResult(RESULT_CANCELED,intent);
			}*/
        //ImageLoader.getInstance().destroy();
    }

    public void runImage() {
        runOnUiThread(new Runnable(){
            public void run(){

                //Log.d("itn", "RUN LOAD IMAGE");
                //Log.d("itn", String.valueOf(gv.getFirstVisiblePosition()));
                //Log.d("itn", String.valueOf(gv.getLastVisiblePosition()));
                int start = gv.getFirstVisiblePosition();
                int end = gv.getLastVisiblePosition();
                if(end == -1) {
                    end = 20;
                }

                Log.d("thumbnails", "VisibleStart: " + start + " visibleEnd: " + end );
                for(int visiblePosition = start; visiblePosition <= end; visiblePosition++) {

                    View convertView = gv.getChildAt(visiblePosition);
                    //Log.d("itn", "Get View");

                    int position = visiblePosition;
                    //Log.d("itn", String.valueOf(position));
                    try {
                        if(thumbnails[position] == null) {
                            Log.d("thumbnails", "Thumb on position == null: " + position );
                            String imagePath = "file://"+imageUrls.get(position);
                            File imgFile = new  File(imageUrls.get(position));

                            if(imgFile.exists()){
                                Log.d("thumbnails", "ImageExist: ");

                                //Bitmap myBitmap = CameraLauncher BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                                try {
                                    InputStream inStream = new FileInputStream(imgFile);

                                    //Bitmap myBitmap =  CameraLauncher.decodeSampledBitmapFromResourceMemOpt(inStream, 0, 0);

                                    int orientation = 0;
                                    Matrix matrix = new Matrix();
                                    int index = 0;

                                    //index = convertView.getId();

                                    index = position;

                                    Log.d("thumbnails", "After get index: " );

                                    Bitmap myBitmap = MediaStore.Images.Thumbnails.getThumbnail(
                                            getApplicationContext().getContentResolver(), imagesID.get(position),
                                            MediaStore.Images.Thumbnails.MICRO_KIND, null);

                                    String[] filePathColumn = { MediaStore.Images.Media.ORIENTATION };
                                    String selectedID =  imageUrlsID.get(index);

                                    Log.d("thumbnails", "Cursor get: " );

                                    Cursor images = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, filePathColumn, MediaStore.Images.Media._ID + "=?", new String[] {selectedID}, null);
                                    if (images != null && images.moveToFirst()) {
                                        orientation = images.getInt(0);
                                    }

                                    Log.d("thumbnails", "Orientation get: " );

                                    if (orientation != 0) {
                                        Log.d("debug", "Orientation = "+orientation);
                                        if(orientation == 180) {
                                            matrix.postRotate(180);
                                        } else if(orientation == 90) {
                                            matrix.postRotate(90);
                                        } else if (orientation == -90) {
                                            matrix.postRotate(-90);
                                        }
                                        thumbnails[position] = Bitmap.createBitmap(myBitmap, 0, 0, myBitmap.getWidth(), myBitmap.getHeight(), matrix, true);
                                    } else {
                                        thumbnails[position] = myBitmap;
                                    }

                                    Log.d("thumbnails", "Set bitmap on position: " + position );

                                    images.close();

                                } catch(Exception e) {
                                    e.printStackTrace();
                                }

                            }

                            ViewHolder holder;
                            if(convertView == null) {
                                holder = new ViewHolder();

                                DisplayMetrics dm = new DisplayMetrics();
                                getWindowManager().getDefaultDisplay().getMetrics(dm);
                                int lenght = dm.widthPixels/3;

                                RelativeLayout rl = new RelativeLayout(getBaseContext());
                                AbsListView.LayoutParams rlLP = new AbsListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT);
                                rl.setLayoutParams(rlLP);

                                ImageView iv = new ImageView(getBaseContext());
                                RelativeLayout.LayoutParams ivLP = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                                ivLP.height = lenght;
                                ivLP.addRule(RelativeLayout.CENTER_IN_PARENT);
                                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                iv.setAdjustViewBounds(false);
                                iv.setClickable(true);
                                iv.setLayoutParams(ivLP);


                                TextView tv = new TextView(getBaseContext());
                                RelativeLayout.LayoutParams cbLP = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                                cbLP.addRule(RelativeLayout.CENTER_HORIZONTAL);
                                cbLP.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                                tv.setLayoutParams(cbLP);
                                tv.setGravity(Gravity.CENTER);
                                tv.setBackgroundColor(Color.GRAY);

                                rl.addView(iv);
                                rl.addView(tv);

                                convertView = rl.getRootView();
                                holder.imageview = iv;
                                holder.textview = tv;

                                convertView.setTag(holder);
                            }
                            else {
                                holder = (ViewHolder) convertView.getTag();
                            }
                            convertView.setId(position);
                            holder.textview.setId(position);
                            holder.imageview.setId(position);
                            holder.imageview.setTag(holder.textview);

                            holder.imageview.setImageBitmap(thumbnails[position]);
                            holder.id = position;

                            TextView mTextView = holder.textview;
                            final ImageView imageView = holder.imageview;

                            mTextView.setText(albumName.get(position));
                            imageView.setOnClickListener(new View.OnClickListener() {

                                public void onClick(View v) {
                                    try{
                                        ImageView iv = (ImageView) v;

                                        //ImageLoader.getInstance().destroy();

                                        int id = iv.getId();
                                        String selectedAlbum =  albumPath.get(id);
                                        Intent intent = new Intent(AlbumActivity.this,CustomGallery.class);

                                        intent.putExtra("maxSelectedLimit", maxSelectedLimit);
                                        intent.putExtra("album", selectedAlbum);
                                        AlbumActivity.this.startActivityForResult(intent, 0);
                                    } catch(Exception e) {

                                    }
                                }
                            });

                        }
                        //Log.d("itn", "NOT NULL");
                    } catch (Exception e) {
                        e.printStackTrace();
                        //Log.d("itn", " "+e);
                    }
                }
                //Log.d("itn", "FINISHED");
                imageAdapter.notifyDataSetChanged();

            }
        });
    }

    public class ImageAdapter extends BaseAdapter {

        ArrayList<String> mList;
        LayoutInflater mInflater;
        Context mContext;
        SparseBooleanArray mSparseBooleanArray;
        Activity activity;
        int maxSelectedLimit;


        public ImageAdapter(Context context, ArrayList<String> imageList, Activity activity, int maxSelectedLimit ) {
            // TODO Auto-generated constructor stub
            mContext = context;

            mInflater = LayoutInflater.from(mContext);
            mSparseBooleanArray = new SparseBooleanArray();
            mList = new ArrayList<String>();
            this.mList = imageList;
            this.activity = activity;
            this.maxSelectedLimit = maxSelectedLimit;

        }

        public ArrayList<String> getCheckedItems() {
            ArrayList<String> mTempArry = new ArrayList<String>();

            for(int i=0;i<mList.size();i++) {
                if(mSparseBooleanArray.get(i)) {
                    mTempArry.add(mList.get(i));
                }
            }

            return mTempArry;
        }

        public ArrayList<Integer> getCheckedItemsID() {
            ArrayList<Integer> mTempArry = new ArrayList<Integer>();

            for(int i=0;i<mList.size();i++) {
                if(mSparseBooleanArray.get(i)) {
                    mTempArry.add(i);
                }
            }

            return mTempArry;
        }

        @Override
        public int getCount() {
            return imageUrls.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if(convertView == null) {
                holder = new ViewHolder();

                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(dm);
                int lenght = dm.widthPixels/3;

                RelativeLayout rl = new RelativeLayout(getBaseContext());
                AbsListView.LayoutParams rlLP = new AbsListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT);
                rl.setLayoutParams(rlLP);

                ImageView iv = new ImageView(getBaseContext());
                RelativeLayout.LayoutParams ivLP = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                ivLP.height = lenght;
                ivLP.addRule(RelativeLayout.CENTER_IN_PARENT);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setAdjustViewBounds(false);
                iv.setClickable(true);
                iv.setLayoutParams(ivLP);


                TextView tv = new TextView(getBaseContext());
                RelativeLayout.LayoutParams cbLP = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cbLP.addRule(RelativeLayout.CENTER_HORIZONTAL);
                cbLP.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                tv.setLayoutParams(cbLP);
                tv.setGravity(Gravity.CENTER);
                tv.setBackgroundColor(Color.GRAY);

                rl.addView(iv);
                rl.addView(tv);

                convertView = rl.getRootView();
                holder.imageview = iv;
                holder.textview = tv;

                convertView.setTag(holder);
            }
            else {
                holder = (ViewHolder) convertView.getTag();
            }
            convertView.setId(position);
            holder.textview.setId(position);
            holder.imageview.setId(position);
            holder.imageview.setTag(holder.textview);

            TextView mTextView = holder.textview;
            final ImageView imageView = holder.imageview;

            /*String imagePath = "file://"+imageUrls.get(position);
            File imgFile = new  File(imageUrls.get(position));

            if(imgFile.exists()){
                //Bitmap myBitmap = CameraLauncher BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                try {
                    InputStream inStream = new FileInputStream(imgFile);

                    //Bitmap myBitmap =  CameraLauncher.decodeSampledBitmapFromResourceMemOpt(inStream, 0, 0);

                    int orientation = 0;
                    Matrix matrix = new Matrix();
                    int index = 0;

                    index = convertView.getId();

                    Bitmap myBitmap = MediaStore.Images.Thumbnails.getThumbnail(
                            getApplicationContext().getContentResolver(), imagesID.get(position),
                            MediaStore.Images.Thumbnails.MICRO_KIND, null);

                    String[] filePathColumn = { MediaStore.Images.Media.ORIENTATION };
                    String selectedID =  imageUrlsID.get(index);

                    Cursor images = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, filePathColumn, MediaStore.Images.Media._ID + "=?", new String[] {selectedID}, null);
                    if (images != null && images.moveToFirst()) {
                        orientation = images.getInt(0);
                    }

                    if (orientation != 0) {
                        Log.d("debug", "Orientation = "+orientation);
                        if(orientation == 180) {
                            matrix.postRotate(180);
                        } else if(orientation == 90) {
                            matrix.postRotate(90);
                        } else if (orientation == -90) {
                            matrix.postRotate(-90);
                        }
                        myBitmap = Bitmap.createBitmap(myBitmap, 0, 0, myBitmap.getWidth(), myBitmap.getHeight(), matrix, true);
                        ImageView image = (ImageView)holder.imageview;
                        image.setImageBitmap(myBitmap);
                    } else {
                        ImageView image = (ImageView)holder.imageview;
                        image.setImageBitmap(myBitmap);
                    }
                    images.close();

                } catch(Exception e) {

                }

            }*/
            ImageView image = (ImageView)holder.imageview;
            Log.d("thumbnails", "Try to set thumb on position: " + position + " isNull: " + (thumbnails[position] == null) );
            image.setImageBitmap(thumbnails[position]);

            mTextView.setText(albumName.get(position));
            imageView.setOnClickListener(new View.OnClickListener() {

                public void onClick(View v) {
                    try{
                        ImageView iv = (ImageView) v;

                        //ImageLoader.getInstance().destroy();

                        int id = iv.getId();
                        String selectedAlbum =  albumPath.get(id);
                        Intent intent = new Intent(activity,CustomGallery.class);

                        intent.putExtra("maxSelectedLimit", maxSelectedLimit);
                        intent.putExtra("album", selectedAlbum);
                        activity.startActivityForResult(intent, 0);
                    } catch(Exception e) {

                    }
                }
            });

            return convertView;
        }
    }
    class ViewHolder {
        ImageView imageview;
        TextView textview;
        int id;
    }
}
