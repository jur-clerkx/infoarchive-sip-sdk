/*
 * Copyright (c) 2016 EMC Corporation. All Rights Reserved.
 * EMC Confidential: Restricted Internal Distribution
 */
package com.emc.ia.sdk.sip.assembly;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import com.emc.ia.sdk.support.io.DataBuffer;
import com.emc.ia.sdk.support.io.DataBufferSupplier;
import com.emc.ia.sdk.support.io.DefaultZipAssembler;
import com.emc.ia.sdk.support.io.EncodedHash;
import com.emc.ia.sdk.support.io.FileBuffer;
import com.emc.ia.sdk.support.io.HashAssembler;
import com.emc.ia.sdk.support.io.MemoryBuffer;
import com.emc.ia.sdk.support.io.NoHashAssembler;
import com.emc.ia.sdk.support.io.RuntimeIoException;
import com.emc.ia.sdk.support.io.ZipAssembler;


/**
 * Assembles a <a href="http://public.ccsds.org/publications/archive/650x0m2.pdf">Submission Information Package</a>
 * (SIP) from several domain objects of the same type. Each domain object is typically a Plain Old Java Object (POJO)
 * that you create in an application-specific manner.
 * <p>
 * A SIP is a ZIP file that contains:<ul>
 * <li>One {@linkplain com.emc.ia.sdk.sip.assembly.PackagingInformation Packaging Information} that describes the
 * content of the SIP</li>
 * <li>One Preservation Description Information (PDI) that contains structured data to be archived</li>
 * <li>Zero or more Content Data Objects that contain unstructured data to be archived. These are referenced from the
 * PDI</li>
 * </ul>
 * <p>
 * Packaging Information is created by a {@linkplain PackagingInformationFactory factory}. If you want only one SIP in
 * a {@linkplain DataSubmissionSession DSS}, then you can use a {@linkplain DefaultPackagingInformationFactory}
 * to create the Packaging Information based on a prototype which contains application-specific fields.
 * <p>
 * The PDI will be assembled from the domain objects by an {@linkplain Assembler} and added to the ZIP by a
 * {@linkplain ZipAssembler}. Each domain object may also contain zero or more {@linkplain DigitalObject}s, which
 * are extracted from the domain object using a {@linkplain DigitalObjectsExtraction} and added to the ZIP.
 * The PDI is written to a {@linkplain DataBuffer} until it is complete. For small PDIs, you can use a
 * {@linkplain MemoryBuffer} to hold this data, but for larger PDIs you should use a {@linkplain FileBuffer} to prevent
 * running out of memory.
 * <p>
 * Use the following steps to assemble a SIP:
 * <ol>
 * <li>Start the process by calling the {@linkplain #start(DataBuffer)} method</li>
 * <li>Add zero or more domain objects by calling the {@linkplain #add(Object)} method multiple times</li>
 * <li>Finish the process by calling the {@linkplain #end()} method</li>
 * </ol>
 * You can optionally get metrics about the SIP assembly process by calling {@linkplain #getMetrics()} at any time.
 * <p>
 * If the number of domain objects is small and each individual domain object is also small, you can wrap a
 * {@linkplain SipAssembler} in a {@linkplain Generator} to reduce the above code to a single call.
 * <p>
 * To assemble a number of SIPs in a batch, use {@linkplain BatchSipAssembler}.
 * <p>
 * @param <D> The type of domain objects to assemble the SIP from
 */
public class SipAssembler<D> implements Assembler<D> {

  private static final String PACKAGING_INFORMATION_ENTRY = "eas_sip.xml";
  private static final String PDI_ENTRY = "eas_pdi.xml";

  private final ZipAssembler zip;
  private final Assembler<PackagingInformation> packagingInformationAssembler;
  private final Assembler<HashedContents<D>> pdiAssembler;
  private final HashAssembler pdiHashAssembler;
  private final DigitalObjectsExtraction<D> contentsExtraction;
  private final HashAssembler contentHashAssembler;
  private final Supplier<? extends DataBuffer> pdiBufferSupplier;
  private final PackagingInformationFactory packagingInformationFactory;
  private final Counters metrics = new Counters();
  private DataBuffer pdiBuffer;
  private Optional<EncodedHash> pdiHash;

  /**
   * Assemble a SIP that contains only structured data and is the only SIP in its DSS.
   * @param <D> The type of domain objects to assemble the SIP from
   * @param prototype Prototype for the Packaging Information
   * @param pdiAssembler Assembler that builds up the PDI
   * @return The newly created SIP assembler
   */
  public static <D> SipAssembler<D> forPdi(PackagingInformation prototype, Assembler<HashedContents<D>> pdiAssembler) {
    return forPdiWithHashing(prototype, pdiAssembler, new NoHashAssembler());
  }

