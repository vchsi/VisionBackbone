import { base, depend } from "dp";

export default base("toml", ".toml", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["toml"], "toml");
    const { default: { parse } } = await import("toml");

    return `export default ${JSON.stringify(parse(text))}`;
  });
});
