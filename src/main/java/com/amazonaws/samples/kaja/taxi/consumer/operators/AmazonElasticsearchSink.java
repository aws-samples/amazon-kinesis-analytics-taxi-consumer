/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may
 * not use this file except in compliance with the License. A copy of the
 * License is located at
 *
 *    http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazonaws.samples.kaja.taxi.consumer.operators;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.google.common.base.Supplier;
import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.streaming.connectors.elasticsearch.ElasticsearchSinkFunction;
import org.apache.flink.streaming.connectors.elasticsearch.RequestIndexer;
import org.apache.flink.streaming.connectors.elasticsearch.util.RetryRejectedExecutionFailureHandler;
import org.apache.flink.streaming.connectors.elasticsearch6.ElasticsearchSink;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vc.inreach.aws.request.AWSSigner;
import vc.inreach.aws.request.AWSSigningRequestInterceptor;

public class AmazonElasticsearchSink {
  private static final String ES_SERVICE_NAME = "es";

  private static final int FLUSH_MAX_ACTIONS = 10_000;
  private static final long FLUSH_INTERVAL_MILLIS = 1_000;
  private static final int FLUSH_MAX_SIZE_MB = 1;

  private static final Logger LOG = LoggerFactory.getLogger(AmazonElasticsearchSink.class);

  public static <T> ElasticsearchSink<T> buildElasticsearchSink(String elasticsearchEndpoint, String region, String indexName, String type) {
    final List<HttpHost> httpHosts = Arrays.asList(HttpHost.create(elasticsearchEndpoint));
    final SerializableAWSSigningRequestInterceptor requestInterceptor = new SerializableAWSSigningRequestInterceptor(region);

    ElasticsearchSink.Builder<T> esSinkBuilder = new ElasticsearchSink.Builder<>(
        httpHosts,
        new ElasticsearchSinkFunction<T>() {
          public IndexRequest createIndexRequest(T element) {
            return Requests.indexRequest()
                .index(indexName)
                .type(type)
                .source(element.toString(), XContentType.JSON);
          }

          @Override
          public void process(T element, RuntimeContext ctx, RequestIndexer indexer) {
            indexer.add(createIndexRequest(element));
          }
        }
    );

    esSinkBuilder.setBulkFlushMaxActions(FLUSH_MAX_ACTIONS);
    esSinkBuilder.setBulkFlushInterval(FLUSH_INTERVAL_MILLIS);
    esSinkBuilder.setBulkFlushMaxSizeMb(FLUSH_MAX_SIZE_MB);
    esSinkBuilder.setBulkFlushBackoff(true);

    esSinkBuilder.setRestClientFactory(
        restClientBuilder -> restClientBuilder.setHttpClientConfigCallback(callback -> callback.addInterceptorLast(requestInterceptor))
    );

    esSinkBuilder.setFailureHandler(new RetryRejectedExecutionFailureHandler());

    return esSinkBuilder.build();
  }


  static class SerializableAWSSigningRequestInterceptor implements HttpRequestInterceptor, Serializable {
    private final String region;
    private transient AWSSigningRequestInterceptor requestInterceptor;

    public SerializableAWSSigningRequestInterceptor(String region) {
      this.region = region;
    }

    @Override
    public void process(HttpRequest httpRequest, HttpContext httpContext) throws HttpException, IOException {
      if (requestInterceptor == null) {
        final Supplier<LocalDateTime> clock = () -> LocalDateTime.now(ZoneOffset.UTC);
        final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
        final AWSSigner awsSigner = new AWSSigner(credentialsProvider, region, ES_SERVICE_NAME, clock);

        requestInterceptor = new AWSSigningRequestInterceptor(awsSigner);
      }

      requestInterceptor.process(httpRequest, httpContext);
    }
  }
}

