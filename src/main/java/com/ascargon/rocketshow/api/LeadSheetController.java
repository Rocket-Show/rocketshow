package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionFileService;
import com.ascargon.rocketshow.composition.LeadSheet;
import com.ascargon.rocketshow.composition.LeadSheetService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/lead-sheet")
@CrossOrigin
public class LeadSheetController {

    private final LeadSheetService leadSheetService;

    public LeadSheetController(LeadSheetService leadSheetService) {
        this.leadSheetService = leadSheetService;
    }

    @GetMapping("list")
    public List<LeadSheet> getAll() {
        return leadSheetService.getAllLeadSheets();
    }

    @PostMapping("upload")
    public LeadSheet upload(@RequestParam("file") MultipartFile file) throws IOException {
        return leadSheetService.saveLeadSheet(file.getInputStream(), file.getOriginalFilename());
    }

    @PostMapping("delete")
    public ResponseEntity<Void> saveSettings(@RequestParam("name") String name) {
        leadSheetService.deleteLeadSheet(name);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("image")
    public ResponseEntity<Resource> downloadLogs(@RequestParam("name") String name) throws Exception {
        InputStreamResource resource = new InputStreamResource(new FileInputStream(leadSheetService.getImage(name)));

        return ResponseEntity
                .ok()
                .body(resource);
    }

}
