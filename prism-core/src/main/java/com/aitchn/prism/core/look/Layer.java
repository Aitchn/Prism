package com.aitchn.prism.core.look;

/**
 * The chunk layer a Java client draws a block with. A transparent texture only shows through in {@code CUTOUT}; in
 * {@code SOLID} its transparent texels render opaque, so a shell on a solid block is only invisible if the client
 * derives the layer from the texture.
 */
public enum Layer {
    CUTOUT,
    SOLID
}
