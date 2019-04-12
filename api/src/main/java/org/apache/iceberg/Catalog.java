package com.netflix.iceberg;

import com.netflix.iceberg.exceptions.AlreadyExistsException;
import com.netflix.iceberg.exceptions.NoSuchTableException;

import java.util.List;

public interface Catalog {
  /**
   * creates the table or throws {@link AlreadyExistsException}.
   * @param schema
   * @param spec
   * @param catalogIdentifier
   * @return Table instance that was created
   */
  Table create(Schema schema, PartitionSpec spec, CatalogIdentifier catalogIdentifier);

  /**
   *
   * @param catalogIdentifier
   * @return true if table exists, false if it doesn't.
   */
  boolean exists(CatalogIdentifier catalogIdentifier);

  /**
   * Drops the table if it exists, otherwise throws {@link NoSuchTableException}
   * @param catalogIdentifier
   * @param shouldDeleteData should the data corresponding to this table be deleted
   */
  void drop(CatalogIdentifier catalogIdentifier, boolean shouldDeleteData);

  /**
   * Renames a table. If @{code from} does not exists throws {@link NoSuchTableException}
   * If {@code to} exists than throws {@link AlreadyExistsException}.
   * @param from
   * @param to
   */
  void rename(CatalogIdentifier from, CatalogIdentifier to);

  /**
   *
   * @param catalogIdentifier
   * @return List of fully qualified table {@link CatalogIdentifier} under the specified catalogIdentifier.
   */
  List<CatalogIdentifier> list(CatalogIdentifier catalogIdentifier);
}
