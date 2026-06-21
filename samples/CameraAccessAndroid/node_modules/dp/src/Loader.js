import { register } from "node:module";
import o from "rcompat/object";
import { is } from "rcompat/invariant";

export default class {
  #started = false;
  #config;

  constructor(config) {
    this.#config = o.defaults(config, {
      virtuals: {},
      extensions: {},
    });
  }

  virtual(url, text) {
    is(url).string();
    is(text).string();

    this.#config.virtuals[url] = text;
  }

  extension(extension, loader) {
    this.#config.extensions[extension] = loader;
  }

  init() {
    if (this.#started) {
      throw new Error("loader already started");
    }
    const data = { config: this.#config };
    register("./register.js", import.meta.url, { data });

    this.#started = true;
  }
}
