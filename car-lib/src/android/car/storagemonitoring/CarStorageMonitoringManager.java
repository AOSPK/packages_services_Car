/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.car.storagemonitoring;

import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import com.android.car.internal.SingleMessageHandler;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static android.car.CarApiUtil.checkCarNotConnectedExceptionFromCarService;

/**
 * API for retrieving information and metrics about the flash storage.
 *
 * @hide
 */
@SystemApi
public final class CarStorageMonitoringManager implements CarManagerBase {
    private static final String TAG = CarStorageMonitoringManager.class.getSimpleName();
    private static final int MSG_IO_STATS_EVENT = 0;

    private final ICarStorageMonitoring mService;
    private ListenerToService mListenerToService;
    private final SingleMessageHandler<UidIoStatsDelta> mMessageHandler;
    private final Set<UidIoStatsListener> mListeners = new HashSet<>();

    public interface UidIoStatsListener {
        void onSnapshot(UidIoStatsDelta snapshot);
    }
    private static final class ListenerToService extends IUidIoStatsListener.Stub {
        private final WeakReference<CarStorageMonitoringManager> mManager;

        ListenerToService(CarStorageMonitoringManager manager) {
            mManager = new WeakReference<>(manager);
        }

        @Override
        public void onSnapshot(UidIoStatsDelta snapshot) {
            CarStorageMonitoringManager manager = mManager.get();
            if (manager != null) {
                manager.mMessageHandler.sendEvents(Collections.singletonList(snapshot));
            }
        }
    }

    public static final String INTENT_EXCESSIVE_IO = "android.car.storagemonitoring.EXCESSIVE_IO";

    public static final int PRE_EOL_INFO_UNKNOWN = 0;
    public static final int PRE_EOL_INFO_NORMAL = 1;
    public static final int PRE_EOL_INFO_WARNING = 2;
    public static final int PRE_EOL_INFO_URGENT = 3;

    /**
     * @hide
     */
    public CarStorageMonitoringManager(IBinder service, Handler handler) {
        mService = ICarStorageMonitoring.Stub.asInterface(service);
        mMessageHandler = new SingleMessageHandler<UidIoStatsDelta>(handler, MSG_IO_STATS_EVENT) {
            @Override
            protected void handleEvent(UidIoStatsDelta event) {
                for (UidIoStatsListener listener : mListeners) {
                    listener.onSnapshot(event);
                }
            }
        };
    }

    /**
     * @hide
     */
    @Override
    public void onCarDisconnected() {
    }

    // ICarStorageMonitoring forwards

