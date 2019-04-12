package com.netflix.iceberg;

import java.util.List;

/**
 * Identifies a catalog instance as list of strings.
 */
public class CatalogIdentifier {
  private List<String> identifier;

  public CatalogIdentifier(List<String> identifier) {
    this.identifier = identifier;
  }

  /**
   *
   * @return return the list of string representation of underlying identifier.
   */
  public List<String> getIdentifier() {
    return identifier;
  }
}
