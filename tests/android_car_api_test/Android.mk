# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := AndroidCarApiTest
LOCAL_PRIVATE_PLATFORM_APIS := true

# for system|priviledged permission.
LOCAL_CERTIFICATE := platform

LOCAL_MODULE_TAGS := tests

# When built explicitly put it in the data partition
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA_APPS)

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_STATIC_JAVA_LIBRARIES := junit
LOCAL_STATIC_JAVA_LIBRARIES += \
        androidx.test.rules \
        android.hidl.base-V1.0-java \
        android.hardware.automotive.vehicle-V2.0-java \
        android.car.cluster.navigation \
        android.car.cluster.navigation \
        android.car.testapi \
        android.car.test.utils \
        androidx.test.runner \
        compatibility-device-util-axt \
        platform-test-annotations \
        testng \
        truth-prebuilt

LOCAL_JAVA_LIBRARIES := android.car android.test.runner android.test.base

LOCAL_COMPATIBILITY_SUITE := general-tests

include $(BUILD_PACKAGE)
