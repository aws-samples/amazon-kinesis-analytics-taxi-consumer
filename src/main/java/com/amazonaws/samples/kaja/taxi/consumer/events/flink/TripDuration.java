package com.amazonaws.samples.kaja.taxi.consumer.events.flink;

public class TripDuration {
  public final long tripDuration;
  public final String pickupGeoHash;
  public final String airportCode;

  public TripDuration() {
    tripDuration = 0;
    pickupGeoHash = "";
    airportCode = "";
  }

  public TripDuration(long tripDuration, String pickupGeoHash, String airportCode) {
    this.tripDuration = tripDuration;
    this.pickupGeoHash = pickupGeoHash;
    this.airportCode = airportCode;
  }
}
