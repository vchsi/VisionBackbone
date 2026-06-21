import { base, depend } from "dp";

export default base("csv", ".csv", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["csv-parse"], "csv");
    const { parse } = await import("csv-parse/sync");

    return `export default ${JSON.stringify(parse(text))}`;
  });
});
