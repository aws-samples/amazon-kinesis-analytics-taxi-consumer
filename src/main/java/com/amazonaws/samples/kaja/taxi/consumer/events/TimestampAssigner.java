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

package com.amazonaws.samples.kaja.taxi.consumer.events;

import com.amazonaws.samples.kaja.taxi.consumer.events.kinesis.Event;
import com.amazonaws.samples.kaja.taxi.consumer.events.kinesis.WatermarkEvent;
import org.apache.flink.streaming.api.functions.AssignerWithPunctuatedWatermarks;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TimestampAssigner implements AssignerWithPunctuatedWatermarks<Event> {
  private static final Logger LOG = LoggerFactory.getLogger(TimestampAssigner.class);

  @Override
  public long extractTimestamp(Event element, long previousElementTimestamp) {
    return element.getTimestamp();
  }

  @Override
  public Watermark checkAndGetNextWatermark(Event lastElement, long extractedTimestamp) {
    return lastElement instanceof WatermarkEvent ? new Watermark(extractedTimestamp) : null;
  }
}