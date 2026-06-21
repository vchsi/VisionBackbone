import { base, depend } from "dp";

export default base("svelte", ".svelte", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["svelte"], "svelte");
    const { compile } = await import("svelte/compiler");

    return compile(text, { generate: "ssr", hydratable: true }).js.code;
  });
});
