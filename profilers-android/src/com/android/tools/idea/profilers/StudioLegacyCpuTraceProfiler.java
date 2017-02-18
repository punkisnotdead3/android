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
package com.android.tools.idea.profilers;

import com.android.annotations.Nullable;
import com.android.ddmlib.*;
import com.android.tools.profiler.proto.CpuProfiler.*;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Since the 'am' (activity manager) command line in older releases of Android has limited functionalities for
 * CPU method-level tracing, we therefore need to employ JDWP (DDMS).
 *
 * In this class, 'profiling' means method-level tracing using either instrumentation or profiling.
 * Within this class 'profiling' is not a general concept as in 'CPU profiler'.
 */
public class StudioLegacyCpuTraceProfiler implements LegacyCpuTraceProfiler {

  private static Logger getLogger() {
    return Logger.getInstance(StudioLegacyCpuTraceProfiler.class);
  }

  @NotNull private IDevice myDevice;
  @NotNull private final Object myLegacyProfilingLock = new Object();
  /**
   * Map from app package name to the record of the profiling.
   * Existence in the map means there is an active ongoing profiling for that given app.
   */
  @NotNull private Map<String, LegacyProfilingRecord> myLegacyProfilingRecord = new HashMap<>();

  public StudioLegacyCpuTraceProfiler(@NotNull IDevice device) {
    myDevice = device;

    // Sets a global handler which contains callbacks related to method-level profiling using DDMS.
    ClientData.setMethodProfilingHandler(new LegacyProfilingHandler(myLegacyProfilingRecord));
  }

  @Override
  public CpuProfilingAppStartResponse startProfilingApp(CpuProfilingAppStartRequest request) {
    long timestamp = System.nanoTime();
    CpuProfilingAppStartResponse.Builder responseBuilder = CpuProfilingAppStartResponse.newBuilder();
    String appPkgName = request.getAppPkgName();
    Client client = myDevice.getClient(appPkgName);
    if (client == null) {
      return responseBuilder.setStatus(CpuProfilingAppStartResponse.Status.FAILURE)
        .setErrorMessage("App is not running.").build();
    }

    synchronized (myLegacyProfilingLock) {
      LegacyProfilingRecord record = myLegacyProfilingRecord.get(appPkgName);
      if (record != null) {
        return responseBuilder.setStatus(CpuProfilingAppStartResponse.Status.FAILURE)
          .setErrorMessage("Start request ignored. The app has an on-going profiling session.").build();
      }

      // com.android.ddmlib.HandleProfiling.sendSPSS(..) has buffer size as a parameter, but we cannot call it
      // because the class is not public. To set buffer size, we modify DdmPreferences which will be read by
      // client.startSamplingProfiler(..) and client.startMethodTracer().
      DdmPreferences.setProfilerBufferSizeMb(request.getBufferSizeInMb());
      record = new LegacyProfilingRecord(request, timestamp, responseBuilder);
      myLegacyProfilingRecord.put(appPkgName, record);
      try {
        if (request.getMode() == CpuProfilingAppStartRequest.Mode.SAMPLED) {
          client.startSamplingProfiler(request.getSamplingIntervalUs(), TimeUnit.MICROSECONDS);
        }
        else {
          client.startMethodTracer();
        }
        // startSamplingProfiler() and startMethodTracer() calls above always return immediately.
        // In case there is an error, ClientData.IMethodProfilingHandler.onEndFailure(..) will be called and the
        // responseBuilder has been populated there. Because IMethodProfilingHandler has no callback at success,
        // we limit the waiting to 0.1 second which is usually sufficient for the error handling to complete.
        record.myStartLatch.await(100, TimeUnit.MILLISECONDS);
        if (record.myStartFailed) {
          myLegacyProfilingRecord.remove(appPkgName);
        } else {
          responseBuilder.setStatus(CpuProfilingAppStartResponse.Status.SUCCESS);
        }
      }
      catch (IOException | InterruptedException e) {
        responseBuilder.setStatus(CpuProfilingAppStartResponse.Status.FAILURE);
        responseBuilder.setErrorMessage("Failed: " + e);
        getLogger().error("Exception while CpuServiceProxy startProfilingAppDdms: " + e);
      }
    }
    return responseBuilder.build();
  }

