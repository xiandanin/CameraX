/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.camera.camera2.impl;

import static org.junit.Assume.assumeTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraDevice;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.camera2.Camera2Config;
import androidx.camera.core.BaseCamera;
import androidx.camera.core.CameraDeviceConfig;
import androidx.camera.core.CameraFactory;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.ImmediateSurface;
import androidx.camera.core.SessionConfig;
import androidx.camera.core.UseCase;
import androidx.camera.core.UseCaseConfig;
import androidx.camera.testing.CameraUtil;
import androidx.camera.testing.fakes.FakeUseCase;
import androidx.camera.testing.fakes.FakeUseCaseConfig;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(AndroidJUnit4.class)
public final class CameraTest {
    private static final LensFacing DEFAULT_LENS_FACING = LensFacing.BACK;
    static CameraFactory sCameraFactory;

    BaseCamera mCamera;

    TestUseCase mFakeUseCase;
    OnImageAvailableListener mMockOnImageAvailableListener;
    String mCameraId;

    private CountDownLatch mLatchForDeviceClose;
    private CameraDevice.StateCallback mDeviceStateCallback;

    private static String getCameraIdForLensFacingUnchecked(LensFacing lensFacing) {
        try {
            return sCameraFactory.cameraIdForLensFacing(lensFacing);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Unable to attach to camera with LensFacing " + lensFacing, e);
        }
    }

    @BeforeClass
    public static void classSetup() {
        sCameraFactory = new Camera2CameraFactory(ApplicationProvider.getApplicationContext());
    }

    @Before
    public void setup() {
        assumeTrue(CameraUtil.deviceHasCamera());
        mMockOnImageAvailableListener = Mockito.mock(ImageReader.OnImageAvailableListener.class);

        mLatchForDeviceClose = new CountDownLatch(1);
        mDeviceStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
            }

