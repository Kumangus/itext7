package com.itextpdf.basics.font.cmap;


import com.itextpdf.basics.Utilities;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * This class represents a CMap file.
 *
 * @author Ben Litchfield (ben@benlitchfield.com)
 * @since 2.1.4
 */
public class CMapToUnicode extends AbstractCMap {

    private Map<Integer, String> singleByteMappings = new HashMap<Integer, String>();
    private Map<Integer, String> doubleByteMappings = new HashMap<Integer, String>();

    /**
     * Creates a new instance of CMap.
     */
    public CMapToUnicode() {
        //default constructor
    }

    /**
     * This will tell if this cmap has any one byte mappings.
     *
     * @return true If there are any one byte mappings, false otherwise.
     */
    public boolean hasOneByteMappings() {
        return !singleByteMappings.isEmpty();
    }

    /**
     * This will tell if this cmap has any two byte mappings.
     *
     * @return true If there are any two byte mappings, false otherwise.
     */
    public boolean hasTwoByteMappings() {
        return !doubleByteMappings.isEmpty();
    }

    /**
     * This will perform a lookup into the map.
     *
     * @param code   The code used to lookup.
     * @param offset The offset into the byte array.
     * @param length The length of the data we are getting.
     * @return The string that matches the lookup.
     */
    public String lookup(byte[] code, int offset, int length) {

        String result = null;
        Integer key = null;
        if (length == 1) {

            key = Integer.valueOf(code[offset] & 0xff);
            result = singleByteMappings.get(key);
        } else if (length == 2) {
            int intKey = code[offset] & 0xff;
            intKey <<= 8;
            intKey += code[offset + 1] & 0xff;
            key = Integer.valueOf(intKey);

            result = doubleByteMappings.get(key);
        }

        return result;
    }

    public Map<Integer, Integer> createReverseMapping() throws IOException {
        Map<Integer, Integer> result = new HashMap<Integer, Integer>();
        for (Map.Entry<Integer, String> entry : singleByteMappings.entrySet()) {
            result.put(convertToInt(entry.getValue()), entry.getKey());
        }
        for (Map.Entry<Integer, String> entry : doubleByteMappings.entrySet()) {
            result.put(convertToInt(entry.getValue()), entry.getKey());
        }
        return result;
    }

    public Map<Integer, Integer> createDirectMapping() throws IOException {
        Map<Integer, Integer> result = new HashMap<Integer, Integer>();
        for (Map.Entry<Integer, String> entry : singleByteMappings.entrySet()) {
            result.put(entry.getKey(), convertToInt(entry.getValue()));
        }
        for (Map.Entry<Integer, String> entry : doubleByteMappings.entrySet()) {
            result.put(entry.getKey(), convertToInt(entry.getValue()));
        }
        return result;
    }

    private int convertToInt(String s) throws IOException {
        byte[] b = s.getBytes("UTF-16BE");
        int value = 0;
        for (int i = 0; i < b.length - 1; i++) {
            value += b[i] & 0xff;
            value <<= 8;
        }
        value += b[b.length - 1] & 0xff;
        return value;
    }

    void addChar(int cid, String uni) {
        doubleByteMappings.put(Integer.valueOf(cid), uni);
    }

    @Override
    void addChar(String mark, CMapObject code) {

        byte[] src = mark.getBytes();
        String dest = null;
        try {
            dest = createStringFromBytes(code.toString().getBytes());

            if (src.length == 1) {
                singleByteMappings.put(Integer.valueOf(src[0] & 0xff), dest);
            } else if (src.length == 2) {
                int intSrc = src[0] & 0xFF;
                intSrc <<= 8;
                intSrc |= src[1] & 0xFF;
                doubleByteMappings.put(Integer.valueOf(intSrc), dest);
            } else {
                throw new RuntimeException();
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }

    }

    private String createStringFromBytes(byte[] bytes) throws IOException {
        String retval = null;
        if (bytes.length == 1) {
            retval = new String(bytes);
        } else {
            retval = new String(bytes, "UTF-16BE");
        }
        return retval;
    }

    public static CMapToUnicode getIdentity() {
        CMapToUnicode uni = new CMapToUnicode();
        for (int i = 0; i < 65537; i++) {
            uni.addChar(i, Utilities.convertFromUtf32(i));
        }
        return uni;
    }
}