# Factory Asset Manifest

`v1/factory-asset-manifest.schema.json` records the identity, origin, representation and license
evidence for every asset that the Factory Scene may render. A GLB entry must also carry its byte
length and SHA-256. The browser verifies both values before GLTF parsing so a downloaded file cannot
silently change.

The initial manifest contains only ForgeSync's code-defined generic machine primitive. It does not
claim that an external CNC GLB is licensed or that the simulated layout represents a real NIST
factory. A later external asset must add verified license evidence before it becomes the default.
