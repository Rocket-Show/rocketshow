package com.ascargon.rocketshow.gstreamer;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.elements.BaseSink;
import org.freedesktop.gstreamer.lowlevel.GType;
import org.freedesktop.gstreamer.lowlevel.GValueAPI;
import org.freedesktop.gstreamer.lowlevel.GstNative;

import java.awt.*;

/**
 * Functions for Gstreamer not provided in the official Java Gstreamer bindings.
 */
public interface GstApi extends Library {

    GstApi GST_API = GstNative.load(GstApi.class);

    // Get the Gstreamer GType "GST_TYPE_ARRAY"
    GType gst_value_array_get_type();

    // Append a GST_TYPE_ARRAY to a GST_TYPE_ARRAY (different to GValueArray)
    void gst_value_array_append_value(GValueAPI.GValue var1, Pointer var2);

    // Set a pointer property to an object, like a GST_TYPE_ARRAY
    void g_object_set_property (GObject object, String property, Pointer value);

    // Get the caps from a string (e.g. "audio/x-raw,channels=4")
    Caps gst_caps_from_string(String string);

    // Unref an object
    void gst_object_unref(Object object);

    // Get the bus from an element (usually the pipeline)
    Bus gst_element_get_bus(Element element);

}
