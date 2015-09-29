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

import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class TimingInfo implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private AtomTimer[] myPerCommand;
  private AtomRangeTimer[] myPerDrawCall;
  private AtomRangeTimer[] myPerFrame;

  // Constructs a default-initialized {@link TimingInfo}.
  public TimingInfo() {}


  public AtomTimer[] getPerCommand() {
    return myPerCommand;
  }

  public TimingInfo setPerCommand(AtomTimer[] v) {
    myPerCommand = v;
    return this;
  }

  public AtomRangeTimer[] getPerDrawCall() {
    return myPerDrawCall;
  }

  public TimingInfo setPerDrawCall(AtomRangeTimer[] v) {
    myPerDrawCall = v;
    return this;
  }

  public AtomRangeTimer[] getPerFrame() {
    return myPerFrame;
  }

  public TimingInfo setPerFrame(AtomRangeTimer[] v) {
    myPerFrame = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {-45, 22, 84, -89, -71, -2, 26, 57, 47, 89, 114, -115, -82, 53, 9, -62, 61, 51, -103, 82, };
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
    public BinaryObject create() { return new TimingInfo(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      TimingInfo o = (TimingInfo)obj;
      e.uint32(o.myPerCommand.length);
      for (int i = 0; i < o.myPerCommand.length; i++) {
        e.value(o.myPerCommand[i]);
      }
      e.uint32(o.myPerDrawCall.length);
      for (int i = 0; i < o.myPerDrawCall.length; i++) {
        e.value(o.myPerDrawCall[i]);
      }
      e.uint32(o.myPerFrame.length);
      for (int i = 0; i < o.myPerFrame.length; i++) {
        e.value(o.myPerFrame[i]);
      }
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      TimingInfo o = (TimingInfo)obj;
      o.myPerCommand = new AtomTimer[d.uint32()];
      for (int i = 0; i <o.myPerCommand.length; i++) {
        o.myPerCommand[i] = new AtomTimer();
        d.value(o.myPerCommand[i]);
      }
      o.myPerDrawCall = new AtomRangeTimer[d.uint32()];
      for (int i = 0; i <o.myPerDrawCall.length; i++) {
        o.myPerDrawCall[i] = new AtomRangeTimer();
        d.value(o.myPerDrawCall[i]);
      }
      o.myPerFrame = new AtomRangeTimer[d.uint32()];
      for (int i = 0; i <o.myPerFrame.length; i++) {
        o.myPerFrame[i] = new AtomRangeTimer();
        d.value(o.myPerFrame[i]);
      }
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
