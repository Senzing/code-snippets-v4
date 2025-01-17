package com.senzing.runner;

import java.util.*;
import java.io.*;
import javax.json.*;
import java.nio.charset.Charset;

import static javax.json.stream.JsonGenerator.PRETTY_PRINTING;

/**
 * Provides utilities for the snippet runner.
 */
public class Utilities {
    /**
     * Constant for the name of the UTF-8 character encoding.
     */
    public static final String UTF_8 = "UTF-8";

    /**
     * Constant for the UTF-8 {@link Charset}.
     */
    public static final Charset UTF_8_CHARSET = Charset.forName(UTF_8);
    
    /**
     * Pretty printing {@link JsonWriterFactory}.
     */
    private static JsonWriterFactory PRETTY_WRITER_FACTORY = Json
            .createWriterFactory(Collections.singletonMap(PRETTY_PRINTING, true));

    /**
     * Parses JSON text as a {@link JsonObject}. If the specified text is not
     * formatted as a JSON object then an exception will be thrown.
     *
     * @param jsonText The JSON text to be parsed.
     *
     * @return The parsed {@link JsonObject}.
     */
    public static JsonObject parseJsonObject(String jsonText) {
        if (jsonText == null)
            return null;
        StringReader sr = new StringReader(jsonText);
        JsonReader jsonReader = Json.createReader(sr);
        return jsonReader.readObject();
    }

    /**
     * Converts the specified {@link JsonValue} to a JSON string.
     *
     * @param writer      The {@link Writer} to write to.
     *
     * @param jsonValue   The {@link JsonValue} describing the JSON.
     *
     * @param prettyPrint Whether or not to pretty-print the JSON text.
     *
     * @return The specified {@link Writer}.
     *
     * @param <T> The type of the writer to which the write the {@link JsonValue}.
     */
    public static <T extends Writer> T toJsonText(T writer, JsonValue jsonValue, boolean prettyPrint) {
        Objects.requireNonNull(writer, "Writer cannot be null");

        JsonWriter jsonWriter = (prettyPrint) 
            ? PRETTY_WRITER_FACTORY.createWriter(writer) : Json.createWriter(writer);

        if (jsonValue != null) {
            jsonWriter.write(jsonValue);
        } else {
            jsonWriter.write(JsonValue.NULL);
        }

        return writer;
    }

    /**
     * Converts the specified {@link JsonValue} to a JSON string.
     *
     * @param jsonValue   The {@link JsonValue} describing the JSON.
     *
     * @param prettyPrint Whether or not to pretty-print the JSON text.
     *
     * @return The specified {@link JsonValue} converted to a JSON string.
     */
    public static String toJsonText(JsonValue jsonValue, boolean prettyPrint) {
        return toJsonText(new StringWriter(), jsonValue, prettyPrint).toString();
    }

    /**
     * Using the specified character encoding, this method will wraps the specified
     * {@link Reader} in a new {@link Reader} that will skip the "byte order mark"
     * (BOM) character at the beginning of the file for UTF character encodings
     * (e.g.: "UTF-8", "UTF-16" or "UTF-32"). If the specified character encoding is
     * not a "UTF" character encoding then it is simply returned as-is.
     * 
     * @param src      The source {@link Reader}.
     * @param encoding The character encoding.
     * @return The new {@link Reader} that will skip the byte-order mark.
     * @throws IOException          If an I/O failure occurs.
     * @throws NullPointerException If either parameter is <code>null</code>.
     */
    public static Reader bomSkippingReader(Reader src, String encoding) throws IOException, NullPointerException {
        // check if encoding is null (illegal)
        if (encoding == null) {
            throw new NullPointerException("Cannot skip byte order mark without specifying the encoding.");
        }

        // check if we have an encoding that is NOT a UTF encoding
        if (!encoding.toUpperCase().startsWith("UTF")) {
            // if not UTF encoding then there should not be a BOM to skip
            return src;
        }

        // create a pushback reader and peek at the first character
        PushbackReader result = new PushbackReader(src, 1);
        int first = result.read();

        // check if already at EOF
        if (first == -1) {
            // just return the source stream
            return src;
        }

        // check if we do NOT have a byte order mark
        if (first != 0xFEFF) {
            // push the character back on to the stream so it can be read
            result.unread(first);
        }

        // return the pushback reader
        return result;
    }

    /**
     * Reads the contents of the file as text and returns the {@link String}
     * representing the contents. The text is expected to be encoded in the
     * specified character encoding. If the specified character encoding is
     * <code>null</code> then the system default encoding is used.
     *
     * @param file         The {@link File} whose contents should be read.
     * @param charEncoding The character encoding for the text in the file.
     * @return The {@link String} representing the contents of the file.
     * @throws IOException If an I/O failure occurs.
     */
    public static String readTextFileAsString(File file, String charEncoding) throws IOException {
        Charset charset = (charEncoding == null) 
            ? Charset.defaultCharset() : Charset.forName(charEncoding);

        try (FileInputStream fis = new FileInputStream(file);
                InputStreamReader isr = new InputStreamReader(fis, charset);
                Reader reader = bomSkippingReader(isr, charset.name());
                BufferedReader br = new BufferedReader(reader)) {
            long size = file.length();
            if (size > Integer.MAX_VALUE)
                size = Integer.MAX_VALUE;

            StringBuilder sb = new StringBuilder((int) size);
            for (int nextChar = br.read(); nextChar >= 0; nextChar = br.read()) {
                if (nextChar == 0)
                    continue;
                sb.append((char) nextChar);
            }
            return sb.toString();
        }
    }

}
