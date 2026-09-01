"""MTConnect Devices XML metadata catalog adapter."""

from __future__ import annotations

import xml.etree.ElementTree as element_tree
from pathlib import Path

from .domain import DataItemCatalogEntry, MachineCatalog
from .errors import ProfileError

STRUCTURAL_TAGS = {"Components", "DataItems"}


def read_machine_catalog(devices_path: Path, machine_id: str) -> MachineCatalog:
    try:
        root = element_tree.parse(devices_path).getroot()
    except (OSError, element_tree.ParseError) as error:
        raise ProfileError(f"Cannot parse MTConnect devices document: {error}") from error

    machine = next(
        (
            element
            for element in root.iter()
            if _local_name(element.tag) == "Device"
            and (element.get("id") == machine_id or element.get("name") == machine_id)
        ),
        None,
    )
    if machine is None:
        raise ProfileError(f"Machine not found in devices document: {machine_id}")

    description_element = next(
        (child for child in machine if _local_name(child.tag) == "Description"), None
    )
    description = {
        "text": _clean_text(description_element.text) if description_element is not None else None,
        "manufacturer": description_element.get("manufacturer")
        if description_element is not None
        else None,
        "model": description_element.get("model") if description_element is not None else None,
        "serialNumber": description_element.get("serialNumber")
        if description_element is not None
        else None,
    }
    machine_label = _component_label(machine)
    entries: list[DataItemCatalogEntry] = []
    machine_component_id = machine.get("id")
    if machine_component_id is None:
        raise ProfileError("Machine is missing component id")
    _collect_data_items(machine, machine_component_id, (machine_label,), entries)
    entries.sort(key=lambda entry: (entry.name, entry.data_item_id))
    return MachineCatalog(
        machine_id=machine.get("id", machine_id),
        machine_name=machine.get("name", machine_id),
        uuid=machine.get("uuid"),
        description=description,
        entries=tuple(entries),
    )


def _collect_data_items(
    element: element_tree.Element,
    component_id: str,
    component_path: tuple[str, ...],
    entries: list[DataItemCatalogEntry],
) -> None:
    for child in element:
        tag = _local_name(child.tag)
        if tag == "DataItem":
            source_element = next(
                (nested for nested in child if _local_name(nested.tag) == "Source"), None
            )
            data_item_id = child.get("id")
            name = child.get("name")
            category = child.get("category")
            item_type = child.get("type")
            if data_item_id is None or name is None or category is None or item_type is None:
                raise ProfileError("DataItem is missing id, name, category, or type")
            entries.append(
                DataItemCatalogEntry(
                    data_item_id=data_item_id,
                    component_id=component_id,
                    name=name,
                    category=category,
                    type=item_type,
                    subtype=child.get("subType"),
                    units=child.get("units"),
                    native_units=child.get("nativeUnits"),
                    coordinate_system=child.get("coordinateSystem"),
                    component_path=component_path,
                    source_text=_clean_text(source_element.text)
                    if source_element is not None
                    else None,
                )
            )
            continue
        if tag in STRUCTURAL_TAGS or tag in {"Description", "Source"}:
            next_component_id = component_id
            next_path = component_path
        else:
            next_component_id = child.get("id") or ""
            if not next_component_id:
                raise ProfileError(f"Component is missing id: {tag}")
            next_path = (*component_path, _component_label(child))
        _collect_data_items(child, next_component_id, next_path, entries)


def _component_label(element: element_tree.Element) -> str:
    tag = _local_name(element.tag)
    identity = element.get("name") or element.get("id")
    return f"{tag}:{identity}" if identity else tag


def _local_name(tag: str) -> str:
    return tag.rsplit("}", maxsplit=1)[-1]


def _clean_text(value: str | None) -> str | None:
    if value is None:
        return None
    cleaned = value.strip()
    return cleaned or None
