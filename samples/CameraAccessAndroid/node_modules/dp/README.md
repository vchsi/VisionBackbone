# dp

JavaScript loader

## Why?

* Import a variety of configuration formats (YAML, TOML, JSON5)
* Loosen Node's standard enforcement of import assertions for JSON
* Import frontend framework formats for server-side templating (JSX, Vue, 
Svelte, Solid, Handlebars, Marko)
* Import transpiled languages (TypeScript, CoffeeScript)
* Other stuff (`.txt` files as string, `.wasm` modules)

## Install

`npm install dp`

For the loaders, peer deps must be installed and you will be informed if
they're missing. See [loaders](#loaders) for supported loaders.

## Use 

### In the terminal

If you run from the terminal, you get both static and dynamic imports.

`node --import=dp app.js`

```js
import config from "./config.json";

// or (await import("./config.json")).default
```

### Programmatically

If used programmatically, imports coming after `dp()` must be dynamic
(`import()`).

```js
import dp from "dp";

dp();

const config = (await import("./config.json")).default;
```

### Configure

This currently applies only programmatically.

#### extensions

Object property to remap a given extension to a loader. In the following
example, the `yaml` loader will trigger on `.yml` files in addition to the 
default `.yaml`.

```js
import dp from "dp";

dp({ extensions: { ".yml": "yaml" } });

const config = (await import("./config.yml")).default;
```

#### virtuals

Object property in the form of `{ [path]: code }` to load from memory instead
of the filesystem. The loading occurs before any loaders are called.

```js
import dp from "dp";

dp({ virtuals: { "/config.json": '{ "foo": "bar" }' } });

const config = (await import("/config.json")).default;

// config.foo -> "bar"
```


## Loaders

|Name        |Extension|Dependency                         |
|------------|---------|-----------------------------------|
|json        |.json    |none                               |
|text        |.txt     |none                               |
|wasm        |.wasm    |none                               |
|csv         |.csv     |`csv-parse`                        |
|json5       |.json5   |`json5`                            |
|toml        |.toml    |`toml`                             |
|xml         |.xml     |`xml2js`                           |
|yaml        |.yaml    |`yaml`                             |
|react       |.jsx     |`esbuild`                          |
|vue         |.vue     |`vue`                              |
|svelte      |.svelte  |`svelte`                           |
|solid       |.solid   |`babel-present-solid` `@babel/core`|
|handlebars  |.hbs     |`handlebars`                       |
|marko       |.marko   |`marko`                            |
|typescript  |.ts      |`esbuild`                          |
|coffeescript|.coffee  |`coffeescript`                     |

## License

MIT

## Contributing

By submitting code you confirm that you are its sole owner and agree to place
it under the terms of MIT, see LICENSE.
