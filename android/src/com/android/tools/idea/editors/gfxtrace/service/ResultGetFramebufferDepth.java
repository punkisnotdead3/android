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
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service;

import com.android.tools.idea.editors.gfxtrace.service.path.ImageInfoPath;
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

final class ResultGetFramebufferDepth implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  ImageInfoPath myValue;

  // Constructs a default-initialized {@link ResultGetFramebufferDepth}.
  public ResultGetFramebufferDepth() {}


  public ImageInfoPath getValue() {
    return myValue;
  }

  public ResultGetFramebufferDepth setValue(ImageInfoPath v) {
    myValue = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {-21, 106, 80, 35, 7, -97, -29, 77, -95, -125, 30, -24, 27, 119, -98, 105, 95, -41, 92, -100, };
  public static final BinaryID ID = new BinaryID(IDBytes);

  static {
    Namespace.register(ID, Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public BinaryID id() { return ID; }

    @Override @NotNull
    public BinaryObject create() { return new ResultGetFramebufferDepth(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      ResultGetFramebufferDepth o = (ResultGetFramebufferDepth)obj;
      e.object(o.myValue);
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      ResultGetFramebufferDepth o = (ResultGetFramebufferDepth)obj;
      o.myValue = (ImageInfoPath)d.object();
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
