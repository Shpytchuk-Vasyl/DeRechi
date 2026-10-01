import type { CodegenConfig } from "@graphql-codegen/cli"

const config: CodegenConfig = {
  schema: ["../Client-API/src/main/resources/graphql/schema.graphqls", "graphql/*.graphqls"],
  documents: ["src/**/*.{ts,tsx}", "!src/graphql/generated/**"],
  ignoreNoDocuments: true,
  generates: {
    "src/graphql/generated/": {
      preset: "client",
      presetConfig: {
        fragmentMasking: false,
      },
      config: {
        documentMode: "string",
        scalars: { Date: "string" },
        useTypeImports: true,
        enumsAsTypes: true,
      },
    },
  },
}

export default config
