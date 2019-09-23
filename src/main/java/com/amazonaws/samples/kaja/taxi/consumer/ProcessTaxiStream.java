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

package com.amazonaws.samples.kaja.taxi.consumer;

import com.amazonaws.regions.Regions;
import com.amazonaws.samples.kaja.taxi.consumer.events.EventSchema;
import com.amazonaws.samples.kaja.taxi.consumer.events.PunctuatedAssigner;
import com.amazonaws.samples.kaja.taxi.consumer.events.es.AverageTripDuration;
import com.amazonaws.samples.kaja.taxi.consumer.events.es.PickupCount;
import com.amazonaws.samples.kaja.taxi.consumer.events.kinesis.Event;
import com.amazonaws.samples.kaja.taxi.consumer.events.kinesis.TripEvent;
import com.amazonaws.samples.kaja.taxi.consumer.operators.CountByGeoHash;
import com.amazonaws.samples.kaja.taxi.consumer.operators.TripDurationToAverageTripDuration;
import com.amazonaws.samples.kaja.taxi.consumer.operators.TripToGeoHash;
import com.amazonaws.samples.kaja.taxi.consumer.operators.TripToTripDuration;
import com.amazonaws.samples.kaja.taxi.consumer.utils.AmazonElasticsearchSink;
import com.amazonaws.samples.kaja.taxi.consumer.utils.GeoUtils;
import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ProcessTaxiStream {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessTaxiStream.class);
  private static final List<String> MANDATORY_PARAMETERS = Arrays.asList("InputStreamName");

  public static void main(String[] args) throws Exception {
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    ParameterTool parameter = ParameterTool.fromArgs(args);


    Properties kinesisConsumerConfig = new Properties();
    kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_REGION, parameter.get("Region", Regions.getCurrentRegion().getName()));
    kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER, "AUTO");
    kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS, "1000");


    DataStream<Event> kinesisStream = env.addSource(new FlinkKinesisConsumer<>(
        parameter.getRequired("InputStreamName"),
        new EventSchema(),
        kinesisConsumerConfig)
    );


    DataStream<TripEvent> trips = kinesisStream
        .filter(event -> TripEvent.class.isAssignableFrom(event.getClass()))
        .map(event -> (TripEvent) event)
        .filter(GeoUtils::hasValidCoordinates);

    trips.print();


    /*
    DataStream<TripEvent> trips = kinesisStream
        .assignTimestampsAndWatermarks(new PunctuatedAssigner())
        .filter(event -> TripEvent.class.isAssignableFrom(event.getClass()))
        .map(event -> (TripEvent) event)
        .filter(GeoUtils::hasValidCoordinates);


    SingleOutputStreamOperator<PickupCount> pickupCounts = trips
        .map(new TripToGeoHash())
        .keyBy("geoHash")
        .timeWindow(Time.hours(1))
        .apply(new CountByGeoHash());


    DataStream<AverageTripDuration> tripDurations = trips
        .flatMap(new TripToTripDuration())
        .keyBy("pickupGeoHash", "airportCode")
        .timeWindow(Time.hours(1))
        .apply(new TripDurationToAverageTripDuration());


    if (parameter.has("ElasticsearchEndpoint")) {
      final String elasticsearchEndpoint = parameter.get("ElasticsearchEndpoint");
      final String region = parameter.get("Region", Regions.getCurrentRegion().getName());

      pickupCounts.addSink(AmazonElasticsearchSink.buildElasticsearchSink(elasticsearchEndpoint, region, "pickup_count", "pickup_count"));
      tripDurations.addSink(AmazonElasticsearchSink.buildElasticsearchSink(elasticsearchEndpoint, region, "trip_duration", "trip_duration"));
    }


     */

    LOG.info("Reading events from stream {}", parameter.getRequired("InputStreamName"));

    env.execute();
  }


  /*
  public static ParameterTool parametersFromKinesisAnalyticsRuntime() throws IOException {
    ParameterTool parameter = new ParameterTool();

    Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();


    if (applicationProperties == null) {
      throw new RuntimeException("Unable to load application properties from the Kinesis Analytics Runtime. Exiting.");
    }

    Properties flinkProperties = applicationProperties.get("FlinkApplicationProperties");

    if (flinkProperties == null) {
      throw new RuntimeException("Unable to load FlinkApplicationProperties properties from the Kinesis Analytics Runtime. Exiting.");
    }

    flinkProperties.forEach((k, v) -> parameter.);
    ParameterTool.fromMap(flinkProperties.)

    if (! flinkProperties.keySet().containsAll(MANDATORY_PARAMETERS)) {
      LOG.error("Missing mandatory parameters. Expected '{}' but found '{}'. Exiting.",
          String.join(", ", MANDATORY_PARAMETERS),
          flinkProperties.keySet());

      return;
    }

    if (flinkProperties.getProperty("EventTime", "true").equals("true")) {
      env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
    }
  }

   */
}
