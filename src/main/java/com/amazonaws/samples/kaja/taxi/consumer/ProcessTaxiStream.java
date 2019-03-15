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

import ch.hsr.geohash.GeoHash;
import com.amazonaws.samples.kaja.taxi.consumer.events.EventSchema;
import com.amazonaws.samples.kaja.taxi.consumer.events.PunctuatedAssigner;
import com.amazonaws.samples.kaja.taxi.consumer.events.es.PickupCount;
import com.amazonaws.samples.kaja.taxi.consumer.events.es.TripDuration;
import com.amazonaws.samples.kaja.taxi.consumer.events.kinesis.Event;
import com.amazonaws.samples.kaja.taxi.consumer.events.kinesis.TripEvent;
import com.amazonaws.samples.kaja.taxi.consumer.utils.AmazonElasticsearchSink;
import com.amazonaws.samples.kaja.taxi.consumer.utils.GeoUtils;
import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import com.google.common.collect.Iterables;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.StreamSupport;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ProcessTaxiStream {
  private static final String DEFAULT_REGION = "eu-west-1";

  private static final Logger LOG = LoggerFactory.getLogger(ProcessTaxiStream.class);
  private static final List<String> MANDATORY_PARAMETERS = Arrays.asList("InputStreamName");

  public static void main(String[] args) throws Exception {
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    Map<String, Properties> applicationProperties;

    if (env instanceof LocalStreamEnvironment) {
      applicationProperties  = KinesisAnalyticsRuntime.getApplicationProperties(ProcessTaxiStream.class.getClassLoader().getResource("testProperties.json").getPath());
    } else {
      applicationProperties  = KinesisAnalyticsRuntime.getApplicationProperties();
    }

    if (applicationProperties == null) {
      LOG.error("Unable to load application properties from the Kinesis Analytics Runtime. Exiting.");

      return;
    }

    Properties flinkProperties = applicationProperties.get("FlinkApplicationProperties");

    if (flinkProperties == null) {
      LOG.error("Unable to load FlinkApplicationProperties properties from the Kinesis Analytics Runtime. Exiting.");

      return;
    }

    if (! flinkProperties.keySet().containsAll(MANDATORY_PARAMETERS)) {
      LOG.error("Missing mandatory parameters. Expected '{}' but found '{}'. Exiting.",
              String.join(", ", MANDATORY_PARAMETERS),
              flinkProperties.keySet());

      return;
    }

    if (flinkProperties.getProperty("EventTime", "true").equals("true")) {
      env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
    }


    Properties kinesisConsumerConfig = new Properties();
    kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_REGION, flinkProperties.getProperty("Region", DEFAULT_REGION));
    kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER, "AUTO");
    kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS, "1000");


    DataStream<Event> kinesisStream = env.addSource(new FlinkKinesisConsumer<>(
        flinkProperties.getProperty("InputStreamName"),
        new EventSchema(),
        kinesisConsumerConfig)
    );


    DataStream<TripEvent> trips = kinesisStream
        .assignTimestampsAndWatermarks(new PunctuatedAssigner())
        .filter(event -> TripEvent.class.isAssignableFrom(event.getClass()))
        .map(event -> (TripEvent) event)
        .filter(GeoUtils::hasValidCoordinates);


    SingleOutputStreamOperator<PickupCount> pickupCounts = trips
        .map(new MapFunction<TripEvent, Tuple3<String, Long, String>>() {
          @Override
          public Tuple3<String, Long, String> map(TripEvent tripEvent) throws Exception {
            return new Tuple3<>(GeoHash.geoHashStringWithCharacterPrecision(tripEvent.pickupLatitude, tripEvent.pickupLongitude, 7), tripEvent.tripId, tripEvent.dropoffDatetime.toString());
          }
        })
        .keyBy(0)
        .timeWindow(Time.hours(1))
        .apply(new WindowFunction<Tuple3<String,Long,String>, PickupCount, Tuple, TimeWindow>() {
          @Override
          public void apply(Tuple tuple, TimeWindow timeWindow, Iterable<Tuple3<String,Long,String>> iterable, Collector<PickupCount> collector) throws Exception {
            long count = Iterables.size(iterable);
            String position = Iterables.get(iterable, 0).f0;

            collector.collect(new PickupCount(position, count, timeWindow.getEnd()));
          }
        });


    DataStream<TripDuration> tripDurations = trips
        .flatMap(new FlatMapFunction<TripEvent, Tuple3<String, String, Long>>() {
          @Override
          public void flatMap(TripEvent tripEvent, Collector<Tuple3<String, String, Long>> collector) {
            String pickupLocation = GeoHash.geoHashStringWithCharacterPrecision(tripEvent.pickupLatitude, tripEvent.pickupLongitude, 6);
            long tripDuration = Duration.between(tripEvent.pickupDatetime, tripEvent.dropoffDatetime).toMinutes();

            if (GeoUtils.nearJFK(tripEvent.dropoffLatitude, tripEvent.dropoffLongitude)) {
              collector.collect(new Tuple3<>(pickupLocation, "JFK", tripDuration));
            } else if (GeoUtils.nearLGA(tripEvent.dropoffLatitude, tripEvent.dropoffLongitude)) {
              collector.collect(new Tuple3<>(pickupLocation, "LGA", tripDuration));
            }
          }
        })
        .keyBy(0,1)
        .timeWindow(Time.hours(1))
        .apply(new WindowFunction<Tuple3<String, String, Long>, TripDuration, Tuple, TimeWindow>() {
          @Override
          public void apply(Tuple tuple, TimeWindow timeWindow, Iterable<Tuple3<String, String, Long>> iterable, Collector<TripDuration> collector) {
            if (Iterables.size(iterable) > 1) {
              String location = Iterables.get(iterable, 0).f0;
              String airportCode = Iterables.get(iterable, 0).f1;

              long sumDuration = StreamSupport
                  .stream(iterable.spliterator(), false)
                  .mapToLong(trip -> trip.f2)
                  .sum();

              double avgDuration = (double) sumDuration / Iterables.size(iterable);

              collector.collect(new TripDuration(location, airportCode, sumDuration, avgDuration, timeWindow.getEnd()));
            }
          }
        });


    if (flinkProperties.containsKey("ElasticsearchEndpoint")) {
      final String elasticsearchEndpoint = flinkProperties.getProperty("ElasticsearchEndpoint");
      final String region = flinkProperties.getProperty("Region", DEFAULT_REGION);

      pickupCounts.addSink(AmazonElasticsearchSink.buildElasticsearchSink(elasticsearchEndpoint, region, "pickup_count", "pickup_count"));
      tripDurations.addSink(AmazonElasticsearchSink.buildElasticsearchSink(elasticsearchEndpoint, region, "trip_duration", "trip_duration"));
    }


    LOG.info("Starting to consume events from stream {}", flinkProperties.getProperty("InputStreamName"));

    env.execute();
  }

}
