import { File } from "rcompat/fs";
import o from "rcompat/object";
import * as loaders from "./loaders/exports.js";
import extensions from "./extensions.js";

const fallback = {
  loader: (url, context, next) => next(url, context),
};

const facade_loaded = loaded => typeof loaded === "string"
  ? { format: "module", shortCircuit: true, source: loaded }
  : loaded;

const empty =
  { format: "module", shortCircuit: true, source: "export default {}" };

const try_load = async func => {
  try {
    return await func();
  } catch(error) {
    return empty;
  }
};

const Runtime = class Runtime {
  #config;
  #used = false;
  #loaders = [];

  constructor(config) {
    this.#config = config;
  }

  reuse() {
    this.#used = false;
  }

  #text(url) {
    return this.#config.virtuals[url] ?? File.text(url);
  }

  async #resolve(url) {
    return this.#config.virtuals[url] || File.exists(url);
  }

  onload({ filter }, loader) {
    this.#loaders.push({
      filter,
      loader: async (url, context, next) =>
        await this.#resolve(url)
          ? facade_loaded(await loader(await this.#text(url)))
          : next(url, context),
    });
  }

  resolve(specifier, context, next) {
    return this.#config.virtuals[specifier]
      ? {
        format: "module",
        importAttributes: {},
        shortCircuit: true,
        url: specifier,
      }
      : next(specifier, context);
  }

  async load(url, context, next) {
    if (this.#config.once && this.#used) {
      return fallback.loader(url, context, next);
    }
    const { loader } = this.#loaders.find(_ => _.filter.test(url)) ?? fallback;
    if (loader !== fallback.loader) {
      this.#used = true;
    }
    return try_load(() => loader(url, context, next));
  }
};

export default {
  async new(config) {
    const $config = o.defaults(config, {
      once: false,
      virtuals: {},
      extensions,
    });
    const runtime = new Runtime($config);
    await Promise.all(Object.entries($config.extensions)
      .map(([extension, loader]) => loaders[loader](extension).setup(runtime)));

    return runtime;
  },
};
