import { of } from "rxjs";
import { ToastGeneralErrorService } from "./toast-general-error.service";

describe("ToastGeneralErrorService", () => {
  let toastrService: jasmine.SpyObj<any>;
  let service: ToastGeneralErrorService;

  beforeEach(() => {
    const translateService = jasmine.createSpyObj("TranslateService", ["get"]);
    translateService.get.and.returnValue(of({
      "settings.toast-general-error": "An error occured.",
      "settings.toast-general-error-title": "Error",
    }));
    toastrService = jasmine.createSpyObj("ToastrService", ["error"]);
    service = new ToastGeneralErrorService(translateService, toastrService);
  });

  it("shows the message as text, on its lines", () => {
    service.showMessage("song.wav: wavparse0: Internal data stream error.\nCould not link a:src -> b:sink. Accepted: audio/x-raw, channels=< 1, 2 >");

    expect(toastrService.error).toHaveBeenCalledWith(
      "An error occured.<hr /><small>song.wav: wavparse0: Internal data stream error.<br />"
        + "Could not link a:src -&gt; b:sink. Accepted: audio/x-raw, channels=&lt; 1, 2 &gt;</small>",
      "Error",
      jasmine.objectContaining({ enableHtml: true })
    );
  });

  it("shows only the general error without a message", () => {
    service.showMessage(undefined);

    expect(toastrService.error).toHaveBeenCalledWith("An error occured.", "Error", jasmine.any(Object));
  });
});
