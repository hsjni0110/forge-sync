import js from "@eslint/js";
import boundaries from "eslint-plugin-boundaries";
import globals from "globals";
import tseslint from "typescript-eslint";

export default tseslint.config(
  { ignores: ["dist"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      globals: globals.browser,
    },
    plugins: {
      boundaries,
    },
    settings: {
      "import/resolver": {
        node: {
          extensions: [".js", ".jsx", ".ts", ".tsx"],
        },
      },
      "boundaries/elements": [
        { type: "domain", pattern: "**/features/*/domain/**", partialMatch: false },
        { type: "application", pattern: "**/features/*/application/**", partialMatch: false },
        { type: "adapters", pattern: "**/features/*/adapters/**", partialMatch: false },
        { type: "ui", pattern: "**/features/*/ui/**", partialMatch: false },
      ],
    },
    rules: {
      "boundaries/dependencies": [
        "error",
        {
          default: "disallow",
          policies: [
            {
              from: { element: { type: "domain" } },
              allow: { to: { element: { type: "domain" } } },
            },
            {
              from: { element: { type: "application" } },
              allow: { to: { element: { types: { anyOf: ["domain", "application"] } } } },
            },
            {
              from: { element: { type: "adapters" } },
              allow: {
                to: { element: { types: { anyOf: ["domain", "application", "adapters"] } } },
              },
            },
            {
              from: { element: { type: "ui" } },
              allow: {
                to: {
                  element: {
                    types: { anyOf: ["domain", "application", "adapters", "ui"] },
                  },
                },
              },
            },
          ],
        },
      ],
    },
  },
  {
    files: [
      "src/features/factory3d/ui/FactoryScene.tsx",
      "src/features/factory3d/ui/MachineTwin.tsx",
      "src/features/factory3d/ui/FloatingMachineLabel.tsx",
      "src/features/factory3d/ui/GenericMachinePrimitive.tsx",
      "tests/architecture/fixtures/features/factory3d/ui/invalid.ts",
    ],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["../../twin/**", "**/features/twin/**"],
              message:
                "3D renderer components consume MachineVisualState, not Twin/backend response types.",
            },
          ],
        },
      ],
    },
  },
);
