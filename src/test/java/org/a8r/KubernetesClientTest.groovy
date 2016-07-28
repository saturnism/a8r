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
import groovy.util.logging.Slf4j;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class KubernetesClientTest {
    KubernetesClient client

    @Before
    void setup() {
        client = new KubernetesClient(
                host: "104.154.58.185",
                port: 443)
    }

    @After
    void tearDown() {
    }

    @Test
    void testGetReplicationController() {
        /*
         def response = client.get("replicationControllers", "hazelcast")
         println response
         */
    }

    @Test
    void testResizeReplicationController() {
        /*
         def rc = client.get("replicationControllers", "hazelcast")
         rc.desiredState.replicas = 2
         client.put("replicationControllers", "hazelcast", rc)
         */
    }

    @Test
    void testWatchReplicationControllers() {
        /*
         client.watch("pods", new WatchCallback() {
         void eventReceived(Map event) {
         println event
         }
         })
         */
    }
}
