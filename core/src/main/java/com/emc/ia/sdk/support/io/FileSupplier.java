/*
 * Copyright (c) 2016 EMC Corporation. All Rights Reserved.
 */
package com.emc.ia.sdk.support.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.function.Supplier;


/**
 * Several ways of supplying files.
 */
public final class FileSupplier {

  private FileSupplier() {
    // Utility class
  }

  /**
   * Returns a supplier that creates randomly named files in a temporary directory.
   * @return A supplier that creates randomly named files in a temporary directory
   */
  public static Supplier<File> fromTemporaryDirectory() {
    return fromDirectory(tempDirectory());
  }

  private static File tempDirectory() {
    try {
      return Files.createTempDirectory(null).toFile();
    } catch (IOException e) {
      throw new RuntimeIoException(e);
    }
  }

  /**
   * Returns a supplier that creates randomly named files in the given directory.
   * @param dir The directory in which to create files
   * @return A supplier that creates randomly named files in the given directory
   */
  public static Supplier<File> fromDirectory(File dir) {
    return () -> createFileIn(dir);
  }

  private static File createFileIn(File dir) {
    return new File(dir, UUID.randomUUID().toString());
  }

  /**
   * Returns a supplier that creates sequentially named files in the given directory.
   * @param dir The directory in which to create files
   * @param prefix The prefix for the file names
   * @param suffix The suffix for the file names
   * @return A supplier that creates sequentially named files in the given directory
   */
  public static Supplier<File> fromDirectory(File dir, String prefix, String suffix) {
    return new Supplier<File>() {
      private int count;

      @Override
      public File get() {
        return new File(dir, prefix + ++count + suffix);
      }
    };
  }

}
