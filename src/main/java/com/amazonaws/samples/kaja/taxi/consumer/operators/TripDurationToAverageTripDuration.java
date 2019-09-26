package com.amazonaws.samples.kaja.taxi.consumer.operators;

import com.amazonaws.samples.kaja.taxi.consumer.events.es.AverageTripDuration;
import com.amazonaws.samples.kaja.taxi.consumer.events.flink.TripDuration;
import com.google.common.collect.Iterables;
import java.util.stream.StreamSupport;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

public class TripDurationToAverageTripDuration implements WindowFunction<TripDuration, AverageTripDuration, Tuple, TimeWindow> {
  @Override
  public void apply(Tuple tuple, TimeWindow timeWindow, Iterable<TripDuration> iterable, Collector<AverageTripDuration> collector) {
    if (Iterables.size(iterable) > 1) {
      String location = Iterables.get(iterable, 0).pickupGeoHash;
      String airportCode = Iterables.get(iterable, 0).airportCode;

      long sumDuration = StreamSupport
          .stream(iterable.spliterator(), false)
          .mapToLong(trip -> trip.tripDuration)
          .sum();

      double avgDuration = (double) sumDuration / Iterables.size(iterable);

      collector.collect(new AverageTripDuration(location, airportCode, sumDuration, avgDuration, timeWindow.getEnd()));
    }
  }
}
