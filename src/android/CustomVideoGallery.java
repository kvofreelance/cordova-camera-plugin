package org.apache.cordova.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.kvofreelance.camera.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

@SuppressLint("NewApi")
public class CustomVideoGallery extends Activity {
	private int count = 0;
	private Bitmap[] thumbnails = null;
	private boolean[] thumbnailsselection = null;
	private String[] arrPath = null;
	private ImageAdapter imageAdapter = null;
	private int maxSelectedLimit = 0;
	private Cursor imagecursor = null;
	private Cursor imagecursorPost = null;
	private GridView gv = null;
	Thread visibleSetImages = null;
    String album = "";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.count = 0;
		this.thumbnails = null;
		this.thumbnailsselection = null;
		this.arrPath = null;
		this.imageAdapter = null;
		this.maxSelectedLimit = 0;
		this.imagecursor = null;
		this.imagecursorPost = null;

		//Create UI
		RelativeLayout rl = new RelativeLayout(this.getBaseContext());
		rl.setLayoutParams(new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

		Button select = new Button(this.getBaseContext());
		select.setMinWidth(200);
		select.setText("Select");
		RelativeLayout.LayoutParams selectLP = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		selectLP.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		selectLP.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		selectLP.addRule(RelativeLayout.CENTER_HORIZONTAL);
		select.setLayoutParams(selectLP);

		Button cancel = new Button(this.getBaseContext());
		cancel.setMinWidth(200);
		cancel.setText("Cancel");
		RelativeLayout.LayoutParams cancelLP = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		cancelLP.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		cancelLP.addRule(RelativeLayout.CENTER_HORIZONTAL);
		cancel.setLayoutParams(cancelLP);

		gv = new GridView(this.getBaseContext());
		LayoutParams gridLP = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		gv.setGravity(Gravity.CENTER_HORIZONTAL);
		gv.setNumColumns(3);
		gv.setVerticalSpacing(1);
		gv.setHorizontalSpacing(1);
		gv.setLayoutParams(gridLP);

		rl.addView(gv);
		rl.addView(cancel);
		//rl.addView(select);

		setContentView(rl.getRootView());

		gv.setOnScrollListener(new OnScrollListener(){
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

		Intent intent = getIntent();
		this.maxSelectedLimit = intent.getIntExtra("maxSelectedLimit", -1);
        this.album = intent.getStringExtra("album");
        final String[] columns = { MediaStore.Video.Media.DATA, MediaStore.Video.Media._ID };
		final String orderBy = MediaStore.Video.Media.DATE_TAKEN + " DESC";
        String condition = null;
        if(this.album != null && !this.album.isEmpty()) {
            condition = MediaStore.Video.Media.DATA + " like '%" + album + "%'";
            Log.d("FOLDER_NAME", condition);
        }
		/*imagecursor = managedQuery(
				MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null,
				null, orderBy);*/

		imagecursorPost = managedQuery(
				MediaStore.Video.Media.EXTERNAL_CONTENT_URI, columns, condition,
				null, orderBy);

		int image_column_index = imagecursorPost.getColumnIndex(MediaStore.Video.Media._ID);
		this.count = imagecursorPost.getCount();
		this.thumbnails = new Bitmap[this.count];
		this.arrPath = new String[this.count];
		this.thumbnailsselection = new boolean[this.count];
		Arrays.fill(this.thumbnailsselection, false);

		imageAdapter = new ImageAdapter();
		gv.setAdapter(imageAdapter);
		//imagecursor.close();

		select.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				// TODO Auto-generated method stub
				try {
				    final int len = thumbnailsselection.length;
				    int cnt = 0;
				    String selectImages = "";
				    ArrayList<Uri> uris = new ArrayList<Uri>();

            	    for (int i=0; i<len; i++)
            	    {
            		    if (thumbnailsselection[i]){
            			    cnt++;
            			    File fileIn = new File(arrPath[i]);
               			    Uri u = Uri.fromFile(fileIn);
                		    uris.add(u);
            		    }
            	    }

				    disableAll();

				    Intent data = new Intent();
				    data.putParcelableArrayListExtra("selectedImagesArray", uris);

				    //data.putExtra("selectedImagesArray",selectImages);
				    setResult(RESULT_OK,data);
				    //imagecursor.close();
				    imagecursorPost.close();
				    finish();
				}catch(Exception e) {

				}
			}
		});

