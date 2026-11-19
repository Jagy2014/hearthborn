/*
 * This file is part of Hearthborn Client.
 *
 * Copyright (C) 2026 Jagy2014
 * GitHub: https://github.com/Jagy2014
 *
 * Licensed under the GNU Lesser General Public License
 * version 3.0 (LGPL-3.0).
 *
 * See the COPYING file for details.
 */

package hearthborn.io;
import java.io.*;
import java.nio.file.*;

/**
 * Atomic storage for local client records; never writes to game or reference-client configuration.
 *
 * @author Jagy2014
 */
public final class LocalStore {
    private static Path file(String name) { return DataDirectory.path().resolve(name+".dat"); }
    public static synchronized Object read(String name) {
        Path path=file(name);
        if(!Files.exists(path))return null;
        try(ObjectInputStream in=new ObjectInputStream(Files.newInputStream(path))) { return in.readObject(); }
        catch(IOException | ClassNotFoundException e) { System.err.println("Could not read "+path+": "+e.getMessage());return null; }
    }
    public static synchronized void write(String name,Serializable value) {
        Path target=file(name),temporary=target.resolveSibling(target.getFileName()+".tmp");
        try {
            Files.createDirectories(target.getParent());
            try(ObjectOutputStream out=new ObjectOutputStream(Files.newOutputStream(temporary))) { out.writeObject(value); }
            try { Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING); }
        } catch(IOException e) { System.err.println("Could not save "+target+": "+e.getMessage()); }
    }
    private LocalStore() {}
}
