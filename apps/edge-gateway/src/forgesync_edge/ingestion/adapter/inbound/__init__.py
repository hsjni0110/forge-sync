"""Inbound adapters for source records and mapping configuration."""

from .mapping_table_json import load_mapping_table
from .source_acquisition import adapt_catalog, adapt_records

__all__ = ["adapt_catalog", "adapt_records", "load_mapping_table"]