  @Override
  public CpuProfilingAppStopResponse stopProfilingApp(CpuProfilingAppStopRequest request) {
    CpuProfilingAppStopResponse.Builder responseBuilder = CpuProfilingAppStopResponse.newBuilder();
    String appPkyName = request.getAppPkgName();
    Client client = myDevice.getClient(appPkyName);

    synchronized (myLegacyProfilingLock) {
      if (client == null) {
        myLegacyProfilingRecord.remove(appPkyName);   // Remove the entry if there exists one.
        return responseBuilder.setStatus(CpuProfilingAppStopResponse.Status.FAILURE)
          .setErrorMessage("App is not running.").build();
      }

      LegacyProfilingRecord record = myLegacyProfilingRecord.get(appPkyName);
      if (record == null) {
        return responseBuilder.setStatus(CpuProfilingAppStopResponse.Status.FAILURE)
          .setErrorMessage("The app is not being profiled.").build();
      }

      record.setStopResponseBuilder(responseBuilder);
      try {
        if (record.myStartRequest.getMode() == CpuProfilingAppStartRequest.Mode.SAMPLED) {
          client.stopSamplingProfiler();
        } else {
          client.stopMethodTracer();
        }
        record.myStopLatch.await();
      }
      catch (IOException | InterruptedException e) {
        responseBuilder.setStatus(CpuProfilingAppStopResponse.Status.FAILURE);
        responseBuilder.setErrorMessage("Failed: " + e);
        getLogger().error("Exception while CpuServiceProxy stopProfilingApp: " + e);
      }
      myLegacyProfilingRecord.remove(appPkyName);
    }
    return responseBuilder.build();
  }

  @Override
  public ProfilingStateResponse checkAppProfilingState(ProfilingStateRequest request) {
    ProfilingStateResponse.Builder responseBuilder = ProfilingStateResponse.newBuilder();
    String appPkyName = request.getAppPkgName();
    Client client = myDevice.getClient(appPkyName);
    if (client == null) {
      return responseBuilder.setBeingProfiled(false).build();
    }

    synchronized (myLegacyProfilingLock) {
      LegacyProfilingRecord record = myLegacyProfilingRecord.get(appPkyName);
      if (record == null) {
        return responseBuilder.setBeingProfiled(false).build();
      }
      else {
        return responseBuilder
          .setBeingProfiled(true)
          .setStartRequest(record.myStartRequest)
          .setStartTimestamp(record.myStartRequestTimestamp)
          .build();
      }
    }
  }

  private static class LegacyProfilingHandler implements ClientData.IMethodProfilingHandler {
    @NotNull Map<String, LegacyProfilingRecord> myProfilingRecord;

    LegacyProfilingHandler(@NotNull Map<String, LegacyProfilingRecord> profilingRecord) {
      myProfilingRecord = profilingRecord;
    }

    @Override
    public void onSuccess(String remoteFilePath, Client client) {
      LegacyProfilingRecord record = myProfilingRecord.get(client.getClientData().getClientDescription());
      if (record != null) {
        CpuProfilingAppStopResponse.Builder stopResponseBuilder = record.getStopResponseBuilder();
        assert stopResponseBuilder != null;
        // Devices older than API 10 don't return profile results via JDWP. Instead they save the results on the
        // sdcard. We don't support this.
        stopResponseBuilder.setStatus(CpuProfilingAppStopResponse.Status.FAILURE);
        stopResponseBuilder.setErrorMessage(
          "Method profiling: Older devices (API level < 10) are not supported. Please use DDMS.");
        record.myStopLatch.countDown();
      }
    }

