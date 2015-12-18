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
package com.android.tools.idea.editors.gfxtrace.service.gfxapi;

import com.android.tools.rpclib.schema.Entity;
import com.android.tools.rpclib.schema.Field;
import com.android.tools.rpclib.schema.Slice;
import com.android.tools.rpclib.schema.Struct;
import org.jetbrains.annotations.NotNull;

import com.android.tools.rpclib.binary.BinaryClass;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.Namespace;
import com.android.tools.idea.editors.gfxtrace.service.image.ImageInfo;

import java.io.IOException;

public final class Texture2D implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private ImageInfo[] myLevels;

  // Constructs a default-initialized {@link Texture2D}.
  public Texture2D() {}


  public ImageInfo[] getLevels() {
    return myLevels;
  }

  public Texture2D setLevels(ImageInfo[] v) {
    myLevels = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }


  private static final Entity ENTITY = new Entity("gfxapi","Texture2D","","");

  static {
    ENTITY.setFields(new Field[]{
      new Field("Levels", new Slice("", new Struct(ImageInfo.Klass.INSTANCE.entity()))),
    });
    Namespace.register(Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public Entity entity() { return ENTITY; }

    @Override @NotNull
    public BinaryObject create() { return new Texture2D(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      Texture2D o = (Texture2D)obj;
      e.uint32(o.myLevels.length);
      for (int i = 0; i < o.myLevels.length; i++) {
        e.value(o.myLevels[i]);
      }
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      Texture2D o = (Texture2D)obj;
      o.myLevels = new ImageInfo[d.uint32()];
      for (int i = 0; i <o.myLevels.length; i++) {
        o.myLevels[i] = new ImageInfo();
        d.value(o.myLevels[i]);
      }
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
