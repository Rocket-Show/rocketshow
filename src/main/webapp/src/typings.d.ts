/* SystemJS module definition */
declare var module: NodeModule;
interface NodeModule {
  id: string;
}

declare module "sortablejs" {
  export interface Options {
    [key: string]: any;
  }

  const Sortable: any;
  export default Sortable;
}
