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

import java.util.concurrent.Executor
import java.util.concurrent.Executors;

import org.infinispan.configuration.cache.ConfigurationBuilder
import org.infinispan.configuration.global.GlobalConfigurationBuilder
import org.infinispan.manager.CacheContainer
import org.infinispan.manager.DefaultCacheManager
import org.infinispan.tree.TreeCache
import org.infinispan.tree.TreeCacheFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ComponentScan("org.a8r")
@EnableAutoConfiguration
@EnableAsync
@EnableScheduling
class Application {
    static void main(String[] args) {
        SpringApplication.run(Application, args)
    }
}

@Configuration
class Beans {
    @Bean
    CacheContainer cacheContainer(AutoscalerConfiguration config) {
        def cacheManager = new DefaultCacheManager(
                new GlobalConfigurationBuilder()
                .globalJmxStatistics().allowDuplicateDomains(true)
                .build());

        cacheManager.defineConfiguration("metrics", new ConfigurationBuilder()
                //.expiration().wakeUpInterval((long) (config.metricFreshness / 2)).lifespan(config.metricFreshness * 1000L)
                .invocationBatching().enable()
                .build());

        cacheManager.defineConfiguration("autoscaler", new ConfigurationBuilder()
                .invocationBatching().enable()

                .build());

        return cacheManager;
    }

    @Bean
    TreeCacheFactory treeCacheFactory() { return new TreeCacheFactory() }

    @Bean
    TreeCache metricsCache(TreeCacheFactory treeCacheFactory, CacheContainer cacheContainer) {
        return treeCacheFactory.createTreeCache(cacheContainer.getCache("metrics"))
    }

    @Bean
    TreeCache autoscalerCache(TreeCacheFactory treeCacheFactory, CacheContainer cacheContainer) {
        return treeCacheFactory.createTreeCache(cacheContainer.getCache("autoscaler"))
    }

    @Bean
    @Scope
    Executor autoscaleExecutor() {
        return Executors.newFixedThreadPool(10)
    }
}
