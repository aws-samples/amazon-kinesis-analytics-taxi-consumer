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
import com.amazonaws.samples.kaja.taxi.consumer.events.EventDeserializationSchema;
import com.amazonaws.samples.kaja.taxi.consumer.events.TimestampAssigner;
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


public class ProcessTaxiStreamLocal {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessTaxiStreamLocal.class);

  private static final String DEFAULT_STREAM_NAME = "streaming-analytics-workshop";
  private static final String DEFAULT_REGION_NAME = Regions.getCurrentRegion()==null ? "eu-west-1" : Regions.getCurrentRegion().getName();


  public static void main(String[] args) throws Exception {
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();


    //read the parameters specified from the command line
    ParameterTool parameter = ParameterTool.fromArgs(args);


    Properties kinesisConsumerConfig = new Properties();
    //set the region the Kinesis stream is located in
    kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_REGION, parameter.get("Region", DEFAULT_REGION_NAME));
    //obtain credentials through the DefaultCredentialsProviderChain, which includes credentials from the instance metadata
    kinesisConsumerConfig.setProperty(AWSConfigConstants.AWS_CREDENTIALS_PROVIDER, "AUTO");
    //poll new events from the Kinesis stream once every second
    kinesisConsumerConfig.setProperty(ConsumerConfigConstants.SHARD_GETRECORDS_INTERVAL_MILLIS, "1000");


    //create Kinesis source
    DataStream<Event> kinesisStream = env.addSource(new FlinkKinesisConsumer<>(
        //read events from the Kinesis stream passed in as a parameter
        parameter.get("InputStreamName", DEFAULT_STREAM_NAME),
        //deserialize events with EventSchema
        new EventDeserializationSchema(),
        //using the previously defined Kinesis consumer properties
        kinesisConsumerConfig
    ));


    DataStream<TripEvent> trips = kinesisStream
        //remove all events that aren't TripEvents
        .filter(event -> TripEvent.class.isAssignableFrom(event.getClass()))
        //cast Event to TripEvent
        .map(event -> (TripEvent) event)
        //remove all events with geo coordinates outside of NYC
        .filter(GeoUtils::hasValidCoordinates);


    //print trip events to stdout
    trips.print();


    LOG.info("Reading events from stream {}", parameter.get("InputStreamName", DEFAULT_STREAM_NAME));

    env.execute();
  }
}
