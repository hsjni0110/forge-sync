package architecturefixture.processanalytics.application;

import architecturefixture.equipmenttwin.adapter.InternalTwinRepository;

public final class InvalidProcessAnalyticsService {

  private final InternalTwinRepository twinRepository;

  public InvalidProcessAnalyticsService(InternalTwinRepository twinRepository) {
    this.twinRepository = twinRepository;
  }

  public InternalTwinRepository twinRepository() {
    return twinRepository;
  }
}
