import Loader from "./Loader.js";

if (process.execArgv[0]?.includes("--import=dp")) {
  new Loader().init();
}

export default config => new Loader(config).init();

export { default as depend } from "./loaders/depend.js";
export { default as base } from "./loaders/base.js";
export { default as test } from "./loaders/base.test.js";
export { Loader };
