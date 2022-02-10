/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package karate;

import static java.nio.file.StandardCopyOption.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;


public class CopyFolderRecursively {

    // cp -r polyglot/src/test/resources/test-js-plugins core/target/plugins
    public  void copyFolder() throws IOException, InterruptedException {

        String workingDir = System.getProperty("user.dir");
        String projectRoot = workingDir.substring(0, workingDir.lastIndexOf("/"));

        Path src = Paths.get(projectRoot, "/polyglot/src/test/resources/test-js-plugins");
        
        Path dest = Paths.get(projectRoot, "/core/target/plugins/test-js-plugins/");
       
        Path jsPluginDir = Paths.get(projectRoot, "/core/target/plugins/test-js-plugins/");

        // delete test-js-plugins directory and files within
        if(Files.exists(jsPluginDir)) {

            Files.walk(jsPluginDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }     

        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> copy(source, dest.resolve(src.relativize(source))) );
        }
        // file set last modified
        setLastModified(jsPluginDir.toFile(), System.currentTimeMillis());
    }

    private void copy(Path source, Path dest) {
        try {
            Files.copy(source, dest, COPY_ATTRIBUTES);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    public void setLastModified(File file, long timestamp) {
        file.setLastModified(timestamp);
    }
    
}
