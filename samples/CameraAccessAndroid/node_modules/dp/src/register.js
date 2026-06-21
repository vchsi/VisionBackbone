import Runtime from "./Runtime.js";

export const initialize = async ({ config } = {}) => {
  globalThis.runtime = await Runtime.new(config);
};

export const resolve = (specifier, context, next) =>
  globalThis.runtime.resolve(specifier, context, next);

export const load = (...args) => globalThis.runtime.load(...args);