    @Override
    public void onSuccess(byte[] data, Client client) {
      LegacyProfilingRecord record = myProfilingRecord.get(client.getClientData().getClientDescription());
      if (record != null) {
        CpuProfilingAppStopResponse.Builder stopResponseBuilder = record.getStopResponseBuilder();
        assert stopResponseBuilder != null;
        stopResponseBuilder.setStatus(CpuProfilingAppStopResponse.Status.SUCCESS);
        stopResponseBuilder.setTrace(ByteString.copyFrom(data));
        // Set the trace id to a random integer.
        // TODO: Change to something more predictable/robust.
        stopResponseBuilder.setTraceId((int)(Math.random() * Integer.MAX_VALUE));
        record.myStopLatch.countDown();
      }
    }

    /**
     * The interface says this callback is "called when method tracing failed to start". It may be true
     * for API < 10 (not having FEATURE_PROFILING_STREAMING), but there appears no executing path trigger it
     * for API >= 10.
     * @param client the client that was profiled.
     * @param message an optional (<code>null</code> ok) error message to be displayed.
     */
    @Override
    public void onStartFailure(Client client, String message) {
    }

    /**
     * The interface says "Called when method tracing failed to end on the VM side", but in reality
     * it is called when either start or end fails. Therefore, we rely on whether a field
     * (record.getStopResponseBuilder()) is set to distinguish it is a start or stop failure,
     *
     * @param client the client that was profiled.
     * @param message an optional (<code>null</code> ok) error message to be displayed.
     */
    @Override
    public void onEndFailure(Client client, String message) {
      LegacyProfilingRecord record = myProfilingRecord.get(client.getClientData().getClientDescription());
      if (record != null) {
        CpuProfilingAppStopResponse.Builder stopResponseBuilder = record.getStopResponseBuilder();
        if (stopResponseBuilder != null) {
          stopResponseBuilder.setStatus(CpuProfilingAppStopResponse.Status.FAILURE);
          stopResponseBuilder.setErrorMessage("Failed to stop profiling: " + message);
          record.myStopLatch.countDown();
        } else {
          record.myStartFailed = true;
          record.myStartResponseBuilder.setStatus(CpuProfilingAppStartResponse.Status.FAILURE);
          record.myStartResponseBuilder.setErrorMessage("Failed to start profiling: " + message);
          record.myStartLatch.countDown();
        }
      }
    }
  }

  /**
   * Metadata of an ongoing profiling session of an app.
   */
  private static class LegacyProfilingRecord {
    @NotNull final CpuProfilingAppStartRequest myStartRequest;
    long myStartRequestTimestamp;
    @NotNull CpuProfilingAppStartResponse.Builder myStartResponseBuilder;
    /**
     * The latch that the profiler waits on when sending a start profiling request.
     * If the start fails, LegacyProfilingHandler.onEndFailure(..) would be triggered which
     * counts down the latch. There is no known way to count down if the start succeeds.
     */
    @NotNull CountDownLatch myStartLatch = new CountDownLatch(1);
    /**
     * The latch that the profiler waits on when sending a stop profiling request.
     * If the stop succeeds, LegacyProfilingHandler.onSuccess(..) would be triggered which
     * counts down the latch. If the stop fails, LegacyProfilingHandler.onEndFailure(..)
     * would be triggered which counts down the latch.
     */
    @NotNull CountDownLatch myStopLatch = new CountDownLatch(1);

    boolean myStartFailed = false;
    /**
     * The builder of stop response, Set only after the stop profiling API is called.
     */
    @Nullable CpuProfilingAppStopResponse.Builder myStopResponseBuilder;

    public LegacyProfilingRecord(@NotNull CpuProfilingAppStartRequest request,
                                 long timestamp,
                                 CpuProfilingAppStartResponse.Builder startResponseBuilder) {
      myStartRequest = request;
      myStartRequestTimestamp = timestamp;
      myStartResponseBuilder = startResponseBuilder;
    }

    public void setStopResponseBuilder(@NotNull CpuProfilingAppStopResponse.Builder builder) {
      myStopResponseBuilder = builder;
    }

    @Nullable public CpuProfilingAppStopResponse.Builder getStopResponseBuilder() {
      return myStopResponseBuilder;
    }
  }
}
