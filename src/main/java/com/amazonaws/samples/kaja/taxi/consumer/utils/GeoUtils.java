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

package com.amazonaws.samples.kaja.taxi.consumer.utils;

import ch.hsr.geohash.BoundingBox;
import ch.hsr.geohash.WGS84Point;
import com.amazonaws.samples.kaja.taxi.consumer.events.kinesis.TripEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class GeoUtils {
  private static final BoundingBox NYC = new BoundingBox(new WGS84Point(40.878,-74.054), new WGS84Point(40.560,-73.722));
  private static final BoundingBox JFK = new BoundingBox(new WGS84Point(40.654,-73.800), new WGS84Point(40.632,-73.761));
  private static final BoundingBox LGA = new BoundingBox(new WGS84Point(40.778,-73.881), new WGS84Point(40.766,-73.859));

  private static final Logger LOG = LoggerFactory.getLogger(GeoUtils.class);


  public static boolean hasValidCoordinates(TripEvent trip) {
    try {
      WGS84Point pickup = new WGS84Point(trip.pickupLatitude, trip.pickupLongitude);
      WGS84Point dropoff = new WGS84Point(trip.dropoffLatitude, trip.dropoffLongitude);

      return NYC.contains(pickup) && NYC.contains(dropoff);
    } catch (IllegalArgumentException e) {
      LOG.debug("cannot parse coordinates for event {}", trip, e);

      return false;
    }
  }

  public static boolean nearJFK(double latitude, double longitude) {
    try {
      return JFK.contains(new WGS84Point(latitude, longitude));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public static boolean nearLGA(double latitude, double longitude) {
    try {
      return LGA.contains(new WGS84Point(latitude, longitude));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

}
