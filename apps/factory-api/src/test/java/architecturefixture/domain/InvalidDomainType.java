package architecturefixture.domain;

import architecturefixture.adapter.FrameworkAdapter;

public final class InvalidDomainType {

  private final FrameworkAdapter adapter;

  public InvalidDomainType(FrameworkAdapter adapter) {
    this.adapter = adapter;
  }

  public FrameworkAdapter adapter() {
    return adapter;
  }
}
