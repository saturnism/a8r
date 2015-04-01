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

import org.a8r.AutoscalerService;
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
import org.junit.After;
import org.junit.Before
import org.junit.Test


class MetricsSimulator {
    String replicationControllerId
    String podName
    String metricName

    int timeRange = -5000
    double lowValue
    double highValue

    Metric next() {
        Calendar now = Calendar.instance;
        now.add(Calendar.MILLISECOND, (int)(Math.random() * timeRange))

        return new Metric(
                replicationControllerId: this.replicationControllerId,
                podId: this.podName,
                metricName: this.metricName,
                metricTimestamp: now.time,
                metricValue: lowValue + Math.random() * highValue
                )
    }
}

class BaseTest {
    EmbeddedCacheManager cacheManager
    TreeCache metricsCache
    MetricsService metricsService
    AutoscalerService autoscaler
    AutoscalerConfiguration config

    @Before
    void setup() {
        this.config = new AutoscalerConfiguration(
                metricFreshness: 5000
                )
        this.cacheManager = new DefaultCacheManager(
                new GlobalConfigurationBuilder()
                .globalJmxStatistics().allowDuplicateDomains(true)
                .build(),
                new ConfigurationBuilder()
                .invocationBatching().enable()
                .transaction().transactionManagerLookup(new DummyTransactionManagerLookup())
                .build()
                )
        TreeCacheFactory factory = new TreeCacheFactory()
        this.metricsCache = factory.createTreeCache(this.cacheManager.getCache())
        this.metricsService = new MetricsService(
                config: this.config,
                metricsCache: this.metricsCache
                )
        this.autoscaler = new AutoscalerService(
                metricsService: this.metricsService
                )
    }

    @After
    void tearDown() {
        this.cacheManager.stop();
    }
}
