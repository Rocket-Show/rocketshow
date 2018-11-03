// The file contents for the current environment will overwrite these during build.
// The build system defaults to the dev environment which uses `environment.ts`, but if you do
// `ng build --env=prod` then `environment.prod.ts` will be used instead.
// The list of which env maps to which file can be found in `.angular-cli.json`.

export const environment = {
  production: false,
  name: "dev",

  // Local backend in local Tomcat
  //localBackend: "localhost:8080/ROOT",

  // Local backend in docker
  //localBackend: "localhost:8080",

  localBackend: "rocketshow.local",

  debug: true
};