    /**
     * This method returns the value of the "pre EOL" indicator for the flash storage
     * as retrieved during the current boot cycle.
     *
     * It will return either PRE_EOL_INFO_UNKNOWN if the value can't be determined,
     * or one of PRE_EOL_INFO_{NORMAL|WARNING|URGENT} depending on the device state.
     */
    @RequiresPermission(value=Car.PERMISSION_STORAGE_MONITORING)
    public int getPreEolIndicatorStatus() throws CarNotConnectedException {
        try {
            return mService.getPreEolIndicatorStatus();
        } catch (IllegalStateException e) {
            checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return PRE_EOL_INFO_UNKNOWN;
    }

    /**
     * This method returns the value of the wear estimate indicators for the flash storage
     * as retrieved during the current boot cycle.
     *
     * The indicators are guaranteed to be a lower-bound on the actual wear of the storage.
     * Current technology in common automotive usage offers estimates in 10% increments.
     *
     * If either or both indicators are not available, they will be reported as UNKNOWN.
     */
    @RequiresPermission(value=Car.PERMISSION_STORAGE_MONITORING)
    public WearEstimate getWearEstimate() throws CarNotConnectedException {
        try {
            return mService.getWearEstimate();
        } catch (IllegalStateException e) {
            checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return WearEstimate.UNKNOWN_ESTIMATE;
    }

    /**
     * This method returns a list of all changes in wear estimate indicators detected during the
     * lifetime of the system.
     *
     * The indicators are not guaranteed to persist across a factory reset.
     *
     * The indicators are guaranteed to be a lower-bound on the actual wear of the storage.
     * Current technology in common automotive usage offers estimates in 10% increments.
     *
     * If no indicators are available, an empty list will be returned.
     */
    @RequiresPermission(value=Car.PERMISSION_STORAGE_MONITORING)
    public List<WearEstimateChange> getWearEstimateHistory() throws CarNotConnectedException {
        try {
            return mService.getWearEstimateHistory();
        } catch (IllegalStateException e) {
            checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return Collections.emptyList();
    }

    /**
     * This method returns a list of per user-id I/O activity metrics as collected at the end of
     * system boot.
     *
     * The BOOT_COMPLETE broadcast is used as the trigger to collect this data. The implementation
     * may impose an additional, and even variable across boot cycles, delay between the sending
     * of the broadcast and the collection of the data.
     *
     * If the information is not available, an empty list will be returned.
     */
    @RequiresPermission(value=Car.PERMISSION_STORAGE_MONITORING)
    public List<UidIoStats> getBootIoStats() throws CarNotConnectedException {
        try {
            return mService.getBootIoStats();
        } catch (IllegalStateException e) {
            checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return Collections.emptyList();
    }

    /**
     * This method returns a list of per user-id I/O activity metrics as collected from kernel
     * start until the last snapshot.
     *
     * The samples provided might be as old as the value of the ioStatsRefreshRateSeconds setting.
     *
     * If the information is not available, an empty list will be returned.
     */
    @RequiresPermission(value=Car.PERMISSION_STORAGE_MONITORING)
    public List<UidIoStats> getAggregateIoStats() throws CarNotConnectedException {
        try {
            return mService.getAggregateIoStats();
        } catch (IllegalStateException e) {
            checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return Collections.emptyList();
    }

    /**
     * This method returns a list of the I/O stats deltas currently stored by the system.
     *
     * Periodically, the system gathers I/O activity metrics and computes and stores a delta from
     * the previous cycle. The timing and the number of these stored samples are configurable
     * by the OEM.
     *
     * The samples are returned in order from the oldest to the newest.
     *
     * If the information is not available, an empty list will be returned.
     */
    @RequiresPermission(value=Car.PERMISSION_STORAGE_MONITORING)
    public List<UidIoStatsDelta> getIoStatsDeltas() throws CarNotConnectedException {
        try {
            return mService.getIoStatsDeltas();
        } catch (IllegalStateException e) {
            checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
        return Collections.emptyList();
    }

    /**
     * This method registers a new listener to receive I/O stats deltas.
     *
     * The system periodically gathers I/O activity metrics and computes a delta of such
     * activity. Registered listeners will receive those deltas as they are available.
     *
     * The timing of availability of the deltas is configurable by the OEM.
     */
    @RequiresPermission(value=Car.PERMISSION_STORAGE_MONITORING)
    public void registerListener(UidIoStatsListener listener) throws CarNotConnectedException {
        try {
            if (mListeners.isEmpty()) {
                if (mListenerToService == null) {
                    mListenerToService = new ListenerToService(this);
                }
                mService.registerListener(mListenerToService);
            }
            mListeners.add(listener);
        } catch (IllegalStateException e) {
            checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
    }

    /**
     * This method removes a registered listener of I/O stats deltas.
     */
    @RequiresPermission(value=Car.PERMISSION_STORAGE_MONITORING)
    public void unregisterListener(UidIoStatsListener listener) throws CarNotConnectedException {
        try {
            if (!mListeners.remove(listener)) {
                return;
            }
            if (mListeners.isEmpty()) {
                mService.unregisterListener(mListenerToService);
                mListenerToService = null;
            }
        } catch (IllegalStateException e) {
            checkCarNotConnectedExceptionFromCarService(e);
        } catch (RemoteException e) {
            throw new CarNotConnectedException();
        }
    }
}
