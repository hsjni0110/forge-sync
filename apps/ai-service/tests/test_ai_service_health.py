from fastapi.testclient import TestClient
from forgesync_ai.main import create_app


def test_health_reports_service_availability_without_domain_readiness() -> None:
    response = TestClient(create_app()).get("/health")

    assert response.status_code == 200
    assert response.json() == {"service": "forgesync-ai-service", "status": "UP"}
