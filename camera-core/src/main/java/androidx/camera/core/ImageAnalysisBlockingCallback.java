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

package androidx.camera.core;

import android.os.Handler;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OnImageAvailableListener with blocking behavior. It never drops image without analyzing it.
 *
 * <p> Used with {@link ImageAnalysis}.
 */
final class ImageAnalysisBlockingCallback implements ImageReaderProxy.OnImageAvailableListener {

    private static final String TAG = "BlockingCallback";

    final AtomicReference<ImageAnalysis.Analyzer> mSubscribedAnalyzer;
    final AtomicInteger mRelativeRotation;

    private final Handler mUserHandler;

    ImageAnalysisBlockingCallback(AtomicReference<ImageAnalysis.Analyzer> subscribedAnalyzer,
            AtomicInteger relativeRotation, Handler userHandler) {
        mSubscribedAnalyzer = subscribedAnalyzer;
        mRelativeRotation = relativeRotation;
        mUserHandler = userHandler;
    }

    @Override
    public void onImageAvailable(ImageReaderProxy imageReaderProxy) {
        ImageProxy image = imageReaderProxy.acquireNextImage();
        if (image == null) {
            return;
        }
        try {
            mUserHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        ImageAnalysis.Analyzer analyzer = mSubscribedAnalyzer.get();
                        if (analyzer != null) {
                            analyzer.analyze(image, mRelativeRotation.get());
                        }
                    } finally {
                        image.close();
                    }
                }
            });
        } catch (RuntimeException e) {
            image.close();
        }
    }
}