            @Override
            public void onClosed(@NonNull CameraDevice camera) {
                mLatchForDeviceClose.countDown();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
            }
        };

        mCameraId = getCameraIdForLensFacingUnchecked(DEFAULT_LENS_FACING);
        mCamera = sCameraFactory.getCamera(mCameraId);

        FakeUseCaseConfig.Builder configBuilder =
                new FakeUseCaseConfig.Builder()
                        .setTargetName("UseCase")
                        .setLensFacing(DEFAULT_LENS_FACING);
        new Camera2Config.Extender(configBuilder).setDeviceStateCallback(mDeviceStateCallback);
        mFakeUseCase = new TestUseCase(configBuilder.build(), mMockOnImageAvailableListener);
    }

    @After
    public void teardown() throws InterruptedException {
        // Need to release the camera no matter what is done, otherwise the CameraDevice is not
        // closed.
        // When the CameraDevice is not closed, then it can cause problems with interferes with
        // other test cases.
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }

        // Wait camera to be closed.
        if (mFakeUseCase != null) {
            mFakeUseCase.close();
            mFakeUseCase = null;
        }

        if (mLatchForDeviceClose != null) {
            mLatchForDeviceClose.await(2, TimeUnit.SECONDS);
        }
    }

    @Test
    public void onlineUseCase() {
        mCamera.open();

        mCamera.addOnlineUseCase(Collections.<UseCase>singletonList(mFakeUseCase));

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));

        mCamera.release();
    }

    @Test
    public void activeUseCase() {
        mCamera.open();
        mCamera.onUseCaseActive(mFakeUseCase);

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));

        mCamera.release();
    }

    @Test
    public void onlineAndActiveUseCase() {
        mCamera.addOnlineUseCase(Collections.<UseCase>singletonList(mFakeUseCase));
        mCamera.onUseCaseActive(mFakeUseCase);

        verify(mMockOnImageAvailableListener, timeout(4000).atLeastOnce())
                .onImageAvailable(any(ImageReader.class));
    }

    @Test
    public void removeOnlineUseCase() {
        mCamera.addOnlineUseCase(Collections.<UseCase>singletonList(mFakeUseCase));

        mCamera.removeOnlineUseCase(Collections.<UseCase>singletonList(mFakeUseCase));
        mCamera.onUseCaseActive(mFakeUseCase);

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));
    }

    @Test
    public void unopenedCamera() {
        mCamera.addOnlineUseCase(Collections.<UseCase>singletonList(mFakeUseCase));
        mCamera.removeOnlineUseCase(Collections.<UseCase>singletonList(mFakeUseCase));

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));
    }

    @Test
    public void closedCamera() {
        mCamera.addOnlineUseCase(Collections.<UseCase>singletonList(mFakeUseCase));
        mCamera.removeOnlineUseCase(Collections.<UseCase>singletonList(mFakeUseCase));

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));
    }

    @Test
    public void releaseUnopenedCamera() {
        // bypass camera close checking
        mLatchForDeviceClose = null;
        // Checks that if a camera has been released then calling open() will no longer open it.
        mCamera.release();
        mCamera.open();

        mCamera.addOnlineUseCase(Collections.<UseCase>singletonList(mFakeUseCase));
        mCamera.onUseCaseActive(mFakeUseCase);

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));
    }

    @Test
    public void releasedOpenedCamera() {
        // bypass camera close checking
        mLatchForDeviceClose = null;
        mCamera.open();
        mCamera.release();

        mCamera.addOnlineUseCase(Collections.<UseCase>singletonList(mFakeUseCase));
        mCamera.onUseCaseActive(mFakeUseCase);

        verify(mMockOnImageAvailableListener, never()).onImageAvailable(any(ImageReader.class));
    }

    private static class TestUseCase extends FakeUseCase {
        private final ImageReader.OnImageAvailableListener mImageAvailableListener;
        HandlerThread mHandlerThread = new HandlerThread("HandlerThread");
        Handler mHandler;
        ImageReader mImageReader;
        FakeUseCaseConfig mConfig;

        TestUseCase(
                FakeUseCaseConfig config,
                ImageReader.OnImageAvailableListener listener) {
            super(config);
            // Ensure we're using the combined configuration (user config + defaults)
            mConfig = (FakeUseCaseConfig) getUseCaseConfig();

            mImageAvailableListener = listener;
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
            Map<String, Size> suggestedResolutionMap = new HashMap<>();
            String cameraId = getCameraIdForLensFacingUnchecked(config.getLensFacing());
            suggestedResolutionMap.put(cameraId, new Size(640, 480));
            updateSuggestedResolution(suggestedResolutionMap);
        }

        // we need to set Camera2OptionUnpacker to the Config to enable the camera2 callback hookup.
        @Override
        protected UseCaseConfig.Builder<?, ?, ?> getDefaultBuilder(CameraX.LensFacing lensFacing) {
            return new FakeUseCaseConfig.Builder()
                    .setLensFacing(lensFacing)
                    .setSessionOptionUnpacker(new Camera2SessionOptionUnpacker());
        }

        void close() {
            mHandler.removeCallbacksAndMessages(null);
            mHandlerThread.quitSafely();
            if (mImageReader != null) {
                mImageReader.close();
            }
        }

        @Override
        protected Map<String, Size> onSuggestedResolutionUpdated(
                Map<String, Size> suggestedResolutionMap) {
            LensFacing lensFacing = ((CameraDeviceConfig) getUseCaseConfig()).getLensFacing();
            String cameraId = getCameraIdForLensFacingUnchecked(lensFacing);
            Size resolution = suggestedResolutionMap.get(cameraId);
            SessionConfig.Builder builder = SessionConfig.Builder.createFrom(mConfig);

            builder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);
            mImageReader =
                    ImageReader.newInstance(
                            resolution.getWidth(),
                            resolution.getHeight(),
                            ImageFormat.YUV_420_888, /*maxImages*/
                            2);
            mImageReader.setOnImageAvailableListener(mImageAvailableListener, mHandler);
            builder.addSurface(new ImmediateSurface(mImageReader.getSurface()));

            attachToCamera(cameraId, builder.build());
            return suggestedResolutionMap;
        }
    }
}
