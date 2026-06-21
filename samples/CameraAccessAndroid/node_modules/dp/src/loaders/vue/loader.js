import { base, depend } from "dp";

export default base("vue", ".vue", (filter, runtime) => {
  runtime.onload({ filter }, async text => {
    await depend(["vue"], "vue");
    const { compileTemplate, parse } = await import("vue/compiler-sfc");

    return compileTemplate({
      source: parse(text).descriptor.template.content,
      id: "1",
    }).code;
  });
});
