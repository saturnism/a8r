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

import groovy.util.logging.Slf4j

import java.util.concurrent.Executor

import javax.annotation.PostConstruct;
import javax.validation.Valid
import javax.validation.constraints.NotNull

import org.hibernate.validator.constraints.NotEmpty
import org.infinispan.tree.Fqn
import org.infinispan.tree.TreeCache
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpClientErrorException

class AutoscalerDefintion {
    @NotEmpty
    String replicationControllerId

    @NotEmpty
    String metricName

    @NotNull
    Double threshold

    @NotNull
    Integer duration

    Integer minReplicas = 1

    @NotNull
    Integer maxReplicas
}

@RestController
@RequestMapping("/a8r/autoscaler")
@Slf4j
class AutoscalerService {
    @Autowired
    private MetricsService metricsService

    @Autowired
    private TreeCache autoscalerCache

    @Autowired
    private KubernetesClient kubernetesClient

    int calculateScale(AutoscalerDefintion definition) {
        def stat = metricsService.getMetricStat(definition.replicationControllerId,
                definition.metricName, definition.duration)

        log.info "stat: {}", stat.properties

        if (stat.hosts > 0 && stat.average > definition.threshold && stat.hosts < definition.maxReplicas) return 1
        if (stat.hosts > definition.minReplicas && stat.average * stat.count / (stat.hosts - 1) < definition.threshold) return -1
        return 0
    }

    int calculateScale(AutoscalerDefintion definition, Map rc) {
        def desired = rc.desiredState.replicas
        def current = rc.currentState.replicas

        def result = calculateScale(definition)

        if (result == 0) {
            return 0
        } else if (result > 0) {
            // TODO: account for differences
            if (desired > current) {
                return 0
            } else {
                return result
            }
        } else if (result < 0) {
            if (desired < current) {
                return 0
            } else {
                return result
            }
        }
    }

    @RequestMapping(method=[RequestMethod.POST])
    @ResponseBody String addDefinition(@Valid @RequestBody AutoscalerDefintion definition) {
        def fqn = new Fqn(definition.replicationControllerId)
        if (autoscalerCache.exists(fqn)) {
            throw new AlreadyExistsException("$fqn already exists")
        }

        def map = definition.properties
        map.remove("class")
        autoscalerCache.put(fqn, map)
        return definition.replicationControllerId
    }

    @RequestMapping(method=[RequestMethod.PUT])
    @ResponseBody String updateDefinition(@Valid @RequestBody AutoscalerDefintion definition) {
        def fqn = new Fqn(definition.replicationControllerId)
        if (!autoscalerCache.exists(fqn)) {
            throw new NotFoundException("$fqn doesn't exist")
        }

        def map = definition.properties
        map.remove("class")
        autoscalerCache.put(fqn, map)
        return definition.replicationControllerId
    }

    @RequestMapping(method=[RequestMethod.GET], value="/{replicationControllerId}")
    @ResponseBody AutoscalerDefintion getDefinition(@PathVariable String replicationControllerId) {
        def fqn = new Fqn(replicationControllerId)
        if (!autoscalerCache.exists(fqn)) {
            throw new NotFoundException("$fqn doesn't exists")
        }

        return autoscalerCache.getNode(fqn).data as AutoscalerDefintion
    }

    @RequestMapping(method=[RequestMethod.DELETE], value="/{replicationControllerId}")
    @ResponseBody AutoscalerDefintion deleteDefinition(@PathVariable String replicationControllerId) {
        def fqn = new Fqn(replicationControllerId)
        if (!autoscalerCache.exists(fqn)) {
            throw new NotFoundException("$fqn doesn't exists")
        }

        def definition = autoscalerCache.getNode(fqn).data as AutoscalerDefintion
        autoscalerCache.removeNode(fqn)
        return definition
    }

    @Scheduled(fixedRateString = "\${autoscaler.wakeupInterval}")
    void autoscale() {
        autoscalerCache.root.children.each {
            scale(it.data as AutoscalerDefintion)
        }
    }

    @Async
    void scale(AutoscalerDefintion definition) {
        try {
            def rc = kubernetesClient.get("replicationControllers", definition.replicationControllerId)
            def result = calculateScale(definition, rc)
            log.info "Autoscaling: {}, Result: {}", definition.properties, result

            rc.desiredState.replicas += result
            kubernetesClient.put("replicationControllers", rc.id, rc)
        } catch (HttpClientErrorException e) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                log.warn "{} was not found in Kubernetes ", definition.replicationControllerId
            } else {
                log.error "Unknown error occured", e
            }
        }
    }
}