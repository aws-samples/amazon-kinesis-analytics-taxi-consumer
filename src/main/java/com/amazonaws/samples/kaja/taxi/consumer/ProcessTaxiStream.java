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
import com.amazonaws.samples.kaja.taxi.consumer.operators.*;
import com.amazonaws.samples.kaja.taxi.consumer.utils.GeoUtils;
import com.amazonaws.samples.kaja.taxi.consumer.utils.ParameterToolUtils;
import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import java.util.Map;
import java.util.Properties;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.LocalStreamEnvironment;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer;
import org.apache.flink.streaming.connectors.kinesis.config.AWSConfigConstants;
import org.apache.flink.streaming.connectors.kinesis.config.ConsumerConfigConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ProcessTaxiStream {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessTaxiStream.class);

  private static final String DEFAULT_STREAM_NAME = "kda-java-workshop";
  private static final String DEFAULT_REGION_NAME = Regions.getCurrentRegion()==null ? "eu-west-1" : Regions.getCurrentRegion().getName();


  public static void main(String[] args) throws Exception {
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    ParameterTool parameter;

    if (env instanceof LocalStreamEnvironment) {
      //read the parameters specified from the command line
      parameter = ParameterTool.fromArgs(args);
    } else {
      //read the parameters from the Kinesis Analytics environment
      Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();

      if (applicationProperties == null) {
        throw new RuntimeException("Unable to load application properties from the Kinesis Analytics Runtime. Exiting.");
      }

      Properties flinkProperties = applicationProperties.get("FlinkApplicationProperties");

      if (flinkProperties == null) {
        throw new RuntimeException("Unable to load FlinkApplicationProperties properties from the Kinesis Analytics Runtime. Exiting.");
      }

      parameter = ParameterToolUtils.fromApplicationProperties(flinkProperties);
    }

    
    //set Kinesis consumer properties
    Properties kinesisConsumerConfig = new Properties();
    //set the region the Kinesis stream is located in
    kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_REGION, parameter.get("Region", DEFAULT_REGION_NAME));
    //obtain credentials through the DefaultCredentialsProviderChain, which includes the instance metadata
    kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER, "AUTO");
    //poll new events from the Kinesis stream once every second
    kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS, "1000");


    //enable event time processing
    if (parameter.get("EventTime", "true").equals("true")) {
      env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
    }


    //create Kinesis source
    DataStream<Event> kinesisStream = env.addSource(new FlinkKinesisConsumer<>(
        //read events from the Kinesis stream passed in as a parameter
        parameter.get("InputStreamName", DEFAULT_STREAM_NAME),
        //deserialize events with EventSchema
        new EventSchema(),
        //using the previously defined properties
        kinesisConsumerConfig
    ));


    DataStream<TripEvent> trips = kinesisStream
        //extract watermarks from watermark events
        .assignTimestampsAndWatermarks(new PunctuatedAssigner())
        //remove all events that aren't TripEvents
        .filter(event -> TripEvent.class.isAssignableFrom(event.getClass()))
        //cast Event to TripEvent
        .map(event -> (TripEvent) event)
        //remove all events with geo coordinates outside of NYC
        .filter(GeoUtils::hasValidCoordinates);


    DataStream<PickupCount> pickupCounts = trips
        //compute geo hash for every event
        .map(new TripToGeoHash())
        .keyBy("geoHash")
        //collect all events in a one hour window
        .timeWindow(Time.hours(1))
        //count events per geo hash in the one hour window
        .apply(new CountByGeoHash());


    DataStream<AverageTripDuration> tripDurations = trips
        .flatMap(new TripToTripDuration())
        .keyBy("pickupGeoHash", "airportCode")
        .timeWindow(Time.hours(1))
        .apply(new TripDurationToAverageTripDuration());


    if (parameter.has("ElasticsearchEndpoint")) {
      final String elasticsearchEndpoint = parameter.get("ElasticsearchEndpoint");
      final String region = parameter.get("Region", DEFAULT_REGION_NAME);

      pickupCounts.addSink(AmazonElasticsearchSink.buildElasticsearchSink(elasticsearchEndpoint, region, "pickup_count", "pickup_count"));
      tripDurations.addSink(AmazonElasticsearchSink.buildElasticsearchSink(elasticsearchEndpoint, region, "trip_duration", "trip_duration"));
    }


    LOG.info("Reading events from stream {}", parameter.get("InputStreamName", DEFAULT_STREAM_NAME));

    env.execute();
  }
}