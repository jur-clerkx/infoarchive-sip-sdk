/*
 * Copyright (c) 2016 EMC Corporation. All Rights Reserved.
 */
package com.emc.ia.sdk.sip.ingestion;

import java.util.List;

import org.apache.http.Header;


/**
 * Stores InfoArchive server configuration parameters.
 */
public interface IAConfiguration {

  Tenant getTenant();

  Application getApplication();

  List<Header> getHeaders();

  String getAipsHref();

}
