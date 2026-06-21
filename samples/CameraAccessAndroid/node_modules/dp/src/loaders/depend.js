import { tryreturn } from "rcompat/async";
import { red, yellow, bold } from "rcompat/colors";
import console from "rcompat/console";

const MODULE_NOT_FOUND = "ERR_MODULE_NOT_FOUND";

const error_message = (loader, dependencies) =>
  `${red("ERROR")} missing dependencies for loader \`${yellow(loader)}\`
  -> install with ${bold(`npm install ${dependencies.join(" ")}`)}`;

export default async (dependencies, loader) => {
  const missing = (await Promise.all(dependencies.map(dependency =>
    tryreturn(async _ => {
      await import(dependency);
      return null;
    }).orelse(({ code }) => code === MODULE_NOT_FOUND ? dependency : null)))
  ).filter(failed => failed !== null);

  if (missing.length > 0) {
    console.log(error_message(loader, missing));
  }
};
