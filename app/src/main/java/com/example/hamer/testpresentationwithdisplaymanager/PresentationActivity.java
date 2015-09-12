package com.example.hamer.testpresentationwithdisplaymanager;


import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Presentation;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class PresentationActivity extends Activity implements CompoundButton.OnCheckedChangeListener, View.OnClickListener {

    private static final String TAG = "PresentationActivity";
    private static final String PRESENTATION_KEY = "presentation";


    // The content that we want to show on the presentation
    private static final int[] PHOTOS = new int[] {
            R.drawable.frantic,
            R.drawable.photo1, R.drawable.photo2, R.drawable.photo3,
            R.drawable.photo4, R.drawable.photo5, R.drawable.photo6,
            R.drawable.sample_4,
    };
    private DisplayManager mDisplayManager;
    private DisplayListAdapter mDisplayListAdapter;
    private CheckBox mShowAllDisplayCheckbox;
    private ListView mListView;
    private int mNextImageNumber;

    private SparseArray<PresentationContents> mSavedPresentationContents;

    private final SparseArray<DemoPresentation> mActivePresentations = new SparseArray<DemoPresentation>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Restore saved instance state.
        if (savedInstanceState != null) {
            mSavedPresentationContents = savedInstanceState.getSparseParcelableArray(PRESENTATION_KEY);
        } else {
            mSavedPresentationContents = new SparseArray<PresentationContents>();
        }

        // Get the display manager service
        mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);

        setContentView(R.layout.presentation_activity);

        mShowAllDisplayCheckbox = (CheckBox) findViewById(R.id.show_all_displays);
        mShowAllDisplayCheckbox.setOnCheckedChangeListener(this);
