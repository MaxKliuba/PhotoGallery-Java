package com.maxclub.android.photogallery;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CompoundButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.squareup.picasso.Picasso;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryFragment extends VisibleFragment {

    private static final String TAG = "PhotoGalleryFragment";

    private static final int MIN_ITEM_WIDTH_PX = 480;
    private static final int VERTICAL_SCROLL_RANGE = 180;

    private RecyclerView mPhotoRecyclerView;
    private GridLayoutManager mLayoutManager;
    private List<GalleryItem> mItems = new ArrayList<>();
    private boolean mIsFetching = false;
    private boolean mIsRefreshing = false;
    private int mCurrentPage = 1;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private LinearProgressIndicator mLinearProgressIndicator;
    private FloatingActionButton mScrollUpButton;
    private boolean mIsScrollUpButtonVisible;
    private int mPrevVerticalScrollOffset;
    private Animation mAnimMoveDown;
    private Animation mAnimMoveUp;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                fetchItemsAsync();
            }
        }, 1);
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
            public void onScrolled(@NonNull @NotNull RecyclerView recyclerView, int dx, int dy) {
                if (Math.abs(recyclerView.computeVerticalScrollOffset() - mPrevVerticalScrollOffset) > VERTICAL_SCROLL_RANGE) {
                    mPrevVerticalScrollOffset = recyclerView.computeVerticalScrollOffset();

                    if (dy > 0 && mIsScrollUpButtonVisible) { // scrolling down
                        mScrollUpButton.startAnimation(mAnimMoveUp);
                    } else if (dy < 0 && !mIsScrollUpButtonVisible) { // scrolling up
                        mScrollUpButton.startAnimation(mAnimMoveDown);
                    }
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull @NotNull RecyclerView recyclerView, int newState) {
                if (!recyclerView.canScrollVertically(1)) {
                    fetchItemsAsync(mCurrentPage + 1);
                }

                if (!recyclerView.canScrollVertically(-1) && mIsScrollUpButtonVisible) {
                    mScrollUpButton.startAnimation(mAnimMoveUp);
                }
            }
        });

        setupAdapter();

        mSwipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.design_default_color_primary);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                fetchItemsAsync();
            }
        });

        mLinearProgressIndicator = (LinearProgressIndicator) view.findViewById(R.id.linear_progress_indicator);

        mScrollUpButton = (FloatingActionButton) view.findViewById(R.id.scroll_button);
        mScrollUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPhotoRecyclerView.smoothScrollToPosition(0);
            }
        });

        mAnimMoveDown = AnimationUtils.loadAnimation(getActivity(), R.anim.move_down);
        mAnimMoveDown.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mIsScrollUpButtonVisible = true;
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mScrollUpButton.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        mAnimMoveUp = AnimationUtils.loadAnimation(getActivity(), R.anim.move_up);
        mAnimMoveUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mIsScrollUpButtonVisible = false;
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mScrollUpButton.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull @NotNull Menu menu, @NonNull @NotNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_gallery, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        String query = QueryPreferences.getStoredQuery(getActivity());
        if (query != null) {
            searchView.setQuery(query, false);
            searchView.setIconified(false);
            searchView.clearFocus();
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "QueryTextSubmit: " + query);
                QueryPreferences.setStoredQuery(getActivity(), query);
                fetchItemsAsync();
                searchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(TAG, "QueryTextChange: " + newText);
                return false;
            }
        });

        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                QueryPreferences.setStoredQuery(getActivity(), null);
                fetchItemsAsync();

                return false;
            }
        });

        MenuItem switchItem = menu.findItem(R.id.menu_item_notification_switch);
        final SwitchMaterial notificationSwitch = (SwitchMaterial) switchItem.getActionView();
        notificationSwitch.setChecked(PollJobService.isActive(getActivity()));
        notificationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PollJobService.setServiceAlarm(getActivity(), isChecked);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void setupAdapter() {
        if (isAdded()) {
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private void fetchItemsAsync(int page) {
        if (!mIsFetching) {
            mCurrentPage = page;
            new FetchItemsTask(page, QueryPreferences.getStoredQuery(getActivity())).execute();
        }
    }

    private void fetchItemsAsync() {
        mIsRefreshing = true;
        fetchItemsAsync(1);
    }

    private class PhotoHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ImageView mItemImageView;
        private GalleryItem mGalleryItem;

        public PhotoHolder(View itemView) {
            super(itemView);

            mItemImageView = (ImageView) itemView.findViewById(R.id.item_image_view);
            itemView.setOnClickListener(this);
        }

        public void bindGalleryItem(GalleryItem galleryItem) {
            mGalleryItem = galleryItem;
            Picasso.get()
                    .load(galleryItem.getUrl())
                    .placeholder(R.drawable.placeholder)
                    .fit()
                    .centerCrop()
                    .into(mItemImageView);
        }

        @Override
        public void onClick(View v) {
            Intent intent = PhotoPageActivity.newIntent(getActivity(), mGalleryItem.getPhotoUri());
            startActivity(intent);
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
            holder.bindGalleryItem(galleryItem);
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    private class FetchItemsTask extends AsyncTask<Void, Void, List<GalleryItem>> {
        private int mPage;
        private String mQuery;

        public FetchItemsTask(int page, String query) {
            mPage = page;
            mQuery = query;
        }

        @Override
        protected void onPreExecute() {
            mIsFetching = true;
            if (mLinearProgressIndicator != null) {
                mLinearProgressIndicator.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected List<GalleryItem> doInBackground(Void... voids) {
            if (mQuery == null) {
                return new FlickrFetcher().fetchRecentPhotos(mPage);
            } else {
                return new FlickrFetcher().searchPhotos(mQuery, mPage);
            }
        }

        @Override
        protected void onPostExecute(List<GalleryItem> galleryItems) {
            if (mIsRefreshing) {
                mIsRefreshing = false;
                mSwipeRefreshLayout.setRefreshing(false);
                mItems.clear();
            }

            int visibleItemCount = mLayoutManager.findLastVisibleItemPosition()
                    - mLayoutManager.findFirstCompletelyVisibleItemPosition();
            int position = mItems.size() - visibleItemCount;

            mItems.addAll(galleryItems);
            setupAdapter();
            mPhotoRecyclerView.scrollToPosition(position);

            mLinearProgressIndicator.setVisibility(View.GONE);
            mIsFetching = false;
        }
    }
}