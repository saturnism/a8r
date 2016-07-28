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

import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.Executor
import java.util.concurrent.Executors

import javax.net.ssl.DefaultSSLSocketFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

import org.infinispan.configuration.cache.ConfigurationBuilder
import org.infinispan.configuration.global.GlobalConfigurationBuilder
import org.infinispan.manager.CacheContainer
import org.infinispan.manager.DefaultCacheManager
import org.infinispan.tree.TreeCache
import org.infinispan.tree.TreeCacheFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate

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
@Slf4j
class Beans {
    @Bean
    CacheContainer cacheContainer(AutoscalerConfiguration config) {
        def cacheManager = new DefaultCacheManager(
                new GlobalConfigurationBuilder()
                .globalJmxStatistics().allowDuplicateDomains(true)
                .build());

        cacheManager.defineConfiguration("metrics", new ConfigurationBuilder()
                .expiration()
                .wakeUpInterval((long) (config.metricFreshness / 2))
                .enableReaper()
                .lifespan(config.metricFreshness)
                .invocationBatching().enable()
                .build());

        cacheManager.defineConfiguration("autoscaler", new ConfigurationBuilder()
                .invocationBatching().enable()
                //.persistence().addSingleFileStore()
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

    def getInputStreamFromFile(String file) throws FileNotFoundException {
        if (file != null) {
            return new FileInputStream(file);
        }
        return null;
    }

    @Bean
    String token(@Value("#{environment.KUBERNETES_TOKEN_FILE}") tokenFile) {
        if (!tokenFile) { return ""; }
        return new File(tokenFile).text
    }

    @Bean
    HttpHeaders httpHeaders(String token) {
        def headers = new HttpHeaders();
        if (token) {
            headers.set("Authorization", "Bearer $token")
        }
        return HttpHeaders.readOnlyHttpHeaders(headers)
    }

    @Bean
    SSLSocketFactory sslSocketFactory(@Value("#{environment.KUBERNETES_CA_CERT_FILE}") String caCertFile) throws Exception {
        if (caCertFile == null) {
            return new DefaultSSLSocketFactory();
        }

        try {
            InputStream pemInputStream = getInputStreamFromFile(caCertFile);
            CertificateFactory certFactory = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) certFactory.generateCertificate(pemInputStream);

            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(null);

            String alias = cert.getSubjectX500Principal().getName();
            trustStore.setCertificateEntry(alias, cert);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);


            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagerFactory.getTrustManagers(), null);
            log.info "Using CA from $caCertFile"
            return context.getSocketFactory();
        } catch (Exception e) {
            log.error("Could not create trust manager for " + caCertFile, e);
            throw e;
        }
    }

    @Bean
    RestTemplate restTemplate(SSLSocketFactory sslSocketFactory, HttpHeaders headers) {
        def restTemplate = new RestTemplate()

        restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {
                    protected void prepareConnection(HttpURLConnection conn, String httpMethod) throws IOException {
                        if (headers["Authorization"]) {
                            conn.setRequestProperty("Authorization", headers.getFirst("Authorization"))
                        }
                        if (sslSocketFactory && conn instanceof HttpsURLConnection) {
                            ((HttpsURLConnection) conn).setSSLSocketFactory(sslSocketFactory)
                        }
                        super.prepareConnection(conn, httpMethod);
                    };
                });

        return restTemplate;
    }
}
