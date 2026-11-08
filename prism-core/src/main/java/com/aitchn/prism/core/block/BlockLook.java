package com.aitchn.prism.core.block;

/**
 * What the client is shown for one custom block position: the global block state id of the inert carrier the server
 * stores there, and the id of the look (a vanilla state the resource pack redraws) a viewer must see instead.
 */
public record BlockLook(int carrier, int look) {}
