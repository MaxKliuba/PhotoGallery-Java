package com.maxclub.android.photogallery;

import android.content.Context;
import android.content.Intent;

import androidx.fragment.app.Fragment;

public class PhotoGalleryActivity extends SingleActivityFragment {

    public static Intent newIntent(Context context) {
        return new Intent(context, PhotoGalleryActivity.class);
    }

    @Override
    protected Fragment createFragment() {
        return PhotoGalleryFragment.newInstance();
    }
}