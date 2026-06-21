import { base } from "dp";

export default base("json", ".json", (filter, runtime) => {
  runtime.onload({ filter }, text => `export default ${text}`);
});
