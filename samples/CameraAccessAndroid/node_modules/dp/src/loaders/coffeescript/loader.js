import { base, depend } from "dp";

export default base("coffeescript", ".coffee", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["coffeescript"], "coffeescript");
    const { compile } = await import("coffeescript");

    return compile(text);
  });
});
