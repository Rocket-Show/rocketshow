import { TestBed } from "@angular/core/testing";
import { TranslateService } from "@ngx-translate/core";

import { SparkUpsellService } from "./spark-upsell.service";

/**
 * These assertions are the contract with rocketshow.net: the website matches on
 * utm_source to recognise a visit that started in here, and reports utm_content
 * as the placement that earned it (see website/assets/script.js in the spark
 * repository). Changing either value silently stops the attribution, which is
 * why the exact strings are pinned here.
 */
describe("SparkUpsellService", () => {
  let service: SparkUpsellService;
  let translateService: { currentLang: string, defaultLang: string };

  beforeEach(() => {
    translateService = { currentLang: "en", defaultLang: "en" };

    TestBed.configureTestingModule({
      providers: [
        SparkUpsellService,
        { provide: TranslateService, useValue: translateService }
      ]
    });

    service = TestBed.inject(SparkUpsellService);
  });

  it("tags the link so the website can attribute the visit", () => {
    const url = new URL(service.getUrl("settings-lighting"));

    expect(url.origin + url.pathname).toBe("https://rocketshow.net/spark");
    expect(url.searchParams.get("utm_source")).toBe("webapp");
    expect(url.searchParams.get("utm_medium")).toBe("in-app");
    expect(url.searchParams.get("utm_campaign")).toBe("community-edition");
    expect(url.searchParams.get("utm_content")).toBe("settings-lighting");
  });

  it("opens the page in the language the app is showing", () => {
    translateService.currentLang = "de";

    expect(new URL(service.getUrl("settings-midi")).pathname).toBe("/de/spark");
  });

  it("falls back to English where the website has no translation", () => {
    translateService.currentLang = "zh";

    expect(new URL(service.getUrl("settings-midi")).pathname).toBe("/spark");
  });

  it("keeps the language prefix in front of a page other than SPARK", () => {
    translateService.currentLang = "fr";

    expect(new URL(service.getUrl("settings-info", "/products")).pathname).toBe("/fr/products");
  });
});
