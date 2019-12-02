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
import java.nio.charset.StandardCharsets;
import org.apache.flink.api.common.serialization.AbstractDeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EventDeserializationSchema extends AbstractDeserializationSchema<Event> {

  private static final Logger LOG = LoggerFactory.getLogger(EventDeserializationSchema.class);

  @Override
  public Event deserialize(byte[] bytes) {
    try {
      Event event = Event.parseEvent(bytes);

      if (event instanceof WatermarkEvent) {
        LOG.debug("parsed WatermarkEvent: {}", ((WatermarkEvent) event).watermark);
      }

      return event;
    } catch (Exception e) {
      LOG.debug("cannot parse event '{}'", new String(bytes, StandardCharsets.UTF_8), e);

      return null;
    }
  }

  @Override
  public boolean isEndOfStream(Event event) {
    return false;
  }

  @Override
  public TypeInformation<Event> getProducedType() {
    return TypeExtractor.getForClass(Event.class);
  }

}
