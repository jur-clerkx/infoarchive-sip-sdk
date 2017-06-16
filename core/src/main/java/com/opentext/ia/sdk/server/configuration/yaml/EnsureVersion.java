/*
 * Copyright (c) 2016-2017 by OpenText Corporation. All Rights Reserved.
 */
package com.opentext.ia.sdk.server.configuration.yaml;

import com.opentext.ia.sdk.support.yaml.Visit;
import com.opentext.ia.sdk.support.yaml.Visitor;


class EnsureVersion implements Visitor {

  private static final String VERSION = "version";
  private static final String DEFAULT_VERSION = "1.0.0";

  @Override
  public boolean test(Visit visit) {
    return !visit.getMap().containsKey(VERSION);
  }

  @Override
  public void accept(Visit visit) {
    visit.getMap().put(VERSION, DEFAULT_VERSION);
  }

  @Override
  public int maxNesting() {
    return 0;
  }

}
