package com.amazonaws.samples.kaja.taxi.consumer.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.flink.api.java.utils.ParameterTool;

public class ParameterToolUtils {
    public static ParameterTool fromApplicationProperties(Properties properties) {
        Map<String, String> map = new HashMap<>(properties.size());

        properties.forEach((k, v) -> map.put((String) k, (String) v));

        return ParameterTool.fromMap(map);
    }
}
