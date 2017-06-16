/*
 * Copyright (c) 2016-2017 by OpenText Corporation. All Rights Reserved.
 */
package com.opentext.ia.sdk.support.yaml;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Visit {

  private static final String SEPARATOR = "/";

  private final YamlMap map;
  private final String path;
  private final int level;

  Visit(YamlMap map) {
    this(map, "", 0);
  }

  Visit(YamlMap map, String path, int level) {
    this.map = map;
    this.path = path;
    this.level = level;
  }

  public YamlMap getMap() {
    return map;
  }

  public String getPath() {
    return path.isEmpty() ? SEPARATOR : path;
  }

  public int getLevel() {
    return level;
  }

  Visit descend(Object... keys) {
    return new Visit(map.get(keys).toMap(), appendPath(keys), level + keys.length);
  }

  private String appendPath(Object... keys) {
    return path + SEPARATOR + Arrays.asList(keys).stream()
        .map(key -> key.toString())
        .collect(Collectors.joining(SEPARATOR));
  }

  @Override
  public String toString() {
    return getPath() + " -> " + getMap();
  }

}