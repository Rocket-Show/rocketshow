package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.composition.CompositionFileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/file")
@CrossOrigin
public class CompositionFileController {

    private final ControllerService controllerService;
    private final CompositionFileService compositionFileService;

    public CompositionFileController(ControllerService controllerService, CompositionFileService compositionFileService) {
        this.controllerService = controllerService;
        this.compositionFileService = compositionFileService;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return controllerService.handleException(exception);
    }

    @GetMapping("list")
    public List<CompositionFile> getAll() {
        return compositionFileService.getAllFiles();
    }

    @PostMapping("upload")
    public CompositionFile upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("dzchunkindex") Long dzchunkindex,
            @RequestParam("dztotalchunkcount") Long dztotalchunkcount
    ) throws Exception {
        String fileName = file.getOriginalFilename();
        CompositionFile compositionFile = null;
        if (dzchunkindex == 0) {
            compositionFileService.saveFileInit(fileName);
        }
        compositionFileService.saveFileAddChunk(file.getInputStream(), fileName);
        if (dzchunkindex.equals(dztotalchunkcount - 1)) {
            compositionFile = compositionFileService.saveFileFinish(fileName);
        }
        return compositionFile;
    }

    @PostMapping("delete")
    public ResponseEntity<Void> saveSettings(@RequestParam("name") String name, @RequestParam("type") String type) {
        compositionFileService.deleteFile(name, type);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("get")
    public ResponseEntity<Resource> getFile(@RequestParam("name") String name, @RequestParam("type") String type) throws Exception {
        InputStreamResource resource = new InputStreamResource(new FileInputStream(compositionFileService.getFile(name, type)));

        return ResponseEntity
                .ok()
                .body(resource);
    }

}
