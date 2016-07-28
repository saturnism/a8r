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

import static org.codehaus.groovy.runtime.DefaultGroovyMethodsSupport.closeQuietly
import groovy.util.logging.Slf4j

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate

import javax.annotation.PostConstruct
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

import com.fasterxml.jackson.databind.ObjectMapper

interface WatchCallback {
    void eventReceived(Map event)
}

@Component
@Slf4j
class KubernetesClient {
    @Value("#{environment.KUBERNETES_SERVICE_HOST}")
    String host = "127.0.0.1"

    @Value("#{environment.KUBERNETES_SERVICE_PORT}")
    Integer port = 443

    String version = "v1"

    @Autowired
    RestTemplate restTemplate

    @Autowired
    HttpHeaders headers

    @Autowired
    SSLSocketFactory sslSocketFactory

    String getApiBaseUrl() {
        return "https://$host:$port/api/$version"
    }

    Map get(String resourceType, String id = null, String labelQuery = null, String namespace = "default") {
        def idPath = id == null ? "" : "/$id"
        def labelQueryParam = labelQuery == null ? "" : "?label=" + URLEncoder.encode(labelQuery, "UTF-8")

        return restTemplate.getForEntity("$apiBaseUrl/namespaces/$namespace/$resourceType$idPath$labelQueryParam", Map.class).body
    }

    void put(String resourceType, String id, Map payload, String namespace = "default") {
        restTemplate.exchange("$apiBaseUrl/namespaces/$namespace/$resourceType/$id", HttpMethod.PUT,
                new HttpEntity(payload), Map.class)
    }

    @Async
    void watchContinuously(String resourceType, long reconnectDelay, WatchCallback callback) {
        while (true) {
            try {
                watch(resourceType, callback)
            } catch (all) {
                log.warn "Error watching {}", resourceType, all
            } finally {
                sleep reconnectDelay
            }
        }
    }

    void watch(String resourceType, WatchCallback callback) {
        def url = "$apiBaseUrl/watch/$resourceType"
        HttpURLConnection conn = new URL(url).openConnection()

        if (headers["Authorization"]) {
            conn.setRequestProperty("Authorization", headers.getFirst("Authorization"))
        }

        if (sslSocketFactory && conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(sslSocketFactory)
        }

        def is

        try {
            conn.connect()
            is = conn.inputStream

            def mapper = new ObjectMapper()
            def factory = mapper.factory
            def parser = factory.createParser(is)
            def it = parser.readValuesAs(Map.class)
            it.each {
                try {
                    callback.eventReceived(it)
                } catch (all) {
                    log.error "Error calling eventReceived callback", all
                }
            }
        } catch (all) {
            log.error "Error connection to {}", url, all
        } finally {
            closeQuietly(is)
            conn.disconnect()
        }
    }
}