  /**
   * Assemble a SIP that contains only structured data and is the only SIP in its DSS.
   * @param <D> The type of domain objects to assemble the SIP from
   * @param factory Factory for creating the Packaging Information
   * @param pdiAssembler Assembler that builds up the PDI
   * @return The newly created SIP assembler
   */
  public static <D> SipAssembler<D> forPdi(PackagingInformationFactory factory, Assembler<HashedContents<D>> pdiAssembler) {
    return forPdiWithHashing(factory, pdiAssembler, new NoHashAssembler());
  }

  /**
   * Assemble a SIP that contains only structured data and is the only SIP in its DSS.
   * @param <D> The type of domain objects to assemble the SIP from
   * @param prototype Prototype for the Packaging Information
   * @param pdiAssembler Assembler that builds up the PDI
   * @param pdiHashAssembler Assembler that builds up an encoded hash for the PDI
   * @return The newly created SIP assembler
   */
  public static <D> SipAssembler<D> forPdiWithHashing(PackagingInformation prototype,
      Assembler<HashedContents<D>> pdiAssembler, HashAssembler pdiHashAssembler) {
    return forPdiAndContentWithHashing(prototype, pdiAssembler, pdiHashAssembler, noDigitalObjectsExtraction(),
        new NoHashAssembler());
  }

  private static <D> DigitalObjectsExtraction<D> noDigitalObjectsExtraction() {
    return domainObject -> Collections.emptyIterator();
  }

  /**
   * Assemble a SIP that contains only structured data and is the only SIP in its DSS.
   * @param <D> The type of domain objects to assemble the SIP from
   * @param factory Factory for creating the Packaging Information
   * @param pdiAssembler Assembler that builds up the PDI
   * @param pdiHashAssembler Assembler that builds up an encoded hash for the PDI
   * @return The newly created SIP assembler
   */
  public static <D> SipAssembler<D> forPdiWithHashing(PackagingInformationFactory factory,
      Assembler<HashedContents<D>> pdiAssembler, HashAssembler pdiHashAssembler) {
    return forPdiAndContentWithHashing(factory, pdiAssembler, pdiHashAssembler, noDigitalObjectsExtraction(),
        new NoHashAssembler());
  }

  /**
   * Assemble a SIP that contains only structured data and is the only SIP in its DSS.
   * @param <D> The type of domain objects to assemble the SIP from
   * @param prototype Prototype for the Packaging Information
   * @param pdiAssembler Assembler that builds up the PDI
   * @param contentsExtraction Extraction of content from domain objects added to the SIP
   * @return The newly created SIP assembler
   */
  public static <D> SipAssembler<D> forPdiAndContent(PackagingInformation prototype,
      Assembler<HashedContents<D>> pdiAssembler, DigitalObjectsExtraction<D> contentsExtraction) {
    HashAssembler noHashAssembler = new NoHashAssembler();
    return forPdiAndContentWithHashing(prototype, pdiAssembler, noHashAssembler, contentsExtraction, noHashAssembler);
  }

  /**
   * Assemble a SIP that contains only structured data and is the only SIP in its DSS.
   * @param <D> The type of domain objects to assemble the SIP from
   * @param factory Factory for creating the Packaging Information
   * @param pdiAssembler Assembler that builds up the PDI
   * @param contentsExtraction Extraction of content from domain objects added to the SIP
   * @return The newly created SIP assembler
   */
  public static <D> SipAssembler<D> forPdiAndContent(PackagingInformationFactory factory,
      Assembler<HashedContents<D>> pdiAssembler, DigitalObjectsExtraction<D> contentsExtraction) {
    HashAssembler noHashAssembler = new NoHashAssembler();
    return forPdiAndContentWithHashing(factory, pdiAssembler, noHashAssembler, contentsExtraction, noHashAssembler);
  }

  /**
   * Assemble a SIP that contains only structured data and is the only SIP in its DSS.
   * @param <D> The type of domain objects to assemble the SIP from
   * @param prototype Prototype for the Packaging Information
   * @param pdiAssembler Assembler that builds up the PDI
   * @param contentsExtraction Extraction of content from domain objects added to the SIP
   * @param contentHashAssembler Assembler that builds up an encoded hash for the extracted content
   * @return The newly created SIP assembler
   */
  public static <D> SipAssembler<D> forPdiAndContentWithContentHashing(PackagingInformation prototype,
      Assembler<HashedContents<D>> pdiAssembler, DigitalObjectsExtraction<D> contentsExtraction,
      HashAssembler contentHashAssembler) {
    return forPdiAndContentWithHashing(prototype, pdiAssembler, new NoHashAssembler(), contentsExtraction,
        contentHashAssembler);
  }

