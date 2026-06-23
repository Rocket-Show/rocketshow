import { Action } from "./action";

export class ActionHttp extends Action {
  httpMethod: string = "POST";
  url: string;
  body: string;
  headerList = new Map<string, string>();

  constructor(data?: any) {
    super(data);

    if (!data) {
      return;
    }

    this.httpMethod = data.httpMethod;
    this.url = data.url;
    this.body = data.body;

    if (data.headerList) {
      this.headerList = new Map<string, string>(
        Object.entries(data.headerList)
      );
    }

    if (!this.httpMethod) {
      this.httpMethod = "POST";
    }
  }

  toJSON() {
    return {
      actionHttp: {
        ...this,
        headerList: Object.fromEntries(this.headerList),
      },
    };
  }
}
