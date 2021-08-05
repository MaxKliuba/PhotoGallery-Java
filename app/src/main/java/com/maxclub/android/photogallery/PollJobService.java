package com.maxclub.android.photogallery;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;

public class PollJobService extends JobService {
    private static final int JOB_ID = 1;

    private static final String TAG = "PollJobService";

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

    public static void start(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, new ComponentName(context, PollJobService.class))
                .setPeriodic(1000 * 60 * 15)
                .setPersisted(true)
                .build();

        scheduler.schedule(jobInfo);
    }

    public static void stop(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.cancelAll();
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

                    final String notificationChannelId = "com.maxclub.android.photogallery";
                    final String notificationMame = "NewPictures";

                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationChannel notificationChannel = new NotificationChannel(notificationChannelId,
                                notificationMame, NotificationManager.IMPORTANCE_DEFAULT);
                        notificationManager.createNotificationChannel(notificationChannel);
                    }

                    Notification notification = new NotificationCompat.Builder(mContext, notificationChannelId)
                            .setTicker(resources.getString(R.string.new_pictures_title, resources.getString(R.string.app_name)))
                            .setSmallIcon(android.R.drawable.ic_menu_report_image)
                            .setContentTitle(resources.getString(R.string.new_pictures_title, resources.getString(R.string.app_name)))
                            .setContentText(resources.getString(R.string.new_pictures_text, resources.getString(R.string.app_name)))
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .build();

                    notificationManager.notify(0, notification);
                }

                QueryPreferences.setLastResultId(mContext, resultId);
            }

            jobFinished(jobParams, false);

            return null;
        }
    }
}
