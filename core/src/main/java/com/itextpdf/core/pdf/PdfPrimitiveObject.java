package com.itextpdf.core.pdf;

abstract class PdfPrimitiveObject extends PdfObject {

    protected byte[] content = null;

    public PdfPrimitiveObject() {
        super();
    }

    public PdfPrimitiveObject(byte[] content) {
        this();
        this.content = content;
    }

    protected boolean hasContent() {
        return content != null;
    }

    protected abstract void generateContent();

    final public byte[] getContent() {
        if (content == null)
            generateContent();
        return content;
    }
}