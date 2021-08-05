package com.maxclub.android.photogallery;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.List;

public class PollJobService extends JobService {
    private static final String TAG = "PollJobService";

    private static final int JOB_ID = 1;
    public static final String NOTIFICATION_CHANNEL_ID = "com.maxclub.android.photogallery";
    public static final String ACTION_SHOW_NOTIFICATION = "com.maxclub.android.photogallery.SHOW_NOTIFICATION";
    public static final String PERM_PRIVATE = "com.maxclub.android.photogallery.PRIVATE";
    public static final String REQUEST_CODE = "REQUEST_CODE";
    public static final String NOTIFICATION = "NOTIFICATION";

    private PollTask mCurrentTask;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "onStartJob");
        mCurrentTask = new PollTask(getApplicationContext());
        mCurrentTask.execute(params);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(TAG, "onStopJob");
        if (mCurrentTask != null) {
            mCurrentTask.cancel(true);
        }

        return true;
    }

    public static boolean isActive(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        for (JobInfo jobInfo : scheduler.getAllPendingJobs()) {
            if (jobInfo.getId() == JOB_ID) {
                return true;
            }
        }

        return false;
    }

    public static void setServiceAlarm(Context context, boolean isOn) {
        if (isOn) {
            start(context);
        } else {
            stop(context);
        }
    }

    private static void start(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, new ComponentName(context, PollJobService.class))
                .setPeriodic(1000 * 60 * 15)
                .setPersisted(true)
                .build();
        scheduler.schedule(jobInfo);
        QueryPreferences.setAlarmOn(context, true);
    }

    private static void stop(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.cancelAll();
        QueryPreferences.setAlarmOn(context, false);
    }

    private class PollTask extends AsyncTask<JobParameters, Void, Void> {
        private Context mContext;

        public PollTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(JobParameters... jobParameters) {
            JobParameters jobParams = jobParameters[0];

            String query = QueryPreferences.getStoredQuery(mContext);
            String lastResultId = QueryPreferences.getLastResultId(mContext);
            List<GalleryItem> items;

            if (query == null) {
                items = new FlickrFetcher().fetchRecentPhotos(1);
            } else {
                items = new FlickrFetcher().searchPhotos(query, 1);
            }

            if (items.size() > 0) {
                String resultId = items.get(0).getId();
                if (resultId.equals(lastResultId)) {
                    Log.i(TAG, "Got an old result: " + resultId);
                } else {
                    Log.i(TAG, "Got a new result: " + resultId);

                    Resources resources = getResources();
                    Intent i = PhotoGalleryActivity.newIntent(mContext);
                    PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, i, 0);

                    Notification notification = new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                            .setTicker(resources.getString(R.string.new_pictures_title, resources.getString(R.string.app_name)))
                            .setSmallIcon(android.R.drawable.ic_menu_report_image)
                            .setContentTitle(resources.getString(R.string.new_pictures_title, resources.getString(R.string.app_name)))
                            .setContentText(resources.getString(R.string.new_pictures_text, resources.getString(R.string.app_name)))
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .build();

                    showBackgroundNotification(0, notification);
                }

                QueryPreferences.setLastResultId(mContext, resultId);
            }

            jobFinished(jobParams, false);

            return null;
        }

        private void showBackgroundNotification(int requestCode, Notification notification) {
            Intent intent = new Intent(ACTION_SHOW_NOTIFICATION);
            intent.putExtra(REQUEST_CODE, requestCode);
            intent.putExtra(NOTIFICATION, notification);
            sendOrderedBroadcast(intent, PERM_PRIVATE, null, null,
                    Activity.RESULT_OK, null, null);
        }
    }
}
