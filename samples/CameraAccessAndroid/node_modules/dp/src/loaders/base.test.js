import dp from "dp";

export default (name, extension, body) => test => {
  const $body = body === undefined ? extension : body;
  const $extension = body === undefined ? `.${name}` : extension;

  test.case("load", async assert => {
    dp({ once: true });
    try {
      await $body(assert, await import(`./${name}/test${$extension}`));
    } catch (error) {
      $body(assert, error);
    }
  });
  test.case("different extension", async assert => {
    dp({ once: true, extensions: { [`.${name}-2`]: name } });
    try {
      await $body(assert, await import(`./${name}/test${$extension}-2`));
    } catch (error) {
      $body(assert, error);
    }
  });
};
