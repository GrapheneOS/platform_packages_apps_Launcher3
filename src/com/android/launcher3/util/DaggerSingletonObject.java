/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.util;

import android.content.Context;

import com.android.launcher3.dagger.LauncherAppComponent;
import com.android.launcher3.dagger.LauncherComponentProvider;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A class to provide DaggerSingleton objects in a traditional way.
 * We should delete this class at the end and use @Inject to get dagger provided singletons.
 */

public class DaggerSingletonObject<T> {
    private final Function<LauncherAppComponent, T> mFunction;
    private T mValue;

    public DaggerSingletonObject(Function<LauncherAppComponent, T> function) {
        mFunction = function;
    }

    public T get(Context context) {
        mValue = mFunction.apply(LauncherComponentProvider.get(context));
        return mValue;
    }

    /**
     * Executes the callback is the value is already created
     * @return true if the callback was executed, false otherwise
     */
    public boolean executeIfCreated(Consumer<T> callback) {
        T v = mValue;
        if (v != null) {
            callback.accept(v);
            return true;
        } else {
            return false;
        }
    }
}
