import { base, depend } from "dp";

export default base("json5", ".json5", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["json5"], "json5");
    const { default: { parse } } = await import("json5");

    return `export default ${JSON.stringify(parse(text))}`;
  });
});
