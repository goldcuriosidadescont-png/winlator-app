package org.apache.commons.compress.archivers;
import java.io.*;
public abstract class ArchiveInputStream extends InputStream {
    public boolean canReadEntryData(Object e){return true;}
    public Object getNextEntry() throws IOException {return null;}
}
