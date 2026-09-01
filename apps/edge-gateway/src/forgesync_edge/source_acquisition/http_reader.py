"""HTTPS source adapter."""

from __future__ import annotations

from contextlib import AbstractContextManager
from typing import BinaryIO, cast
from urllib.parse import urlparse
from urllib.request import Request, urlopen

from .errors import AcquisitionError


class HttpsSourceReader:
    def __init__(self, timeout_seconds: float = 30.0) -> None:
        self._timeout_seconds = timeout_seconds

    def open(self, source_uri: str) -> AbstractContextManager[BinaryIO]:
        parsed = urlparse(source_uri)
        if parsed.scheme != "https" or parsed.username or parsed.password or parsed.fragment:
            raise AcquisitionError("Only credential-free HTTPS source URIs are allowed")
        request = Request(source_uri, headers={"User-Agent": "ForgeSync/0.1 source-acquisition"})
        try:
            response = urlopen(request, timeout=self._timeout_seconds)  # noqa: S310
        except OSError as error:
            raise AcquisitionError(f"Cannot open source URI: {error}") from error
        return cast(AbstractContextManager[BinaryIO], response)
