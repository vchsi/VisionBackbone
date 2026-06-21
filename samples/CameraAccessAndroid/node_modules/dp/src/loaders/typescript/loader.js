import { base, depend } from "dp";

export default base("typescript", ".ts", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["esbuild"], "typescript");
    const { transform } = await import("esbuild");

    return (await transform(text, { loader: "ts" })).code;
  });
});
