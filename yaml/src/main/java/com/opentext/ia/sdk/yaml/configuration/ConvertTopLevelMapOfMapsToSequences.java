/*
 * Copyright (c) 2016-2017 by OpenText Corporation. All Rights Reserved.
 */
package com.opentext.ia.sdk.yaml.configuration;

import java.util.function.Consumer;

import com.opentext.ia.sdk.yaml.core.*;


class ConvertTopLevelMapOfMapsToSequences implements Visitor {

  private static final String NAME = "name";

  @Override
  public int maxNesting() {
    return 0;
  }

  @Override
  public void accept(Visit visit) {
    YamlMap yaml = visit.getMap();
    Consumer<Entry> replaceMapOfMapsWithSequence = new MapOfMapsToSequence(yaml);
    yaml.entries()
        .filter(entry -> isMapOfMaps(entry.getValue().toMap()))
        .forEach(replaceMapOfMapsWithSequence);
  }

  private boolean isMapOfMaps(YamlMap yaml) {
    long numChildMapsWithoutName = yaml.values()
        .filter(Value::isMap)
        .map(Value::toMap)
        .filter(nestedMap -> !nestedMap.containsKey(NAME))
        .count();
    return numChildMapsWithoutName > 0 && numChildMapsWithoutName == yaml.values().count();
  }

}