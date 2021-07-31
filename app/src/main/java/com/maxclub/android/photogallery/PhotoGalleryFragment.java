package com.maxclub.android.photogallery;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends Fragment {

    private static final int MIN_ITEM_WIDTH_PX = 480;
    private static final String TAG = "PhotoGalleryFragment";

    private RecyclerView mPhotoRecyclerView;
    private GridLayoutManager mLayoutManager;
    private List<GalleryItem> mItems = new ArrayList<>();
    private boolean mIsFetching = false;
    private int mCurrentPage = 1;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        fetchItemsAsync();

        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloaderListener(new ThumbnailDownloader.ThumbnailDownloaderListener<PhotoHolder>() {
            @Override
            public void onThumbnailDownloaded(PhotoHolder target, Bitmap bitmap) {
                Drawable drawable = new BitmapDrawable(getResources(), bitmap);
                target.bindDrawable(drawable);
            }
        });
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }

    @Nullable
    @org.jetbrains.annotations.Nullable
    @Override
    public View onCreateView(@NonNull @NotNull LayoutInflater inflater,
                             @Nullable @org.jetbrains.annotations.Nullable ViewGroup container,
                             @Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.activity_photo_gallery, container, false);

        mPhotoRecyclerView = (RecyclerView) view.findViewById(R.id.photo_recycler_view);
        mLayoutManager = new GridLayoutManager(getActivity(), 1);
        mPhotoRecyclerView.setLayoutManager(mLayoutManager);
        mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int calculatedSpanCount = mPhotoRecyclerView.getWidth() / MIN_ITEM_WIDTH_PX;
                mLayoutManager.setSpanCount(Math.max(calculatedSpanCount, 1));
            }
        });
        mPhotoRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull @NotNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (!recyclerView.canScrollVertically(1) && !mIsFetching) {
                    fetchItemsAsync();
                }
            }
        });

        setupAdapter();

        swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mCurrentPage = 1;
                mItems.clear();
                fetchItemsAsync();
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    private void setupAdapter() {
        if (isAdded()) {
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private void fetchItemsAsync() {
        mIsFetching = true;
        new FetchItemsTask().execute(mCurrentPage++);
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);

            mItemImageView = (ImageView) itemView.findViewById(R.id.item_image_view);
        }

        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {
        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @NonNull
        @NotNull
        @Override
        public PhotoHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, parent, false);

            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull @NotNull PhotoGalleryFragment.PhotoHolder holder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            Drawable placeholder = getResources().getDrawable(R.drawable.placeholder);
            holder.bindDrawable(placeholder);
            mThumbnailDownloader.queueThumbnail(holder, galleryItem.getUrl());
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    private class FetchItemsTask extends AsyncTask<Integer, Void, List<GalleryItem>> {
        @Override
        protected List<GalleryItem> doInBackground(Integer... pages) {
            return new FlickrFetcher().fetchItemsByPage(pages[0]);
        }

        @Override
        protected void onPostExecute(List<GalleryItem> galleryItems) {
            int visibleItemCount = mLayoutManager.findLastVisibleItemPosition()
                    - mLayoutManager.findFirstCompletelyVisibleItemPosition();
            int position = mItems.size() - visibleItemCount;

            mItems.addAll(galleryItems);
            setupAdapter();
            mPhotoRecyclerView.scrollToPosition(position);

            mIsFetching = false;
            swipeRefreshLayout.setRefreshing(false);
        }
    }
}