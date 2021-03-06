/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.iceberg.util;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ThreadPools {
  public static final String PLANNER_THREAD_POOL_SIZE_PROP = "iceberg.planner.num-threads";
  public static final String WORKER_THREAD_POOL_SIZE_PROP = "iceberg.worker.num-threads";

  private static ExecutorService PLANNER_POOL = MoreExecutors.getExitingExecutorService(
      (ThreadPoolExecutor) Executors.newFixedThreadPool(
          getPoolSize(PLANNER_THREAD_POOL_SIZE_PROP, 4),
          new ThreadFactoryBuilder()
              .setDaemon(true)
              .setNameFormat("iceberg-planner-pool-%d")
              .build()));

  private static ExecutorService WORKER_POOL = MoreExecutors.getExitingExecutorService(
      (ThreadPoolExecutor) Executors.newFixedThreadPool(
          getPoolSize(WORKER_THREAD_POOL_SIZE_PROP, Runtime.getRuntime().availableProcessors()),
          new ThreadFactoryBuilder()
              .setDaemon(true)
              .setNameFormat("iceberg-worker-pool-%d")
              .build()));

  /**
   * Return an {@link ExecutorService} that uses the "planner" thread-pool.
   * <p>
   * The size of the planner pool limits the number of concurrent planning operations in the base
   * table implementation.
   * <p>
   * The size of this thread-pool is controlled by the Java system property
   * {@code iceberg.planner.num-threads}.
   *
   * @return an {@link ExecutorService} that uses the planner pool
   */
  public static ExecutorService getPlannerPool() {
    return PLANNER_POOL;
  }

  /**
   * Return an {@link ExecutorService} that uses the "worker" thread-pool.
   * <p>
   * The size of the worker pool limits the number of tasks concurrently reading manifests in the
   * base table implementation across all concurrent planning operations.
   * <p>
   * The size of this thread-pool is controlled by the Java system property
   * {@code iceberg.worker.num-threads}.
   *
   * @return an {@link ExecutorService} that uses the worker pool
   */
  public static ExecutorService getWorkerPool() {
    return WORKER_POOL;
  }

  private static int getPoolSize(String systemProperty, int defaultSize) {
    String value = System.getProperty(systemProperty);
    if (value != null) {
      try {
        return Integer.parseUnsignedInt(value);
      } catch (NumberFormatException e) {
        // will return the default
      }
    }
    return defaultSize;
  }
}
