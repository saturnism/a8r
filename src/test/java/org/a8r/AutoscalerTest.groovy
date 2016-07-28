/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.a8r

import static org.junit.Assert.*;

import org.a8r.Metric
import org.a8r.MetricsService
import org.infinispan.manager.EmbeddedCacheManager
import org.infinispan.tree.TreeCache
import org.junit.Test


class AutoscalerTest extends BaseTest {
    /*
     @Test
     void testSingleHost() {
     Calendar now = Calendar.instance
     def definition = new AutoscalerDefintion(
     replicationControllerId: "test",
     metricName: "value",
     duration: 60000
     )
     def metrics = []
     metrics << new Metric(
     replicationControllerId: "test",
     podId: "test-aqex",
     metricName: "value",
     metricTimestamp: now.time,
     metricValue: 80.0
     )
     metrics.each {
     metricsService.postMetric(it)
     }
     definition.threshold = 100
     assertEquals(0, autoscaler.calculateScale(definition))
     definition.threshold = 50
     assertEquals(1, autoscaler.calculateScale(definition))
     }
     @Test
     void testTwoHosts() {
     Calendar now = Calendar.instance
     def metrics = []
     metrics << new Metric(
     replicationControllerId: "test",
     podId: "test-aqex",
     metricName: "value",
     metricTimestamp: now.time,
     metricValue: 80.0
     )
     metrics << new Metric(
     replicationControllerId: "test",
     podId: "test-kljz",
     metricName: "value",
     metricTimestamp: now.time,
     metricValue: 80.0
     )
     metrics.each {
     metricsService.postMetric(it)
     }
     def definition = new AutoscalerDefintion(
     replicationControllerId: "test",
     metricName: "value",
     duration: 60000
     )
     definition.threshold = 200
     assertEquals(-1, autoscaler.calculateScale(definition))
     definition.threshold = 100
     assertEquals(0, autoscaler.calculateScale(definition))
     definition.threshold = 50
     assertEquals(1, autoscaler.calculateScale(definition))
     }
     */
}
