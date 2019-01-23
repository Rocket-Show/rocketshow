package com.ascargon.rocketshow.composition;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.util.List;

@Service
public interface LeadSheetService {

    List<LeadSheet> getAllLeadSheets();

    void deleteLeadSheet(String name);

    LeadSheet saveLeadSheet(InputStream uploadedInputStream, String fileName);

    File getImage(String name) throws Exception;

}
