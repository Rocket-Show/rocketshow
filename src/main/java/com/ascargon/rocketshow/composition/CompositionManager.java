package com.ascargon.rocketshow.composition;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBSource;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;
import com.ascargon.rocketshow.util.FileDurationGetter;

public class CompositionManager {

    private final static Logger logger = Logger.getLogger(CompositionManager.class);

    private final static String COMPOSITIONS_PATH = "compositions/";
    private final static String SETS_PATH = "sets/";

    private Manager manager;

    // All available compositions
    private List<Composition> compositionCache = new ArrayList<>();
    private List<Set> setCache = new ArrayList<>();

    public CompositionManager(Manager manager) {
        this.manager = manager;
    }

    private void sortCompositionCache() { compositionCache.sort(Comparator.comparing(Composition::getName)); }

    private void sortSetCache() {
        setCache.sort(Comparator.comparing(Set::getName));
    }

    private void finalizeLoadedComposition(Composition composition, String name) {
        composition.setName(name);
        composition.getMidiMapping().setParent(manager.getSettings().getMidiMapping());
        composition.setManager(manager);
    }

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
        composition = (Composition) jaxbUnmarshaller.unmarshal(new File(Manager.BASE_PATH + COMPOSITIONS_PATH + name));

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
        set = (Set) jaxbUnmarshaller.unmarshal(new File(Manager.BASE_PATH + SETS_PATH + name));

        logger.info("Set '" + name + "' successfully loaded");

        return set;
    }

    public Composition getComposition(String name) {
        for (Composition cachedComposition : compositionCache) {
            if (cachedComposition.getName().equals(name)) {
                return cachedComposition;
            }
        }

        return null;
    }

    public Set getSet(String name) {
        for (Set cachedSet : setCache) {
            if (cachedSet.getName().equals(name)) {
                return cachedSet;
            }
        }

        return null;
    }

    private void updateSets() throws Exception {
        // Update all sets (remove deleted files, update playing times),
        // when a composition has been changed/deleted

        List<Set> sets = getAllSets();

        for (Set set : sets) {
            // Load the full set
            Set fullSet = loadSet(set.getName());
            saveSet(fullSet);
        }
    }

    public List<Composition> getAllCompositions() {
        return compositionCache;
    }

    public List<Set> getAllSets() {
        return setCache;
    }

    public synchronized void loadAllCompositions() {
        File folder = new File(Manager.BASE_PATH + COMPOSITIONS_PATH);
        File[] fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    try {
                        Composition composition = loadComposition(file.getName());
                        compositionCache.add(composition);
                    } catch (Exception e) {
                        logger.error("Could not load composition '" + file.getName() + "'", e);
                    }
                }
            }
        }

        sortCompositionCache();
    }

    public synchronized void loadAllSets() {
        File folder = new File(Manager.BASE_PATH + SETS_PATH);
        File[] fileList = folder.listFiles();

        if (fileList != null) {
            for (File file : fileList) {
                if (file.isFile()) {
                    Set set = new Set();
                    set.setName(file.getName());

                    setCache.add(set);
                }
            }
        }

        sortSetCache();
    }

    public synchronized void saveComposition(Composition composition) throws Exception {
        // Get the duration of each file
        ExecutorService executor = Executors.newFixedThreadPool(30);

        for (com.ascargon.rocketshow.composition.File file : composition.getFileList()) {
            Runnable fileDurationGetter = new FileDurationGetter(file);
            executor.execute(fileDurationGetter);
        }

        executor.shutdown();

        // Wait until all threads are finished
        while (!executor.isTerminated()) {
            Thread.sleep(100);
        }

        // Set the duration of the composition to the maximum duration of the
        // files
        long maxDuration = 0;

        for (com.ascargon.rocketshow.composition.File file : composition.getFileList()) {
            if (file.getDurationMillis() > maxDuration) {
                maxDuration = file.getDurationMillis();
            }
        }
        composition.setDurationMillis(maxDuration);

        // Set the manager
        composition.setManager(manager);

        // Save the composition in XML
        File file = new File(Manager.BASE_PATH + COMPOSITIONS_PATH + composition.getName());
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

        File file = new File(Manager.BASE_PATH + SETS_PATH + set.getName());
        JAXBContext jaxbContext = JAXBContext.newInstance(Set.class);
        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        jaxbMarshaller.marshal(set, file);

        // Update the cache
        for (Set cachedSet : setCache) {
            if (cachedSet.getName().equals(set.getName())) {
                setCache.remove(cachedSet);
                break;
            }
        }
        setCache.add(set);
        sortSetCache();

        logger.info("Set '" + set.getName() + "' saved");
    }

    public synchronized void saveSet(Set set) throws Exception {
        saveSet(set, true);
    }

    public synchronized void deleteComposition(String name) throws Exception {
        // Delete the composition
        File file = new File(Manager.BASE_PATH + COMPOSITIONS_PATH + name);

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
        if (manager.getPlayer().getCompositionName().equals(name)) {
            if (compositionCache.size() > 0) {
                manager.getPlayer().setComposition(compositionCache.get(0));
            }
        }

        logger.info("Composition '" + name + "' deleted");
    }

    public synchronized void deleteSet(String name) {
        // Delete the set
        File file = new File(Manager.BASE_PATH + SETS_PATH + name);

        if (file.exists()) {
            boolean result = file.delete();

            if (!result) {
                logger.error("Could not delete set '" + name + "'");
            }
        }

        for (Set cachedSet : setCache) {
            if (cachedSet.getName().equals(name)) {
                setCache.remove(cachedSet);
                break;
            }
        }

        logger.info("Set '" + name + "' deleted");
    }

    public void nextComposition() {
        // Set the next composition (not set based)
        if (manager.getPlayer().getCompositionName().length() > 0) {
            for (int i = 0; i < compositionCache.size(); i++) {
                if (compositionCache.get(i).getName().equals(manager.getPlayer().getCompositionName())) {
                    if (compositionCache.size() > i + 1) {
                        try {
                            manager.getPlayer().setComposition(compositionCache.get(i + 1));
                        } catch (Exception e) {
                            logger.error("Could not set the next composition", e);
                        }
                    }

                    return;
                }
            }
        }
    }

    public void previousComposition() {
        // Set the previous composition (not set based)
        if (manager.getPlayer().getCompositionName().length() > 0) {
            for (int i = 0; i < compositionCache.size(); i++) {
                if (compositionCache.get(i).getName().equals(manager.getPlayer().getCompositionName())) {
                    if (i - 1 >= 0) {
                        try {
                            manager.getPlayer().setComposition(compositionCache.get(i - 1));
                        } catch (Exception e) {
                            logger.error("Could not set the previous composition", e);
                        }
                    }

                    return;
                }
            }
        }
    }

}
