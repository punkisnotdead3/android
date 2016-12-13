/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.network;

import com.android.tools.profiler.proto.NetworkServiceGrpc;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.TestGrpcChannel;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.android.tools.profiler.proto.NetworkProfiler.*;
import static org.junit.Assert.*;

public class NetworkProfilerTest {
  private static final int FAKE_PID = 111;

  private final FakeService myService = new FakeService();
  @Rule public TestGrpcChannel<FakeService> myGrpcChannel = new TestGrpcChannel<>("NetworkProfilerTest", myService);

  private Profiler.Process FAKE_PROCESS = Profiler.Process.newBuilder().setPid(FAKE_PID).setName("FakeProcess").build();
  private NetworkProfiler myProfiler;

  @Before
  public void setUp() {
    myProfiler = new NetworkProfiler(myGrpcChannel.getProfilers());
  }

  @Test
  public void newMonitor() {
    assertEquals("Network", myProfiler.newMonitor().getName());
  }

  @Test
  public void startMonitoring() {
    myProfiler.startProfiling(FAKE_PROCESS);
    assertEquals(FAKE_PID, myService.getAppId());
  }

  @Test
  public void stopMonitoring() {
    myProfiler.stopProfiling(FAKE_PROCESS);
    assertEquals(FAKE_PID, myService.getAppId());
  }

  private static class FakeService extends NetworkServiceGrpc.NetworkServiceImplBase {
    private int myAppId;

    @Override
    public void startMonitoringApp(NetworkStartRequest request,
                                   StreamObserver<NetworkStartResponse> responseObserver) {
      myAppId = request.getAppId();
      responseObserver.onNext(NetworkStartResponse.newBuilder().build());
      responseObserver.onCompleted();
    }

    @Override
    public void stopMonitoringApp(NetworkStopRequest request,
                                  StreamObserver<NetworkStopResponse> responseObserver) {
      myAppId = request.getAppId();
      responseObserver.onNext(NetworkStopResponse.newBuilder().build());
      responseObserver.onCompleted();
    }

    private int getAppId() {
      return myAppId;
    }
  }
}