import { base, depend } from "dp";

export default base("handlebars", ".hbs", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["handlebars"], "handlebars");
    const { default: { precompile } } = await import("handlebars");

    return `export default ${precompile(text)};`;
  });
});