//        mShowAllDisplayCheckbox.setChecked(true);

        // set up the list of diplays
        mDisplayListAdapter = new DisplayListAdapter(this);
        mListView = (ListView) findViewById(R.id.display_list);
        mListView.setAdapter(mDisplayListAdapter);

        ////////////////////////////////////////////////////////////


    }

    @Override
    protected void onResume() {
        super.onResume();

        mDisplayListAdapter.updateContents();

        // restore presentations from before the activity was paused
        final int numDisplays = mDisplayListAdapter.getCount();
        for (int i = 0; i < numDisplays; i++ ) {
            final Display display = mDisplayListAdapter.getItem(i);
            final PresentationContents contents = mSavedPresentationContents.get(display.getDisplayId());
            if (contents != null) {
                showPresentation(display, contents);
            }
        }
        mSavedPresentationContents.clear();

        // register to receive events from the display manager
        mDisplayManager.registerDisplayListener(mDisplayListener, null);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // unregister from display manager
        mDisplayManager.unregisterDisplayListener(mDisplayListener);

        // dismiss all of our presentations but remember their contents
        Log.d(TAG, "Activity is being paused. Dismissing all active presentations");
        for (int i = 0; i < mActivePresentations.size(); i++) {
            DemoPresentation presentation = mActivePresentations.valueAt(i);
            int displayId = mActivePresentations.keyAt(i);
            mSavedPresentationContents.put(displayId, presentation.mContents);
            presentation.dismiss();
        }
        mActivePresentations.clear();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSparseParcelableArray(PRESENTATION_KEY, mSavedPresentationContents);
    }

    private void showPresentation(Display display, PresentationContents contents) {
        final int displayId = display.getDisplayId();

        if (mActivePresentations.get(displayId) != null) {
            return;
        }

        Log.d(TAG, "Showing presentation photo #" + contents.photo + " on display #" + displayId + ".");

        DemoPresentation presentation = new DemoPresentation(this, display, contents);
        presentation.show();
        presentation.setOnDismissListener(mOnDismissListener);
        mActivePresentations.put(displayId, presentation);
    }

    private void hidePresentation(Display display) {
        final int displayId = display.getDisplayId();
        DemoPresentation presentation = mActivePresentations.get(displayId);
        if (presentation == null) {
            return;
        }

        Log.d(TAG, "Dismissing presentation on displa #" + displayId + ".");

        presentation.dismiss();
        mActivePresentations.delete(displayId);
    }

    private int getNextPhoto() {
        final int photo = mNextImageNumber;
        mNextImageNumber = (mNextImageNumber + 1) % PHOTOS.length;
        return photo;
    }

    /**
     * Called when the show all displays checkbox is toggled or when
     * an item in the list of displays is checked or unchecked
     * @param buttonView
     * @param isChecked
     */
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == mShowAllDisplayCheckbox) {
            mDisplayListAdapter.updateContents();
        } else {
            // display item checkbox was toggled
            final Display display = (Display) buttonView.getTag();
            if (isChecked) {
                PresentationContents contents = new PresentationContents(getNextPhoto());
                showPresentation(display, contents);
            } else {
                hidePresentation(display);
            }
        }
    }

    /**
     * called when the info button next to a display is clicked to show information
     * about the display
     */
    @Override
    public void onClick(View v) {
        Context context = v.getContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final Display display = (Display) v.getTag();
        Resources r = context.getResources();
        AlertDialog alert = builder.setTitle(r.getString(R.string.presentation_alert_info_text, display.getDisplayId()))
                                    .setMessage(display.toString())
                                    .setNeutralButton(R.string.presentation_alert_dismiss_text,
                                            new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    dialog.dismiss();
                                                }
                                            })
                                    .create();
        alert.show();

    }

    /**
     * Listens for displays to be added, changed or removed
     *
     * Note that we don't bother dismissing the presentation when a display is removed, although we could.
     * The presentation API takes care of doing that automatically for us.
     */
    private final DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {

        @Override
        public void onDisplayAdded(int displayId) {
            Log.d(TAG, "Display #" + displayId + " added.");
            mDisplayListAdapter.updateContents();
        }

        @Override
        public void onDisplayChanged(int displayId) {
            Log.d(TAG, "Display #" + displayId + " changed");
            mDisplayListAdapter.updateContents();
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            Log.d(TAG, "Display #" + displayId + " removed.");
            mDisplayListAdapter.updateContents();
        }
    };


    /**
     * Listens for when presentations are dismissed
     */
    private final DialogInterface.OnDismissListener mOnDismissListener = new DialogInterface.OnDismissListener() {

        @Override
        public void onDismiss(DialogInterface dialog) {
            DemoPresentation presentation = (DemoPresentation) dialog;
            int displayId = presentation.getDisplay().getDisplayId();
            Log.d(TAG, "Presentation on display #" + displayId + " was dismissed.");
            mActivePresentations.delete(displayId);
            mDisplayListAdapter.notifyDataSetChanged();
        }
    };

    /**
     * List Adapter.
     * Show infomration about all displays
     */
    public class DisplayListAdapter extends ArrayAdapter<Display> {

        final Context mContext;

        public DisplayListAdapter(Context context) {
            super(context, R.layout.presentation_list_item);
            mContext = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View v;
            if (convertView == null) {
                v = ((Activity) mContext).getLayoutInflater().inflate(R.layout.presentation_list_item, null);
            } else {
                v = convertView;
            }

            final Display display = getItem(position);
            final int displayId = display.getDisplayId();

            CheckBox cb = (CheckBox)v.findViewById(R.id.checkbox_presentation);
            cb.setTag(display);
            cb.setOnCheckedChangeListener(PresentationActivity.this);
            cb.setChecked(mActivePresentations.indexOfKey(displayId) >= 0 ||
                    mSavedPresentationContents.indexOfKey(displayId) >= 0);
            TextView tv = (TextView)v.findViewById(R.id.display_id);
            tv.setText(v.getContext().getResources().getString(R.string.presentation_display_id_text,
                                displayId, display.getName()));

            Button b = (Button) v.findViewById(R.id.info);
            b.setTag(display);
            b.setOnClickListener(PresentationActivity.this);

            return v;

        }

        public void updateContents() {
            clear();

            String displayCategory = getDisplayCategory();
            Display[] displays = mDisplayManager.getDisplays(displayCategory);
            addAll(displays);

            Log.d(TAG, "There are currently " + displays.length + " displays connected.");
            for (Display display: displays) {
                Log.d(TAG, " " + display);
            }
        }

        private String getDisplayCategory() {
            return mShowAllDisplayCheckbox.isChecked() ? null : DisplayManager.DISPLAY_CATEGORY_PRESENTATION;
        }
    }


    /**
     * The presentation to show on the secondary display.
     *
     * Note that the presentation display may have different metrics from the display on which
     * the main activity is showing so we must be careful to use the presentation's own context
     * whenever we load resources.
     */
    private final class DemoPresentation extends Presentation {

        final PresentationContents mContents;

        public DemoPresentation(Context context, Display display, PresentationContents contents) {
            super(context, display);
            mContents = contents;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            // Be sure to call the super class
            super.onCreate(savedInstanceState);

            // Get the resources for context of the presentation
            // Notice that we are getting the resources from the context of the presentation
            Resources r = getContext().getResources();

            // Inflate the layout
            setContentView(R.layout.presentation_content);

            final Display display = getDisplay();
            final int displayId = display.getDisplayId();
            final int photo = mContents.photo;

            // show a caption to describe what's going on
            TextView text = (TextView) findViewById(R.id.text);
            text.setText(r.getString(R.string.presentation_photo_text, photo, displayId, display.getName()));

            // show a image for visual interest
            ImageView image = (ImageView) findViewById(R.id.image);
            image.setImageDrawable(r.getDrawable(PHOTOS[photo]));

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);

            // show the background to a random gradient
            Point p = new Point();
            getDisplay().getSize(p);
            drawable.setGradientRadius(Math.max(p.x, p.y)/2);
            drawable.setColors(mContents.colors);
            findViewById(android.R.id.content).setBackground(drawable);
        }
    }

    private final static class PresentationContents implements Parcelable {

        final int photo;
        final int[] colors;

        public static final Creator<PresentationContents> CREATOR = new Creator<PresentationContents>() {

            @Override
            public PresentationContents createFromParcel(Parcel source) {
                return new PresentationContents(source);
            }

            @Override
            public PresentationContents[] newArray(int size) {
                return new PresentationContents[size];
            }
        };

        public PresentationContents(int photo) {
            this.photo = photo;
            colors = new int[] {
                    ((int) (Math.random() * Integer.MAX_VALUE)) | 0xFF000000,
                    ((int) (Math.random() * Integer.MAX_VALUE)) | 0xFF000000 };

        }

        private PresentationContents(Parcel in) {
            photo = in.readInt();
            colors = new int[] {
                    in.readInt(), in.readInt()
            };
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(photo);
            dest.writeInt(colors[0]);
            dest.writeInt(colors[1]);
        }
    }

}
