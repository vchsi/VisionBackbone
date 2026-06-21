import { base, depend } from "dp";

export default base("yaml", ".yaml", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["yaml"], "yaml");
    const { parse } = await import("yaml");

    return `export default ${JSON.stringify(parse(text))}`;
  });
});
