package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.composition.CompositionFile;
import com.ascargon.rocketshow.composition.CompositionFileService;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/file")
@CrossOrigin
public class CompositionFileController {

    private final CompositionFileService compositionFileService;

    public CompositionFileController(CompositionFileService compositionFileService) {
        this.compositionFileService = compositionFileService;
    }

    @GetMapping("list")
    public List<CompositionFile> getAll() {
        return compositionFileService.getAllFiles();
    }

    @PostMapping("upload")
    public CompositionFile upload(HttpServletRequest request) throws Exception {
        ServletFileUpload upload = new ServletFileUpload();
        FileItemIterator itemIterator = upload.getItemIterator(request);

        while (itemIterator.hasNext()) {
            FileItemStream item = itemIterator.next();
            String fileName = item.getName();

            if (fileName != null) {
                fileName = FilenameUtils.getName(fileName);
            }

            InputStream stream = item.openStream();

            if (!item.isFormField()) {
                return compositionFileService.saveFile(stream, fileName);
            }
        }

        return null;
    }

    @PostMapping("delete")
    public ResponseEntity<Void> saveSettings(@RequestParam("name") String name, @RequestParam("type") String type) {
        compositionFileService.deleteFile(name, type);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
