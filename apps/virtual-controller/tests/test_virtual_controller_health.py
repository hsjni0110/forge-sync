from fastapi.testclient import TestClient
from forgesync_virtual_controller.main import create_app


def test_health_reports_simulator_availability_without_physical_control_claims() -> None:
    response = TestClient(create_app()).get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "service": "forgesync-virtual-controller",
        "status": "UP",
    }
