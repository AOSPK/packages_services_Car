/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.garagemode;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobSnapshot;
import android.content.Intent;
import android.os.Handler;
import android.os.UserHandle;
import android.util.ArraySet;

import com.android.car.CarLocalServices;
import com.android.car.CarStatsLogHelper;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * Class that interacts with JobScheduler, controls system idleness and monitor jobs which are
 * in GarageMode interest
 */

class GarageMode {
    private static final Logger LOG = new Logger("GarageMode");

    /**
     * When changing this field value, please update
     * {@link com.android.server.job.controllers.idle.CarIdlenessTracker} as well.
     */
    public static final String ACTION_GARAGE_MODE_ON =
            "com.android.server.jobscheduler.GARAGE_MODE_ON";

    /**
     * When changing this field value, please update
     * {@link com.android.server.job.controllers.idle.CarIdlenessTracker} as well.
     */
    public static final String ACTION_GARAGE_MODE_OFF =
            "com.android.server.jobscheduler.GARAGE_MODE_OFF";

    static final long JOB_SNAPSHOT_INITIAL_UPDATE_MS = 10_000; // 10 seconds
    static final long JOB_SNAPSHOT_UPDATE_FREQUENCY_MS = 1_000; // 1 second
    static final long USER_STOP_CHECK_INTERVAL = 10_000; // 10 secs

    private final Controller mController;
    private final JobScheduler mJobScheduler;
    private final Object mLock = new Object();
    private final Handler mHandler;

