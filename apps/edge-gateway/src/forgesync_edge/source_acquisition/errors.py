"""Application errors with stable CLI exit categories."""


class SourceAcquisitionError(Exception):
    """Base error for the source acquisition context."""


class ConfigurationError(SourceAcquisitionError):
    """The source lock or command input is invalid."""


class AcquisitionError(SourceAcquisitionError):
    """A source could not be acquired."""


class IntegrityError(AcquisitionError):
    """Stored or downloaded bytes do not match their declared identity."""


class ArtifactNotFoundError(AcquisitionError):
    """A declared artifact is not present in the local store."""


class ProfileError(SourceAcquisitionError):
    """A source profile could not be produced."""
