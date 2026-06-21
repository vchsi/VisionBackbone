import { base, depend } from "dp";

export default base("marko", ".marko", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["@marko/compiler", "@marko/translator-default"], "marko");
    const { compile } = await import("@marko/compiler");

    return (await compile(text, "")).code;
  });
});
