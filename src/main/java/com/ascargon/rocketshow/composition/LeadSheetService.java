package com.ascargon.rocketshow.composition;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.util.List;

@Service
public interface LeadSheetService {

    List<LeadSheet> getAllLeadSheets();

    void deleteLeadSheet(String name);

    void saveLeadSheetInit(String fileName) throws Exception;

    void saveLeadSheetAddChunk(InputStream inputStream, String fileName) throws Exception;

    LeadSheet saveLeadSheetFinish(String fileName) throws Exception;

    File getImage(String name) throws Exception;

}