    @GuardedBy("mLock")
    private boolean mGarageModeActive;
    @GuardedBy("mLock")
    private List<String> mPendingJobs = new ArrayList<>();
    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            int numberRunning = numberOfJobsRunning();
            if (numberRunning > 0) {
                LOG.d("" + numberRunning + " jobs are still running. Need to wait more ...");
                mHandler.postDelayed(mRunnable, JOB_SNAPSHOT_UPDATE_FREQUENCY_MS);
            } else {
                LOG.d("No jobs are currently running.");
                finish();
            }
        }
    };

    private final Runnable mStopUserCheckRunnable = new Runnable() {
        @Override
        public void run() {
            int userToStop = UserHandle.USER_SYSTEM; // BG user never becomes system user.
            int remainingUsersToStop = 0;
            synchronized (mLock) {
                remainingUsersToStop = mStartedBackgroundUsers.size();
                if (remainingUsersToStop < 1) {
                    return;
                }
                userToStop = mStartedBackgroundUsers.valueAt(0);
            }
            if (numberOfJobsRunning() == 0) { // all jobs done or stopped.
                // Keep user until job scheduling is stopped. Otherwise, it can crash jobs.
                if (userToStop != UserHandle.USER_SYSTEM) {
                    CarLocalServices.getService(CarUserService.class).stopBackgroundUser(
                            userToStop);
                    LOG.i("Stopping background user:" + userToStop + " remaining users:"
                            + (remainingUsersToStop - 1));
                }
                synchronized (mLock) {
                    mStartedBackgroundUsers.remove(userToStop);
                    if (mStartedBackgroundUsers.size() == 0) {
                        LOG.i("All background users have stopped");
                        return;
                    }
                }
            } else {
                LOG.i("Waiting for jobs to finish, remaining users:" + remainingUsersToStop);
            }
            // Poll again
            mHandler.postDelayed(mStopUserCheckRunnable, USER_STOP_CHECK_INTERVAL);
        }
    };

    @GuardedBy("mLock")
    private CompletableFuture<Void> mFuture;
    @GuardedBy("mLock")
    private ArraySet<Integer> mStartedBackgroundUsers = new ArraySet<>();

    GarageMode(Controller controller) {
        mGarageModeActive = false;
        mController = controller;
        mJobScheduler = controller.getJobSchedulerService();
        mHandler = controller.getHandler();
    }

    boolean isGarageModeActive() {
        synchronized (mLock) {
            return mGarageModeActive;
        }
    }

    List<String> pendingJobs() {
        synchronized (mLock) {
            return new ArrayList<>(mPendingJobs);
        }
    }

    void enterGarageMode(CompletableFuture<Void> future) {
        LOG.d("Entering GarageMode");
        synchronized (mLock) {
            mGarageModeActive = true;
        }
        updateFuture(future);
        broadcastSignalToJobScheduler(true);
        CarStatsLogHelper.logGarageModeStart();
        startMonitoringThread();
        ArrayList<Integer> startedUsers =
                CarLocalServices.getService(CarUserService.class).startAllBackgroundUsers();
        synchronized (mLock) {
            mStartedBackgroundUsers.addAll(startedUsers);
        }
    }

    void cancel() {
        broadcastSignalToJobScheduler(false);
        synchronized (mLock) {
            if (mFuture != null && !mFuture.isDone()) {
                mFuture.cancel(true);
            }
            mFuture = null;
            startBackgroundUserStoppingLocked();
        }
    }

    void finish() {
        broadcastSignalToJobScheduler(false);
        CarStatsLogHelper.logGarageModeStop();
        mController.scheduleNextWakeup();
        synchronized (mLock) {
            if (mFuture != null && !mFuture.isDone()) {
                mFuture.complete(null);
            }
            mFuture = null;
            startBackgroundUserStoppingLocked();
        }
    }

    private void cleanupGarageMode() {
        LOG.d("Cleaning up GarageMode");
        synchronized (mLock) {
            mGarageModeActive = false;
            stopMonitoringThread();
            mHandler.removeCallbacks(mRunnable);
            startBackgroundUserStoppingLocked();
        }
    }

    private void startBackgroundUserStoppingLocked() {
        if (mStartedBackgroundUsers.size() > 0) {
            mHandler.postDelayed(mStopUserCheckRunnable, USER_STOP_CHECK_INTERVAL);
        }
    }

    private void updateFuture(CompletableFuture<Void> future) {
        synchronized (mLock) {
            mFuture = future;
            if (mFuture != null) {
                mFuture.whenComplete((result, exception) -> {
                    if (exception == null) {
                        LOG.d("GarageMode completed normally");
                    } else if (exception instanceof CancellationException) {
                        LOG.d("GarageMode was canceled");
                    } else {
                        LOG.e("GarageMode ended due to exception: ", exception);
                    }
                    cleanupGarageMode();
                });
            }
        }
    }

    private void broadcastSignalToJobScheduler(boolean enableGarageMode) {
        Intent i = new Intent();
        i.setAction(enableGarageMode ? ACTION_GARAGE_MODE_ON : ACTION_GARAGE_MODE_OFF);
        i.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_NO_ABORT);
        mController.sendBroadcast(i);
    }

    private void startMonitoringThread() {
        mHandler.postDelayed(mRunnable, JOB_SNAPSHOT_INITIAL_UPDATE_MS);
    }

    private void stopMonitoringThread() {
        mHandler.removeCallbacks(mRunnable);
    }

    private int numberOfJobsRunning() {
        List<JobSnapshot> allJobs = mJobScheduler.getAllJobSnapshots();
        List<JobInfo> startedJobs = mJobScheduler.getStartedJobs();
        if (allJobs == null || startedJobs == null) {
            return 0;
        }
        List<String> currentPendingJobs = new ArrayList<>();
        int count = 0;
        for (JobSnapshot snap : allJobs) {
            if (startedJobs.contains(snap.getJobInfo())
                    && snap.getJobInfo().isRequireDeviceIdle()) {
                currentPendingJobs.add(snap.getJobInfo().toString());
                count++;
            }
        }
        if (count > 0) {
            // We have something pending, so update the list.
            // (Otherwise, keep the old list.)
            synchronized (mLock) {
                mPendingJobs = currentPendingJobs;
            }
        }
        return count;
    }
}
