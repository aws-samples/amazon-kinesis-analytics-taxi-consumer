package com.amazonaws.samples.kaja.taxi.consumer.events.flink;

public class TripGeoHash {
  public final String geoHash;

  public TripGeoHash() {
    this.geoHash = "";
  }

  public TripGeoHash(String geoHash) {
    this.geoHash = geoHash;
  }
}
