import { base } from "dp";

const encoder = new TextEncoder();

export default base("wasm", ".wasm", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    return {
      format: "wasm",
      shortCircuit: true,
      source: encoder.encode(text),
    };
  });
});
