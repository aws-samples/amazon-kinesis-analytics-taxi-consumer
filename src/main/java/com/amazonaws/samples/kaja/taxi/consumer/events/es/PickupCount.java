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

package com.amazonaws.samples.kaja.taxi.consumer.events.es;


public class PickupCount extends Document {
  public final String location;
  public final long pickupCount;

  public PickupCount(String location, long pickupCount, long timestamp) {
    super(timestamp);

    this.pickupCount = pickupCount;
    this.location = location;
  }

  @Override
  public String toString() {
    return "PickupCount{" +
        "location='" + location + '\'' +
        ", pickupCount=" + pickupCount +
        ", timestamp=" + timestamp +
        '}';
  }
}
