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

package com.amazonaws.samples.kaja.taxi.consumer.events.kinesis;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TripEvent extends Event {
  public final long tripId;
  public final double pickupLatitude;
  public final double pickupLongitude;
  public final double dropoffLatitude;
  public final double dropoffLongitude;
  public final double totalAmount;
  public final Instant pickupDatetime;
  public final Instant dropoffDatetime;

  private static final Logger LOG = LoggerFactory.getLogger(TripEvent.class);

  public TripEvent() {
    tripId = 0;
    pickupLatitude = 0;
    pickupLongitude = 0;
    dropoffLatitude = 0;
    dropoffLongitude = 0;
    totalAmount = 0;
    pickupDatetime = Instant.EPOCH;
    dropoffDatetime = Instant.EPOCH;
  }

  @Override
  public long getTimestamp() {
    return dropoffDatetime.toEpochMilli();
  }

  @Override
  public String toString() {
    return "TripEvent{" +
            "tripId=" + tripId +
            ", pickupLatitude=" + pickupLatitude +
            ", pickupLongitude=" + pickupLongitude +
            ", dropoffLatitude=" + dropoffLatitude +
            ", dropoffLongitude=" + dropoffLongitude +
            ", totalAmount=" + totalAmount +
            ", pickupDatetime=" + pickupDatetime +
            ", dropoffDatetime=" + dropoffDatetime +
            '}';
  }
}
