/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.car.apitest;

import android.car.content.pm.AppBlockingPackageInfo;
import android.car.content.pm.CarAppBlockingPolicy;
import android.content.Context;
import android.os.Parcel;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

@SmallTest
public class CarAppBlockingPolicyTest {
    private static final String TAG = AppBlockingPackageInfoTest.class.getSimpleName();

    private final Context mContext = InstrumentationRegistry.getInstrumentation()
            .getTargetContext();

    @Test
    public void testParcelling() throws Exception {
        AppBlockingPackageInfo carServiceInfo =
                AppBlockingPackageInfoTest.createInfoCarService(mContext);
        AppBlockingPackageInfo selfInfo =
                AppBlockingPackageInfoTest.createInfoSelf(mContext);
        // this is only for testing parcelling. contents has nothing to do with actual app blocking.
        AppBlockingPackageInfo[] whitelists = new AppBlockingPackageInfo[] { carServiceInfo,
                selfInfo };
        AppBlockingPackageInfo[] blacklists = new AppBlockingPackageInfo[] { selfInfo };
        CarAppBlockingPolicy policyExpected = new CarAppBlockingPolicy(whitelists, blacklists);
        Parcel dest = Parcel.obtain();
        policyExpected.writeToParcel(dest, 0);
        dest.setDataPosition(0);
        CarAppBlockingPolicy policyRead = new CarAppBlockingPolicy(dest);
        Log.i(TAG, "expected:" + policyExpected + ",read:" + policyRead);
        assertThat(policyRead).isEqualTo(policyExpected);
    }
}