		cancel.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				try {
				disableAll();
				Intent data = new Intent();
				setResult(RESULT_CANCELED,data);
				//imagecursor.close();
				imagecursorPost.close();
				finish();
				}catch(Exception e) {

				}
			}
		});

		runImage();
	}

    public void sentVideoFile(int position) {
        ArrayList<Uri> uris = new ArrayList<Uri>();
        File fileIn = new File(arrPath[position]);
        Uri u = Uri.fromFile(fileIn);
        uris.add(u);

        Intent data = new Intent();
        data.putParcelableArrayListExtra("selectedVideosArray", uris);
        setResult(RESULT_OK,data);
        finish();
    }

	public void disableAll() {
		for (int i=0; i<thumbnailsselection.length; i++)
    	{
    		thumbnailsselection[i] = false;
    	}
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
	            	for(int visiblePosition = start; visiblePosition <= end; visiblePosition++) {

	            		View view = gv.getChildAt(visiblePosition);
	            		//Log.d("itn", "Get View");

	            		int position = visiblePosition;
	            		//Log.d("itn", String.valueOf(position));
	            		try {
	            		if(thumbnails[position] == null) {
	            			//Log.d("itn", "NULL");
		            		imagecursorPost.moveToPosition(position);
		        			int id = imagecursorPost.getInt(imagecursorPost.getColumnIndex(MediaStore.Video.Media._ID));
		        			int orientation = 0;//imagecursorPost.getInt(imagecursorPost.getColumnIndex(MediaStore.Images.Media.ORIENTATION));
		        			int dataColumnIndex = imagecursorPost.getColumnIndex(MediaStore.Video.Media.DATA);
		        			Bitmap image = MediaStore.Video.Thumbnails.getThumbnail(
		        					getApplicationContext().getContentResolver(), id,
		        					MediaStore.Video.Thumbnails.MICRO_KIND, null);

                            if(image == null) {
                                String path = imagecursorPost.getString(dataColumnIndex);
                                image = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
                            }

                            Matrix matrix = new Matrix();
			        			if (orientation != 0) {
				        			if(orientation == 180) {
				        				matrix.postRotate(180);
				        			} else if(orientation == 90) {
				        				matrix.postRotate(90);
				        			} else if (orientation == -90) {
				        				matrix.postRotate(-90);
				        			}
				        			thumbnails[position] = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);
			        			} else {
				        			thumbnails[position] = image;
			        			}

			        			//Log.d("debug", String.valueOf(position));
		        			arrPath[position]= imagecursorPost.getString(dataColumnIndex);
		        			ViewHolder holder;
		        			if (view == null) {
		        				holder = new ViewHolder();

		        				/*Display display = getWindowManager().getDefaultDisplay();
		        				Point size = new Point();
		        				display.getSize(size);
		        				int lenght = size.x/3;*/

		        				DisplayMetrics dm = new DisplayMetrics();
		        			    getWindowManager().getDefaultDisplay().getMetrics(dm);
		        			    int lenght = dm.widthPixels/3;

		        				RelativeLayout rl = new RelativeLayout(getBaseContext());
		        				AbsListView.LayoutParams rlLP = new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		        				rl.setLayoutParams(rlLP);

		        				ImageView iv = new ImageView(getBaseContext());
		        				RelativeLayout.LayoutParams ivLP = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		        				ivLP.height = lenght;
		        				ivLP.addRule(RelativeLayout.CENTER_IN_PARENT);
		        				iv.setLayoutParams(ivLP);

		        				CheckBox cb = new CheckBox(getBaseContext());
		        				RelativeLayout.LayoutParams cbLP = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		        				cbLP.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		        				cbLP.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		        				cb.setLayoutParams(cbLP);
		        				cb.setGravity(Gravity.TOP);
		        				cb.setBackgroundColor(Color.TRANSPARENT);
		        				//cb.setAlpha((float) 0.5);
		        				AlphaAnimation alpha = new AlphaAnimation(0.5F, 0.5F);
		        				alpha.setDuration(0);
		        				alpha.setFillAfter(true);
		        				cb.startAnimation(alpha);

		        				rl.addView(iv);
		        				rl.addView(cb);

		        				view = rl.getRootView();
		        				holder.imageview = iv;
		        				holder.checkbox = cb;

		        				view.setTag(holder);
		        			}
		        			else {
		        				holder = (ViewHolder) view.getTag();
		        			}
		        			view.setId(position);
		        			holder.checkbox.setId(position);
		        			holder.imageview.setId(position);
		        			holder.imageview.setTag(holder.checkbox);

		        			holder.imageview.setImageBitmap(thumbnails[position]);
		        			holder.checkbox.setChecked(thumbnailsselection[position]);
		        			holder.id = position;

                            holder.checkbox.setVisibility(View.INVISIBLE);
                            holder.checkbox.setEnabled(false);

		        			/*holder.checkbox.setOnClickListener(new OnClickListener() {

		        				public void onClick(View v) {
		        					CheckBox cb = (CheckBox) v;
		        					int id = cb.getId();
		        					if (thumbnailsselection[id]){
		        						cb.setChecked(false);
		        						thumbnailsselection[id] = false;
		        					} else {
		        						int cnt = getCountSelectedImages();
		        						if(maxSelectedLimit != -1 & cnt >= maxSelectedLimit ) {
		        							Toast.makeText(getApplicationContext(),
		        									"You have exceeded the limit of the photo selection. Your limit is " + cnt,
		        									Toast.LENGTH_LONG).show();
		        							cb.setChecked(false);
		        							thumbnailsselection[id] = false;
		        						} else {
		        							cb.setChecked(true);
		        							thumbnailsselection[id] = true;
		        						}
		        					}
		        				}
		        			});*/
		        			holder.imageview.setOnClickListener(new OnClickListener() {

		        				public void onClick(View v) {
		        					// TODO Auto-generated method stub
		        					ImageView iv = (ImageView) v;
		        					CheckBox cb = (CheckBox) iv.getTag();

		        					//int id = cb.getId();
		        					int id = iv.getId();
		        					/*if (thumbnailsselection[id]){
		        						cb.setChecked(false);
		        						thumbnailsselection[id] = false;
		        					} else {
		        						int cnt = getCountSelectedImages();
		        						if(maxSelectedLimit != -1 & cnt >= maxSelectedLimit ) {
		        							Toast.makeText(getApplicationContext(),
		        									"You have exceeded the limit of the photo selection. Your limit is " + cnt,
		        									Toast.LENGTH_LONG).show();
		        							cb.setChecked(false);
		        							thumbnailsselection[id] = false;
		        						} else {
		        							cb.setChecked(true);
		        							thumbnailsselection[id] = true;
		        						}
		        					}*/

                                    thumbnailsselection[id] = true;

                                    sentVideoFile(id);
                                }
		        			});
		            		//Log.d("itn", "Get ViewHolder");

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

	public int getCountSelectedImages() {
		final int len = thumbnailsselection.length;
		int cnt = 0;
		for (int i =0; i<len; i++)
		{
			if (thumbnailsselection[i]){
				cnt++;
			}
		}
		return cnt;
	}

	public class ImageAdapter extends BaseAdapter {
		private LayoutInflater mInflater;

		public ImageAdapter() {
			mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		public int getCount() {
			return count;
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		public int getCountSelectedImages() {
			final int len = thumbnailsselection.length;
			int cnt = 0;
			for (int i =0; i<len; i++)
			{
				if (thumbnailsselection[i]){
					cnt++;
				}
			}
			return cnt;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				holder = new ViewHolder();

				DisplayMetrics dm = new DisplayMetrics();
			    getWindowManager().getDefaultDisplay().getMetrics(dm);
			    int lenght = dm.widthPixels/3;

				RelativeLayout rl = new RelativeLayout(getBaseContext());
				AbsListView.LayoutParams rlLP = new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
				rl.setLayoutParams(rlLP);

				ImageView iv = new ImageView(getBaseContext());
				RelativeLayout.LayoutParams ivLP = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
				ivLP.height = lenght;
				ivLP.addRule(RelativeLayout.CENTER_IN_PARENT);
				iv.setLayoutParams(ivLP);

				CheckBox cb = new CheckBox(getBaseContext());
				RelativeLayout.LayoutParams cbLP = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				cbLP.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				cbLP.addRule(RelativeLayout.ALIGN_PARENT_TOP);
				cb.setLayoutParams(cbLP);
				cb.setGravity(Gravity.TOP);
                //cb.setBackground(getResources().getDrawable( R.drawable.video_icon ));
                cb.setBackgroundColor(Color.TRANSPARENT);

                ImageView ivIcon = new ImageView(getBaseContext());
                RelativeLayout.LayoutParams ivIconLP = new RelativeLayout.LayoutParams(50, 50);
                ivIconLP.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                ivIconLP.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                ivIcon.setLayoutParams(ivIconLP);
                ivIcon.setScaleType(ImageView.ScaleType.FIT_XY);
                ivIcon.setImageResource(R.drawable.video_icon);

                AlphaAnimation alpha = new AlphaAnimation(0.5F, 0.5F);
				alpha.setDuration(0);
				alpha.setFillAfter(true);
				cb.startAnimation(alpha);
				
				rl.addView(iv);
				rl.addView(cb);
                rl.addView(ivIcon);

				convertView = rl.getRootView();
				holder.imageview = iv;
				holder.checkbox = cb;

				convertView.setTag(holder);
			}
			else {
				holder = (ViewHolder) convertView.getTag();
			}

			convertView.setId(position);
			holder.checkbox.setId(position);
			holder.imageview.setId(position);
			holder.imageview.setTag(holder.checkbox);

            holder.checkbox.setVisibility(View.INVISIBLE);
            holder.checkbox.setEnabled(false);
			/*holder.checkbox.setOnClickListener(new OnClickListener() {
				
				public void onClick(View v) {
					CheckBox cb = (CheckBox) v;
					int id = cb.getId();
					if (thumbnailsselection[id]){
						cb.setChecked(false);
						thumbnailsselection[id] = false;
					} else {
						int cnt = getCountSelectedImages();
						if(maxSelectedLimit != -1 & cnt >= maxSelectedLimit ) {
							Toast.makeText(getApplicationContext(),
									"You have exceeded the limit of the photo selection. Your limit is " + cnt,
									Toast.LENGTH_LONG).show();
							cb.setChecked(false);
							thumbnailsselection[id] = false;
						} else {
							cb.setChecked(true);
							thumbnailsselection[id] = true;
						}
					}
				}
			});*/
			holder.imageview.setOnClickListener(new OnClickListener() {
				
				public void onClick(View v) {
					// TODO Auto-generated method stub
					ImageView iv = (ImageView) v;
					CheckBox cb = (CheckBox) iv.getTag();
					
					//int id = cb.getId();
					int id = iv.getId();
					/*if (thumbnailsselection[id]){
						cb.setChecked(false);
						thumbnailsselection[id] = false;
					} else {
						int cnt = getCountSelectedImages();
						if(maxSelectedLimit != -1 & cnt >= maxSelectedLimit ) {
							Toast.makeText(getApplicationContext(),
									"You have exceeded the limit of the photo selection. Your limit is " + cnt,
									Toast.LENGTH_LONG).show();
							cb.setChecked(false);
							thumbnailsselection[id] = false;
						} else {
							cb.setChecked(true);
							thumbnailsselection[id] = true;
						}
					}*/

                    thumbnailsselection[id] = true;
                    sentVideoFile(id);
				}
			});


			holder.imageview.setImageBitmap(thumbnails[position]);
			holder.checkbox.setChecked(thumbnailsselection[position]);
			holder.id = position;
			return convertView;
		}
	}
	class ViewHolder {
		ImageView imageview;
		CheckBox checkbox;
		int id;
	}
}