import { base, depend } from "dp";

export default base("react", ".jsx", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["esbuild"], "react");
    const { transform } = await import("esbuild");

    return (await transform(text, { loader: "jsx", jsx: "automatic" })).code;
  });
});
