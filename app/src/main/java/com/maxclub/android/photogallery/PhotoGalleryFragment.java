package com.maxclub.android.photogallery;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

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

    private static final int MIN_ITEM_WIDTH_DIP = 1200;
    private static final String TAG = "PhotoGalleryFragment";

    private RecyclerView mPhotoRecyclerView;
    private GridLayoutManager mLayoutManager;
    private PhotoAdapter mAdapter;
    private List<GalleryItem> mItems = new ArrayList<>();
    private boolean mIsFetching;
    private int mCurrentPage = 1;
    private SwipeRefreshLayout swipeRefreshLayout;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        fetchItemsAsync();
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
                int recyclerViewWidthDip = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        mPhotoRecyclerView.getWidth(), getResources().getDisplayMetrics());
                int calculatedSpanCount = recyclerViewWidthDip / MIN_ITEM_WIDTH_DIP;
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

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
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

    private void setupAdapter() {
        if (isAdded()) {
            mAdapter = new PhotoAdapter(mItems);
            mPhotoRecyclerView.setAdapter(mAdapter);
        }
    }

    private void fetchItemsAsync() {
        mIsFetching = true;
        new FetchItemsTask().execute(mCurrentPage++);
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private TextView mTitleTextView;

        public PhotoHolder(View itemView) {
            super(itemView);

            mTitleTextView = (TextView) itemView;
        }

        public void bindGalleryItem(GalleryItem item) {
            mTitleTextView.setText(item.toString());
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {
        private List<GalleryItem> mGalleryItems;

        public PhotoAdapter(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        public List<GalleryItem> getGalleryItems() {
            return mGalleryItems;
        }

        public void setGalleryItems(List<GalleryItem> galleryItems) {
            mGalleryItems = galleryItems;
        }

        @NonNull
        @NotNull
        @Override
        public PhotoHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
            TextView textView = new TextView(getActivity());

            return new PhotoHolder(textView);
        }

        @Override
        public void onBindViewHolder(@NonNull @NotNull PhotoGalleryFragment.PhotoHolder holder, int position) {
            GalleryItem galleryItem = mGalleryItems.get(position);
            holder.bindGalleryItem(galleryItem);
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
            int position = mAdapter.getItemCount() - visibleItemCount;

            mItems.addAll(galleryItems);
            setupAdapter();
            mPhotoRecyclerView.scrollToPosition(position);

            mIsFetching = false;
            swipeRefreshLayout.setRefreshing(false);
        }
    }
}