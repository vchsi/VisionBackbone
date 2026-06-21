import { base, depend } from "dp";

export default base("xml", ".xml", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["xml2js"], "xml");
    const { parseStringPromise } = await import("xml2js");

    return `export default ${JSON.stringify(await parseStringPromise(text))}`;
  });
});
