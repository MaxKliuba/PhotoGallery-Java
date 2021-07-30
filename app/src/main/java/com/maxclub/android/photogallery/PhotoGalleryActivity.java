package com.maxclub.android.photogallery;

import androidx.fragment.app.Fragment;

public class PhotoGalleryActivity extends SingleActivityFragment {

    @Override
    protected Fragment createFragment() {
        return PhotoGalleryFragment.newInstance();
    }
}