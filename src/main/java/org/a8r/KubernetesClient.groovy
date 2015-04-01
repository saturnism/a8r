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

import java.security.SecureRandom
import java.security.cert.X509Certificate

import javax.annotation.PostConstruct
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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

    @Value("#{environment.KUBERNETES_SERVICE_USERNAME}")
    String username

    @Value("#{environment.KUBERNETES_SERVICE_PASSWORD}")
    String password

    String version = "v1beta1"

    @Value("#{environment.KUBERNETES_SERVICE_INSECURE}")
    boolean insecure = false

    private RestTemplate restTemplate = new RestTemplate();

    private HttpHeaders headers;

    @PostConstruct
    init() {
        if (insecure) {
            registerInsecureTrustManager()
        }

        def headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + "$username:$password".bytes.encodeBase64().toString())
        this.headers = HttpHeaders.readOnlyHttpHeaders(headers)
    }

    private registerInsecureTrustManager() {
        // Create a trust manager that does not validate certificate chains
        def insecureTrustManager =
                new X509TrustManager() {
                    X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    void checkClientTrusted(
                            X509Certificate[] certs, String authType) {
                    }
                    void checkServerTrusted(
                            X509Certificate[] certs, String authType) {
                    }
                }

        def insecureHostnameVerifier = new HostnameVerifier() {
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                };

        TrustManager [] trustManagers = [ insecureTrustManager ]
        // Install the all-trusting trust manager
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustManagers, new SecureRandom())
            HttpsURLConnection.defaultSSLSocketFactory = sslContext.socketFactory
            HttpsURLConnection.setDefaultHostnameVerifier(insecureHostnameVerifier);
        } catch (all) {
            log.error "Unable to register insecure SSL trust manager", all
        }
    }

    String getApiBaseUrl() {
        return "https://$host:$port/api/$version"
    }

    Map get(String resourceType, String id = null, String labelQuery = null) {
        def idPath = id == null ? "" : "/$id"
        def labelQueryParam = labelQuery == null ? "" : "?label=" + URLEncoder.encode(labelQuery, "UTF-8")
        return restTemplate.exchange(
                "$apiBaseUrl/$resourceType$idPath$labelQueryParam",
                HttpMethod.GET,
                new HttpEntity(headers),
                Map.class).body
    }

    void put(String resourceType, String id, Map payload) {
        restTemplate.exchange("$apiBaseUrl/$resourceType/$id", HttpMethod.PUT,
                new HttpEntity(payload, headers), Map.class)
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
        conn.setRequestProperty("Authorization", headers["Authorization"][0])

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