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

import org.a8r.AutoscalerConfiguration
import org.a8r.Metric
import org.a8r.MetricsService
import org.infinispan.configuration.cache.ConfigurationBuilder
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager
import org.infinispan.manager.EmbeddedCacheManager
import org.infinispan.transaction.lookup.DummyTransactionManagerLookup;
import org.infinispan.tree.TreeCache
import org.infinispan.tree.TreeCacheFactory
import org.junit.Before
import org.junit.Test


class MetricsServiceTest extends BaseTest {
    @Test
    void testRandom() {
        def sim = new MetricsSimulator(
                replicationControllerId: "test",
                podName: "test-aqex",
                metricName: "value",
                timeRange: -this.config.metricFreshness,
                lowValue: 0.0,
                highValue: 100.0
                )

        for (int i = 0; i < 100; i++) {
            def m = sim.next();
            metricsService.postMetric(m);
        }
    }

    @Test(expected=TooEarlyException)
    void testTooEarly() {
        Calendar now = Calendar.instance
        now.add(Calendar.MILLISECOND, -this.config.metricFreshness-1000)

        def m = new Metric(
                replicationControllerId: "test",
                podId: "test-aqex",
                metricName: "value",
                metricTimestamp: now.time,
                metricValue: 100.0
                )

        metricsService.postMetric(m)
    }

    @Test
    void testAverage() {
        Calendar now = Calendar.instance
        now.add(Calendar.MILLISECOND, -1000)

        def metrics = []
        metrics << new Metric(
                replicationControllerId: "test",
                podId: "test-aqex",
                metricName: "value",
                metricTimestamp: now.time,
                metricValue: 100.0
                )

        now.add(Calendar.MILLISECOND, -1000)
        metrics << new Metric(
                replicationControllerId: "test",
                podId: "test-aqex",
                metricName: "value",
                metricTimestamp: now.time,
                metricValue: 50.0
                )

        metrics << new Metric(
                replicationControllerId: "test",
                podId: "test-kdkl",
                metricName: "value",
                metricTimestamp: now.time,
                metricValue: 25.0
                )

        now.add(Calendar.MILLISECOND, -2000)
        metrics << new Metric(
                replicationControllerId: "test",
                podId: "test-aqex",
                metricName: "value",
                metricTimestamp: now.time,
                metricValue: 10.0
                )

        metrics << new Metric(
                replicationControllerId: "test",
                podId: "test-zcjk",
                metricName: "value",
                metricTimestamp: now.time,
                metricValue: 10.0
                )

        metrics.each {
            metricsService.postMetric(it)
        }

        def stat = metricsService.getMetricStat("test", "value", 3000)
        assertEquals(100.0, stat.max, 0.0)
        assertEquals(25.0, stat.min, 0.0)
        assertEquals(2, stat.hosts)
        assertEquals(58.0, stat.average, 0.5)
    }

}
