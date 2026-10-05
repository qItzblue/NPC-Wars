package com.npcwars.dependency;

import java.net.URI;

/**
 * A resolved plugin download.
 *
 * @param pluginName the {@code name:} the jar's plugin.yml must declare, checked before the jar is installed
 * @param sha512     expected lower-case SHA-512 of the file, or {@code null} when the source publishes none
 */
public record Download(String pluginName, URI uri, String fileName, String sha512) {
}
