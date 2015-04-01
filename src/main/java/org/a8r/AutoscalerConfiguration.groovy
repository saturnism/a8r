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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope
class AutoscalerConfiguration {
    @Value("\${metrics.freshness}")
    int metricFreshness

    @Value("\${autoscaler.threshold.duration}")
    int thresholdDuration

    @Value("\${autoscaler.wakeupInterval}")
    int wakeupInterval

    @Value("\${autoscaler.peristence.dir}")
    String cachePersistenceDir
}