  /**
   * Assemble a SIP that contains only structured data and is the only SIP in its DSS.
   * @param <D> The type of domain objects to assemble the SIP from
   * @param factory Factory for creating the Packaging Information
   * @param pdiAssembler Assembler that builds up the PDI
   * @param contentsExtraction Extraction of content from domain objects added to the SIP
   * @param contentHashAssembler Assembler that builds up an encoded hash for the extracted content
   * @return The newly created SIP assembler
   */
  public static <D> SipAssembler<D> forPdiAndContentWithContentHashing(PackagingInformationFactory factory,
      Assembler<HashedContents<D>> pdiAssembler, DigitalObjectsExtraction<D> contentsExtraction,
      HashAssembler contentHashAssembler) {
    return forPdiAndContentWithHashing(factory, pdiAssembler, new NoHashAssembler(), contentsExtraction,
        contentHashAssembler);
  }

  /**
   * Assemble a SIP that is the only SIP in its DSS.
   * @param <D> The type of domain objects to assemble the SIP from
   * @param prototype Prototype for the Packaging Information
   * @param pdiAssembler Assembler that builds up the PDI
   * @param pdiHashAssembler Assembler that builds up an encoded hash for the PDI
   * @param contentsExtraction Extraction of content from domain objects added to the SIP
   * @param contentHashAssembler Assembler that builds up an encoded hash for the extracted content
   * @return The newly created SIP assembler
   */
  public static <D> SipAssembler<D> forPdiAndContentWithHashing(PackagingInformation prototype,
      Assembler<HashedContents<D>> pdiAssembler, HashAssembler pdiHashAssembler,
      DigitalObjectsExtraction<D> contentsExtraction, HashAssembler contentHashAssembler) {
    return new SipAssembler<>(new DefaultPackagingInformationFactory(prototype), pdiAssembler, pdiHashAssembler,
        new DataBufferSupplier<>(MemoryBuffer.class), contentsExtraction, contentHashAssembler);
  }

  /**
   * Assemble a SIP that is the only SIP in its DSS.
   * @param <D> The type of domain objects to assemble the SIP from
   * @param factory Factory for creating the Packaging Information
   * @param pdiAssembler Assembler that builds up the PDI
   * @param pdiHashAssembler Assembler that builds up an encoded hash for the PDI
   * @param contentsExtraction Extraction of content from domain objects added to the SIP
   * @param contentHashAssembler Assembler that builds up an encoded hash for the extracted content
   * @return The newly created SIP assembler
   */
  public static <D> SipAssembler<D> forPdiAndContentWithHashing(PackagingInformationFactory factory,
      Assembler<HashedContents<D>> pdiAssembler, HashAssembler pdiHashAssembler,
      DigitalObjectsExtraction<D> contentsExtraction, HashAssembler contentHashAssembler) {
    return new SipAssembler<>(factory, pdiAssembler, pdiHashAssembler, new DataBufferSupplier<>(MemoryBuffer.class),
        contentsExtraction, contentHashAssembler);
  }


  /**
   * Create a new instance.
   * @param packagingInformationFactory Factory for creating the Packaging Information
   * @param pdiAssembler Assembler that builds up the PDI
   * @param pdiHashAssembler Assembler that builds up an encoded hash for the PDI and the unstructured data
   * @param pdiBufferSupplier Supplier for a data buffer to store the PDI
   * @param contentsExtraction Extraction of content from domain objects added to the SIP
   * @param contentHashAssembler Assembler that builds up an encoded hash for the extracted content
   */
  public SipAssembler(PackagingInformationFactory packagingInformationFactory, Assembler<HashedContents<D>> pdiAssembler,
      HashAssembler pdiHashAssembler, Supplier<? extends DataBuffer> pdiBufferSupplier,
      DigitalObjectsExtraction<D> contentsExtraction, HashAssembler contentHashAssembler) {
    this(packagingInformationFactory, new InfoArchivePackagingInformationAssembler(), pdiAssembler, pdiHashAssembler,
        pdiBufferSupplier, contentsExtraction, contentHashAssembler, new DefaultZipAssembler());
  }

