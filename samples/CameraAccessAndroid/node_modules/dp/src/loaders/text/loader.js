import { base } from "dp";

export default base("text", ".txt", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    return `export default ${JSON.stringify(text)}`;
  });
});
