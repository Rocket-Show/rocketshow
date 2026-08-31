import { Injectable } from "@angular/core";
import { TranslateService } from "@ngx-translate/core";

/**
 * Builds the links from this app to rocketshow.net.
 *
 * Every one of them is tagged, and this is the only place that knows how. The
 * app carries no Google tag of its own -- it runs on a device that is usually
 * off the internet, and a unit standing on stage is not a place to load a
 * third-party tracker -- so a click on one of the SPARK hints only becomes
 * visible once the visitor lands on the website, which reads the tags off the
 * URL and reports them (see website/assets/script.js in the spark repository).
 *
 * The same arrangement is already in place for the online show designer, which
 * tags its own hardware hints with utm_source=designer. This app is the second
 * source, so the shape of the tags is deliberately identical: only the source
 * differs.
 */
@Injectable()
export class SparkUpsellService {

  private static readonly BASE_URL = "https://rocketshow.net";

  // The value the website matches on to recognise a visit that started in
  // here. Changing it makes the site stop attributing these clicks.
  private static readonly SOURCE = "webapp";

  // The languages rocketshow.net is translated into. English is served
  // unprefixed, and the app's remaining languages have no translated pages, so
  // they land on English too.
  private static readonly WEBSITE_LANGUAGES = ["de", "it", "es", "fr"];

  constructor(private translateService: TranslateService) {
  }

  /**
   * The tagged URL for a hint.
   *
   * @param placement names the spot the hint sits in. It travels as
   *                  utm_content and ends up on every conversion event the
   *                  website reports for the visit, so it has to stay stable
   *                  once there are numbers on it.
   * @param path      the page to open, without a language prefix.
   */
  getUrl(placement: string, path: string = "/spark"): string {
    const params = new URLSearchParams({
      utm_source: SparkUpsellService.SOURCE,
      utm_medium: "in-app",
      // Only the community edition ever shows these hints, so the campaign
      // says which edition the click came from without a second parameter.
      utm_campaign: "community-edition",
      utm_content: placement
    });

    return SparkUpsellService.BASE_URL + this.localize(path) + "?" + params.toString();
  }

  /**
   * Puts the visitor's language in front of the path, so they land on the page
   * they can read instead of on English with a redirect behind it.
   */
  private localize(path: string): string {
    const language = (this.translateService.currentLang || this.translateService.defaultLang || "en")
      .slice(0, 2)
      .toLowerCase();

    if (SparkUpsellService.WEBSITE_LANGUAGES.indexOf(language) < 0) {
      return path;
    }

    return "/" + language + path;
  }

}
