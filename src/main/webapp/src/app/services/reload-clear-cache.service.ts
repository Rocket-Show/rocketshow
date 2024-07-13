import { Injectable } from "@angular/core";

@Injectable({
  providedIn: "root",
})
export class ReloadClearCacheService {
  public reload(): void {
    const currentUrl: string = window.location.href;
    const url: URL = new URL(currentUrl);
    url.searchParams.set("cachebuster", new Date().getTime().toString());
    window.location.href = url.href;
  }
}
