package com.ascargon.rocketshow.gstreamer;

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.GObject;
import org.freedesktop.gstreamer.lowlevel.GType;
import org.freedesktop.gstreamer.lowlevel.GValueAPI;
import org.freedesktop.gstreamer.lowlevel.GstNative;

/**
 * Added a function to append an array to an array
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

}
