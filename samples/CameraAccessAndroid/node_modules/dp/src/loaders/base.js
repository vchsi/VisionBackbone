import { is } from "rcompat/invariant";

export default (name, default_extension, loader) =>
  (extension = default_extension) => {
    is(extension).string("`extension` must be a string");
    const filter = new RegExp(`${extension}$`, "u");

    return { name, setup: runtime => loader(filter, runtime) };
  };
