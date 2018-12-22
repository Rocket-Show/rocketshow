package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.CapabilitiesService;
import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.SettingsService;
import com.ascargon.rocketshow.audio.AudioCompositionFile;
import com.ascargon.rocketshow.gstreamer.GstDiscovererService;
import com.ascargon.rocketshow.midi.MidiCompositionFile;
import com.ascargon.rocketshow.video.VideoCompositionFile;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Handle storage, sorting, etc. of compositions and sets.
 */
@Service
public class DefaultCompositionService implements CompositionService {

    private final static Logger logger = LoggerFactory.getLogger(DefaultCompositionService.class);

    private final static String COMPOSITIONS_PATH = "compositions";
    private final static String SETS_PATH = "sets";

    private final SettingsService settingsService;
    private final CapabilitiesService capabilitiesService;
    private final GstDiscovererService gstDiscovererService;

    // All available compositions
    private final List<Composition> compositionCache = new ArrayList<>();
    private final List<Set> compositionSetCache = new ArrayList<>();

    public DefaultCompositionService(SettingsService settingsService, CapabilitiesService capabilitiesService, GstDiscovererService gstDiscovererService) {
        this.settingsService = settingsService;
        this.capabilitiesService = capabilitiesService;
        this.gstDiscovererService = gstDiscovererService;

        // Initialize the cache
        this.loadAllCompositions();
        this.loadAllSets();
    }

    private void sortCompositionCache() {
        compositionCache.sort(Comparator.comparing(Composition::getName));
    }

    private void sortSetCache() {
        compositionSetCache.sort(Comparator.comparing(Set::getName));
    }

    private void finalizeLoadedComposition(Composition composition, String name) {
        composition.setName(name);
    }

    @Override
    public Composition cloneComposition(Composition composition) throws Exception {
        // Return a deep cloned instance of the composition
        Composition clonedComposition;
        JAXBContext jaxbContext = JAXBContext.newInstance(Composition.class);

        JAXBSource source = new JAXBSource(jaxbContext, composition);

        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        clonedComposition = (Composition) unmarshaller.unmarshal(source);

        finalizeLoadedComposition(clonedComposition, composition.getName());

        return clonedComposition;
    }

    private Composition loadComposition(String name) throws Exception {
        Composition composition;

        logger.debug("Loading composition " + name + "...");

        // Load a composition
        JAXBContext jaxbContext = JAXBContext.newInstance(Composition.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        composition = (Composition) jaxbUnmarshaller.unmarshal(new File(settingsService.getSettings().getBasePath() + File.separator + COMPOSITIONS_PATH + File.separator + name + ".xml"));

        finalizeLoadedComposition(composition, name);

        logger.debug("Composition '" + name + "' successfully loaded");

        return composition;
    }

    private Set loadSet(String name) throws Exception {
        Set set;

        logger.debug("Loading set '" + name + "'...");

        // Load a set
        JAXBContext jaxbContext = JAXBContext.newInstance(Set.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        set = (Set) jaxbUnmarshaller.unmarshal(new File(settingsService.getSettings().getBasePath() + File.separator + SETS_PATH + File.separator + name + ".xml"));

        logger.info("Set '" + name + "' successfully loaded");

        return set;
    }

    @Override
    public Composition getComposition(String name) {
        for (Composition cachedComposition : compositionCache) {
            if (cachedComposition.getName().equals(name)) {
                return cachedComposition;
            }
        }

        return null;
    }

    @Override
    public Set getSet(String name) {
        for (Set cachedCompositionSet : compositionSetCache) {
            if (cachedCompositionSet.getName().equals(name)) {
                return cachedCompositionSet;
            }
        }

        return null;
    }

    private void updateSets() throws Exception {
        // Update all sets (remove deleted files, update playing times),
        // when a composition has been changed/deleted

        List<Set> compositionSets = getAllSets();

        for (Set set : compositionSets) {
            // Load the full set
            Set fullCompositionSet = loadSet(set.getName());
            saveSet(fullCompositionSet);
        }
    }

    @Override
    public List<Composition> getAllCompositions() {
        return compositionCache;
    }

    @Override
    public List<Set> getAllSets() {
        return compositionSetCache;
    }

    @Override
    public synchronized void loadAllCompositions() {
        File folder = new File(settingsService.getSettings().getBasePath() + File.separator + COMPOSITIONS_PATH);
        File[] fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    try {
                        Composition composition = loadComposition(file.getName().substring(0, file.getName().length() - 4));
                        compositionCache.add(composition);
                    } catch (Exception e) {
                        logger.error("Could not load composition '" + file.getName() + "'", e);
                    }
                }
            }
        }

