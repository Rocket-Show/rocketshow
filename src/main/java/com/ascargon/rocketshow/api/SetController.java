package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.composition.CompositionService;
import com.ascargon.rocketshow.composition.Set;
import com.ascargon.rocketshow.composition.SetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController()
@RequestMapping("${spring.data.rest.base-path}/set")
@CrossOrigin
public class SetController {

    private final SetService setService;
    private final CompositionService compositionService;
    private final PlayerService playerService;

    public SetController(SetService setService, CompositionService compositionService, PlayerService playerService) {
        this.setService = setService;
        this.compositionService = compositionService;
        this.playerService = playerService;
    }

    @GetMapping("list")
    public List<Set> getAll() {
        return compositionService.getAllSets();
    }

    @PostMapping("load")
    public ResponseEntity<Void> load(@RequestParam("name") String name) throws Exception {
        playerService.loadSetAndComposition(name);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping
    public Set getCurrent() {
        return setService.getCurrentSet();
    }

    @GetMapping("details")
    public Set getByName(@RequestParam("name") String name) {
        return compositionService.getSet(name);
    }

    @PostMapping
    public ResponseEntity<Void> save(@RequestBody Set set) throws Exception {
        compositionService.saveSet(set);
        playerService.loadSetAndComposition(set.getName());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("delete")
    public ResponseEntity<Void> delete(@RequestParam("name") String name) throws Exception {
        compositionService.deleteSet(name);

        // Load the default set, if the current set has been deleted
        if (setService.getCurrentSet() != null) {
            if (setService.getCurrentSet().getName().equals(name)) {
                playerService.loadSetAndComposition("");
            }
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

}
