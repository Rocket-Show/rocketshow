import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { NEVER } from "rxjs";

import { CompositionService } from "./composition.service";
import { FileService } from "./file.service";
import { LeadSheetService } from "./lead-sheet.service";
import { SettingsPersonalService } from "./settings-personal.service";
import { StateService } from "./state.service";
import { TransportService } from "./transport.service";

/**
 * Names of compositions, sets and files go into the URLs of the requests. A name with characters like "&", "#", "+"
 * or "%" reaches the unit only if it is encoded, otherwise another one (e.g. "Rock Roll" instead of "Rock+Roll").
 */
describe("Names in requests", () => {
  const name = "Rock & Roll + C#1 50%";
  const encoded = "Rock%20%26%20Roll%20%2B%20C%231%2050%25";
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        CompositionService,
        FileService,
        LeadSheetService,
        TransportService,
        // No state of the unit yet
        { provide: StateService, useValue: { getState: () => NEVER, state: NEVER } },
        { provide: SettingsPersonalService, useValue: { settingsChanged: NEVER } }
      ]
    });

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it("encodes the names of compositions and sets", () => {
    const compositionService = TestBed.inject(CompositionService);

    compositionService.getComposition(name).subscribe();
    http.expectOne("composition?name=" + encoded).flush({ name: name });

    compositionService.deleteComposition(name).subscribe();
    http.expectOne("composition/delete?name=" + encoded).flush(null);

    compositionService.loadSet(name).subscribe();
    http.expectOne("set/load?name=" + encoded).flush(null);

    compositionService.getSet(name).subscribe();
    http.expectOne("set/details?name=" + encoded).flush({ name: name, setCompositionList: [] });

    compositionService.deleteSet(name).subscribe();
    http.expectOne("set/delete?name=" + encoded).flush(null);
  });

  it("encodes the name of the composition to select", () => {
    TestBed.inject(TransportService).setCompositionName(name).subscribe();

    http.expectOne("transport/set-composition-name?name=" + encoded).flush(null);
  });

  it("encodes the names of files to delete", () => {
    TestBed.inject(FileService).deleteFile({ name: name + ".wav", type: "AUDIO" } as any).subscribe();
    http.expectOne("file/delete?name=" + encoded + ".wav&type=AUDIO").flush(null);

    TestBed.inject(LeadSheetService).deleteLeadSheet({ name: name + ".png" } as any).subscribe();
    http.expectOne("lead-sheet/delete?name=" + encoded + ".png").flush(null);
  });
});