  SipAssembler(PackagingInformationFactory packagingInformationFactory,
      Assembler<PackagingInformation> packagingInformationAssembler, Assembler<HashedContents<D>> pdiAssembler,
      HashAssembler pdiHashAssembler, Supplier<? extends DataBuffer> pdiBufferSupplier,
      DigitalObjectsExtraction<D> contentsExtraction, HashAssembler contentHashAssembler,
      ZipAssembler zipAssembler) {
    this.packagingInformationFactory = packagingInformationFactory;
    this.packagingInformationAssembler = packagingInformationAssembler;
    this.pdiAssembler = pdiAssembler;
    this.pdiHashAssembler = pdiHashAssembler;
    this.pdiBufferSupplier = pdiBufferSupplier;
    this.contentsExtraction = contentsExtraction;
    this.contentHashAssembler = contentHashAssembler;
    this.zip = zipAssembler;
  }

  @Override
  public void start(DataBuffer buffer) throws IOException {
    pdiHash = Optional.empty();
    metrics.reset();
    metrics.set(SipMetrics.ASSEMBLY_TIME, System.currentTimeMillis());
    zip.begin(buffer.openForWriting());
    startPdi();
  }

  private void startPdi() throws IOException {
    pdiBuffer = pdiBufferSupplier.get();
    pdiAssembler.start(pdiBuffer);
  }

  @Override
  public void add(D domainObject) {
    try {
      Map<String, Collection<EncodedHash>> contentHashes = addContentsOf(domainObject);
      pdiAssembler.add(new HashedContents<>(domainObject, contentHashes));
      metrics.inc(SipMetrics.NUM_AIUS);
      setPdiSize(pdiBuffer.length()); // Approximate PDI size until the end, when we know for sure
    } catch (IOException e) {
      throw new RuntimeIoException(e);
    }
  }

  private void setPdiSize(long pdiSize) {
    metrics.set(SipMetrics.SIZE_PDI, pdiSize);
    metrics.set(SipMetrics.SIZE_SIP, metrics.get(SipMetrics.SIZE_DIGITAL_OBJECTS) + metrics.get(SipMetrics.SIZE_PDI));
  }

  private Map<String, Collection<EncodedHash>> addContentsOf(D domainObject) throws IOException {
    Map<String, Collection<EncodedHash>> result = new TreeMap<>();
    Iterator<? extends DigitalObject> digitalObjects = contentsExtraction.apply(domainObject);
    while (digitalObjects.hasNext()) {
      DigitalObject digitalObject = digitalObjects.next();
      metrics.inc(SipMetrics.NUM_DIGITAL_OBJECTS);
      String entry = digitalObject.getReferenceInformation();
      try (InputStream stream = digitalObject.get()) {
        Collection<EncodedHash> hashes = zip.addEntry(entry, stream, contentHashAssembler);
        result.put(entry, hashes);
        metrics.inc(SipMetrics.SIZE_DIGITAL_OBJECTS, contentHashAssembler.numBytesHashed());
      }
    }
    return result;
  }

  @Override
  public void end() throws IOException {
    endPdi();
    addPackagingInformation();
    zip.close();
    metrics.set(SipMetrics.ASSEMBLY_TIME, System.currentTimeMillis() - metrics.get(SipMetrics.ASSEMBLY_TIME));
  }

  private void endPdi() throws IOException {
    pdiAssembler.end();
    addPdiToZip();
    pdiBuffer = null;
  }

  void addPdiToZip() throws IOException {
    try (InputStream in = pdiBuffer.openForReading()) {
      pdiHash = zip.addEntry(PDI_ENTRY, in, pdiHashAssembler).stream()
          .limit(1)
          .findAny();
    }
    setPdiSize(pdiHashAssembler.numBytesHashed());
  }

  private void addPackagingInformation() throws IOException {
    DataBuffer buffer = new MemoryBuffer();
    packagingInformationAssembler.start(buffer);
    packagingInformationAssembler.add(packagingInformation());
    packagingInformationAssembler.end();
    try (InputStream stream = buffer.openForReading()) {
      zip.addEntry(PACKAGING_INFORMATION_ENTRY, stream, new NoHashAssembler());
    }
  }

  private PackagingInformation packagingInformation() {
    return packagingInformationFactory.newInstance(metrics.get(SipMetrics.NUM_AIUS), pdiHash);
  }

  @Override
  public SipMetrics getMetrics() {
    return new SipMetrics(metrics.forReading());
  }

  public PackagingInformationFactory getPackagingInformationFactory() {
    return packagingInformationFactory;
  }

}