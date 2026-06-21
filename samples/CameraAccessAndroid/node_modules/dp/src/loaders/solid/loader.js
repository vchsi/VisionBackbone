import { base, depend } from "dp";

export default base("solid", ".solid", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["babel-preset-solid", "@babel/core"], "solid");
    const { default: solid } = await import("babel-preset-solid");
    const { transformAsync } = await import("@babel/core");
    const presets = [[solid, { generate: "ssr", hydratable: true }]];

    return (await transformAsync(text, { presets })).code;

  });
});