        sortCompositionCache();
    }

    @Override
    public synchronized void loadAllSets() {
        File folder = new File(settingsService.getSettings().getBasePath() + File.separator + SETS_PATH);
        File[] fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    String setName = file.getName().substring(0, file.getName().length() - 4);
                    try {
                        Set set = loadSet(setName);
                        set.setName(setName);
                        compositionSetCache.add(set);
                    } catch (Exception e) {
                        logger.error("Could not load composition '" + file.getName() + "'", e);
                    }
                }
            }
        }

        sortSetCache();
    }

    private void createDirectoryIfNotExists(String directory) throws IOException {
        Path path = Paths.get(directory);

        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }
    }

    private long getMidiDuration(String path) throws Exception {
        long duration;

        Sequence sequence = MidiSystem.getSequence(new File(path));
        Sequencer sequencer = MidiSystem.getSequencer();
        sequencer.open();
        sequencer.setSequence(sequence);
        duration = sequencer.getMicrosecondLength() / 1000;
        sequencer.close();

        return duration;
    }

    @Override
    public synchronized void saveComposition(Composition composition) throws Exception {
        // Set additional information for each file
        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            String path = settingsService.getSettings().getBasePath() + settingsService.getSettings().getMediaPath() + File.separator;

            if (compositionFile instanceof MidiCompositionFile) {
                // Getting duration with Gstreamer does not work (missing plugins)
                compositionFile.setDurationMillis(getMidiDuration(path + settingsService.getSettings().getMidiPath() + File.separator + compositionFile.getName()));
            } else if (compositionFile instanceof AudioCompositionFile) {
                if (!capabilitiesService.getCapabilities().isGstreamer()) {
                    throw new Exception("Gstreamer is not available");
                }

                AudioCompositionFile audioCompositionFile = ((AudioCompositionFile) compositionFile);

                Pointer discovererInformation = gstDiscovererService.getDiscovererInformation(path + settingsService.getSettings().getAudioPath() + File.separator + compositionFile.getName());
                audioCompositionFile.setDurationMillis(gstDiscovererService.getDurationMillis(discovererInformation));
                audioCompositionFile.setChannels(gstDiscovererService.getChannels(discovererInformation));
            } else if (compositionFile instanceof VideoCompositionFile) {
                if (!capabilitiesService.getCapabilities().isGstreamer()) {
                    throw new Exception("Gstreamer is not available");
                }

                Pointer discovererInformation = gstDiscovererService.getDiscovererInformation(path + settingsService.getSettings().getVideoPath() + File.separator + compositionFile.getName());
                compositionFile.setDurationMillis(gstDiscovererService.getDurationMillis(discovererInformation));
            }
        }

        // Set the duration of the composition to the maximum duration of the
        // files
        long maxDuration = 0;

        for (CompositionFile compositionFile : composition.getCompositionFileList()) {
            if (compositionFile.getDurationMillis() > maxDuration) {
                maxDuration = compositionFile.getDurationMillis();
            }
        }
        composition.setDurationMillis(maxDuration);

        // Save the composition in XML
        String directory = settingsService.getSettings().getBasePath() + File.separator + COMPOSITIONS_PATH;
        createDirectoryIfNotExists(directory);

        File file = new File(directory + File.separator + composition.getName() + ".xml");
        JAXBContext jaxbContext = JAXBContext.newInstance(Composition.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.marshal(composition, file);

        // Update the cache
        for (Composition cachedComposition : compositionCache) {
            if (cachedComposition.getName().equals(composition.getName())) {
                compositionCache.remove(cachedComposition);
                break;
            }
        }
        compositionCache.add(composition);
        sortCompositionCache();

        updateSets();

        logger.info("Composition '" + composition.getName() + "' saved");
    }

    @Override
    public synchronized void saveSet(Set set, boolean checkCompositions) throws Exception {
        if (checkCompositions) {
            // Update all composition information
            Iterator<SetComposition> iterator = set.getSetCompositionList().iterator();

            while (iterator.hasNext()) {
                SetComposition setComposition = iterator.next();

                Composition composition = null;

                try {
                    composition = loadComposition(setComposition.getName());
                } catch (Exception e) {
                    logger.error("Could not load composition", e);
                }

                if (composition == null) {
                    // The composition does not exist anymore (has been deleted)
                    // --> delete it from the set
                    iterator.remove();
                } else {
                    // The composition still exists -> update some information
                    setComposition.setDurationMillis(composition.getDurationMillis());
                }
            }
        }

        String directory = settingsService.getSettings().getBasePath() + File.separator + SETS_PATH;
        createDirectoryIfNotExists(directory);

        File file = new File(directory + File.separator + set.getName() + ".xml");
        JAXBContext jaxbContext = JAXBContext.newInstance(Set.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.marshal(set, file);

        // Update the cache
        for (Set cachedCompositionSet : compositionSetCache) {
            if (cachedCompositionSet.getName().equals(set.getName())) {
                compositionSetCache.remove(cachedCompositionSet);
                break;
            }
        }
        compositionSetCache.add(set);
        sortSetCache();

        logger.info("Set '" + set.getName() + "' saved");
    }

    @Override
    public synchronized void saveSet(Set set) throws Exception {
        saveSet(set, true);
    }

    @Override
    public synchronized void deleteComposition(String name, PlayerService playerService) throws Exception {
        // Delete the composition
        File file = new File(settingsService.getSettings().getBasePath() + File.separator + COMPOSITIONS_PATH + File.separator + name + ".xml");

        if (file.exists()) {
            boolean result = file.delete();

            if (!result) {
                logger.error("Could not delete composition '" + name + "'");
            }
        }

        for (Composition cachedComposition : compositionCache) {
            if (cachedComposition.getName().equals(name)) {
                compositionCache.remove(cachedComposition);
                break;
            }
        }

        updateSets();

        // Set another composition, if we deleted the current one
        if (playerService.getCompositionName().equals(name)) {
            if (compositionCache.size() > 0) {
                playerService.setComposition(compositionCache.get(0));
            }
        }

        logger.info("Composition '" + name + "' deleted");
    }

    @Override
    public synchronized void deleteSet(String name) {
        // Delete the set
        File file = new File(settingsService.getSettings().getBasePath() + File.separator + SETS_PATH + File.separator + name + ".xml");

        if (file.exists()) {
            boolean result = file.delete();

            if (!result) {
                logger.error("Could not delete set '" + name + "'");
            }
        }

        for (Set cachedCompositionSet : compositionSetCache) {
            if (cachedCompositionSet.getName().equals(name)) {
                compositionSetCache.remove(cachedCompositionSet);
                break;
            }
        }

        logger.info("Set '" + name + "' deleted");
    }

    @Override
    public Composition getNextComposition(Composition currentComposition) {
        // Get the next composition (not set based)
        if(currentComposition != null) {
            for (int i = 0; i < compositionCache.size(); i++) {
                if (compositionCache.get(i).getName().equals(currentComposition.getName())) {
                    if (compositionCache.size() > i + 1) {
                        return compositionCache.get(i + 1);
                    } else {
                        return null;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public Composition getPreviousComposition(Composition currentComposition) {
        // Get the next composition (not set based)
        if (currentComposition == null) {
            return null;
        }

        for (int i = 0; i < compositionCache.size(); i++) {
            if (compositionCache.get(i).getName().equals(currentComposition.getName())) {
                if (i - 1 >= 0) {
                    return compositionCache.get(i - 1);
                } else {
                    return null;
                }
            }
        }

        return null;
    }

}
