package com.spire.restbridge.service;

/**
 * Data type and repeating flag of one attribute of a type.
 */
class AttrMeta {

    final String type;
    final boolean repeating;

    AttrMeta(String type, boolean repeating) {
        this.type = type;
        this.repeating = repeating;
    }
}
