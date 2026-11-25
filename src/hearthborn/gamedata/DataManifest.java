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

package hearthborn.gamedata;

import java.io.IOException;
import java.util.Map;

import static hearthborn.gamedata.DataValidator.*;

final class DataManifest {
    private static final int SUPPORTED_SCHEMA = 2;
    final int schemaVersion;
    final int dataVersion;

    private DataManifest(int schemaVersion, int dataVersion) {
        this.schemaVersion = schemaVersion;
        this.dataVersion = dataVersion;
    }

    static DataManifest parse(Object raw) throws IOException {
        Map<String, Object> manifest = object(raw, "manifest");
        keys(manifest, "schemaVersion dataVersion", "");
        int schema = positiveInteger(manifest.get("schemaVersion"), "schemaVersion");
        if(schema > SUPPORTED_SCHEMA) throw invalid("manifest", "unsupported schemaVersion: " + schema);
        return new DataManifest(schema, positiveInteger(manifest.get("dataVersion"), "dataVersion"));
    }
}
