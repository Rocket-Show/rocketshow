package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.composition.Composition;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/composition")
@CrossOrigin
public class CompositionController {

    private final CompositionService compositionService;
    private final PlayerService playerService;
    private final SetService setService;

    public CompositionController(CompositionService compositionService, PlayerService playerService, SetService setService) {
        this.compositionService = compositionService;
        this.playerService = playerService;
        this.setService = setService;
    }

    @GetMapping("list")
    public List<Composition> getAll() {
        return compositionService.getAllCompositions();
    }

    @GetMapping
    public Composition get(@RequestParam("name") String name) {
        return compositionService.getComposition(name);
    }

    @PostMapping
    public ResponseEntity<Void> save(@RequestBody Composition composition) throws Exception {
        MidiCompositionFile midiCompositionFile = new MidiCompositionFile();
        composition.getCompositionFileList().add(midiCompositionFile);
        compositionService.saveComposition(composition);

        // If this is the current composition, read it again
        if (playerService.getCompositionName() != null
                && playerService.getCompositionName().equals(composition.getName())) {

            playerService.setComposition(compositionService.getComposition(composition.getName()),
                    true, true);
        }

        // Refresh the current set
        if (setService.getCurrentSet() != null) {
            playerService.loadSetAndComposition(setService.getCurrentSet().getName());
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("delete")
    public ResponseEntity<Void> delete(@RequestParam("name") String name) throws Exception {
        compositionService.deleteComposition(name, playerService);

        if (setService.getCurrentSet() != null) {
            playerService.loadSetAndComposition(setService.getCurrentSet().getName());
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

}
