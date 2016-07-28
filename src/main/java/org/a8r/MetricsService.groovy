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

import groovy.util.logging.Slf4j;

import javax.annotation.PostConstruct;
import javax.validation.Valid
import javax.validation.constraints.NotNull

import org.hibernate.validator.constraints.NotEmpty
import org.infinispan.tree.Fqn
import org.infinispan.tree.TreeCache
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController


class MetricStat {
    int hosts
    int count
    double average
    double max
    double min
}

class Metric {
    @NotEmpty
    String replicationControllerId

    @NotEmpty
    String podId

    @NotEmpty
    String metricName

    @NotNull
    Double metricValue

    @NotNull
    Date metricTimestamp
}

@RestController
@RequestMapping("/a8r/metrics")
@Slf4j
class MetricsService {
    @Autowired
    private TreeCache metricsCache

    @Autowired
    private AutoscalerConfiguration config

    @Autowired
    private KubernetesClient client

    @PostConstruct
    void init() {
        client.watchContinuously("pods", 2000, new WatchCallback() {
                    void eventReceived(Map event) {
                        if (event.object.kind != 'Pod') return;

                        log.info "Event: {}, Object: {}", event.type, event.object

                        switch (event.type) {
                            case "DELETED":
                                def pod = event.object
                                if (!pod.metadata.generateName) {
                                    break;
                                }

                                def replicationControllerId = pod.metadata.generateName[0..-2]
                                def fqn = new Fqn(replicationControllerId, pod.metadata.name)
                                if (!metricsCache.exists(fqn)) {
                                    break;
                                }
                                metricsCache.remove(fqn)
                                log.info "Removed metrics for {}", fqn
                        }
                    }
                })
    }

    @RequestMapping(method=[RequestMethod.POST])
    @ResponseBody String postMetric(@RequestBody @Valid Metric metric) {
        Calendar earliest = Calendar.instance;
        earliest.add(Calendar.MILLISECOND, -config.metricFreshness)
        if (earliest.getTime().after(metric.metricTimestamp)) {
            log.error "metricsTimestamp is too early: {}", metric.properties
            throw new TooEarlyException("metricsTimestamp is too early")
        }

        def fqn = new Fqn(metric.replicationControllerId, metric.metricName, metric.podId, metric.metricTimestamp.format("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))
        def map = metric.properties
        map.remove("class")
        metricsCache.put(fqn, map)
        log.info "Posted Metric: {} {}", fqn, map
        return fqn
    }

    @RequestMapping(method=[RequestMethod.GET], value="{replicationControllerId}/{metricName}")
    MetricStat getMetricStat(
            @PathVariable String replicationControllerId,
            @PathVariable String metricName,
            @RequestParam(required=false, defaultValue="#{autoscalerConfiguration.metricFreshness}") int duration) {

        Calendar now = Calendar.instance
        Calendar past = Calendar.instance
        past.add(Calendar.MILLISECOND, -duration)

        def fqn = new Fqn(replicationControllerId, metricName)
        if (!metricsCache.exists(fqn)) return new MetricStat()

        def metric = metricsCache.getNode(fqn)
        def hosts = metric.children;

        def averages = [:]
        boolean minSet
        boolean maxSet
        double min
        double max
        int totalCount = 0
        double totalSum = 0.0

        hosts.each {
            int count = 0;
            double sum = 0;
            it.children.each {
                Metric m = it.data

                if (!m.metricTimestamp) {
                    return
                }

                if (m.metricTimestamp.before(past.time) || m.metricTimestamp.after(now.time)) { return }

                //if (m.processed) { return }
                //it.put("processed", true)

                min = !minSet ? m.metricValue : Math.min(min, m.metricValue)
                max = !maxSet ? m.metricValue : Math.max(max, m.metricValue)
                minSet = true
                maxSet = true

                count++
                sum += m.metricValue
            }
            if (count == 0) { return }
            averages[it.fqn] = sum / (double) count
            totalCount += count
            totalSum += sum
        }

        log.info "fqn: {}, count: {}", fqn, totalCount

        if (totalCount == 0) return new MetricStat()

        double sum = 0;
        averages.each {
            sum += it.value
        }
        return new MetricStat(
                min: min,
                max: max,
                count: totalCount,
                hosts: averages.size(),
                average: totalSum / totalCount
                )
    }
}
