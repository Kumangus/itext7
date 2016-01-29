package com.itextpdf.kernel.pdf;

import com.itextpdf.kernel.PdfException;
import java.util.ArrayList;
import java.util.List;

/**
 * Algorithm for construction {@see PdfPages} tree
 */
class PdfPagesTree {
    private final int leafSize = 10;

    private List<PdfDictionary> pageRefs;
    private List<PdfPages> parents;
    private List<PdfPage> pages;
    private PdfDocument document;
    private boolean generated = false;
    private PdfPages root;

    /**
     * Create PdfPages tree.
     *
     * @param pdfCatalog {@see PdfCatalog}
     */
    public PdfPagesTree(PdfCatalog pdfCatalog) {
        this.document = pdfCatalog.getDocument();
        this.pageRefs = new ArrayList<>();
        this.parents = new ArrayList<>();
        this.pages = new ArrayList<>();
        if (pdfCatalog.getPdfObject().containsKey(PdfName.Pages)) {
            PdfDictionary pages = pdfCatalog.getPdfObject().getAsDictionary(PdfName.Pages);
            if (pages == null)
                throw new PdfException(PdfException.InvalidPageStructurePagesPagesMustBePdfDictionary);
            this.root = new PdfPages(0, Integer.MAX_VALUE, pages, null);
            parents.add(this.root);
            for (int i = 0; i < this.root.getCount(); i++) {
                this.pageRefs.add(null);
                this.pages.add(null);
            }
        } else {
            this.root = null;
            this.parents.add(new PdfPages(0, this.document));
        }
        //in read mode we will create PdfPages from 0 to Count
        // and reserve null indexes for pageRefs and pages.
    }

    /**
     * Returns the {@see PdfPage} at the specified position in this list.
     *
     * @param  pageNum one-based index of the element to return
     * @return the {@see PdfPage} at the specified position in this list
     */
    public PdfPage getPage(int pageNum) {
        --pageNum;
        PdfPage pdfPage = pages.get(pageNum);
        if (pdfPage == null) {
            loadPage(pageNum);
            pdfPage = new PdfPage(pageRefs.get(pageNum), document);
            int parentIndex = findPageParent(pageNum);
            PdfPages parentPages = parents.get(parentIndex);
            pdfPage.parentPages = parentPages;
            pages.set(pageNum, pdfPage);
        }
        return pdfPage;
    }

    /**
     * Returns the {@see PdfPage} by page's PdfDictionary.
     *
     * @param pageDictionary page's PdfDictionary
     * @return the {@code PdfPage} object, that wraps {@code pageDictionary}.
     */
    public PdfPage getPage(PdfDictionary pageDictionary) {
        int pageNum = getPageNumber(pageDictionary);
        if (pageNum > 0) {
            return getPage(pageNum);
        }

        return null;
    }

    /**
     * Gets total number of @see PdfPages.
     * @return total number of pages
     */
    public int getNumberOfPages() {
        return pageRefs.size();
    }

    /**
     * Returns the index of the first occurrence of the specified page
     * in this tree, or 0 if this tree does not contain the page.
     */
    public int getPageNumber(PdfPage page) {
        return pages.indexOf(page) + 1;
    }

    /**
     * Returns the index of the first occurrence of the page in this tree
     * specified by it's PdfDictionary, or 0 if this tree does not contain the page.
     */
    public int getPageNumber(PdfDictionary pageDictionary) {
        int pageNum = pageRefs.indexOf(pageDictionary);
        if (pageNum >= 0) {
            return pageNum + 1;
        }
        for (int i = 0; i < pageRefs.size(); i++) {
            if (pageRefs.get(i) == null) {
                loadPage(i);
            }
            if (pageRefs.get(i).equals(pageDictionary)) {
                return i + 1;
            }
        }

        return 0;
    }

    /**
     * Appends the specified {@see PdfPage} to the end of this tree.
     *
     * @param pdfPage {@see PdfPage}
     */
    public void addPage(PdfPage pdfPage) {
        PdfPages pdfPages;
        if (root != null) { // in this case we save tree structure
            if (pageRefs.isEmpty()) {
                pdfPages = root;
            } else {
                loadPage(pageRefs.size() - 1);
                pdfPages = parents.get(parents.size() - 1);
            }
        } else {
            pdfPages = parents.get(parents.size() - 1);
            if (pdfPages.getCount() % leafSize == 0 && !pageRefs.isEmpty()) {
                pdfPages = new PdfPages(pdfPages.getFrom() + pdfPages.getCount(), document);
                parents.add(pdfPages);
            }
        }


        pdfPage.makeIndirect(document);
        pdfPages.addPage(pdfPage.getPdfObject());
        pdfPage.parentPages = pdfPages;
        pageRefs.add(pdfPage.getPdfObject());
        pages.add(pdfPage);
    }

    /**
     * Insert {@see PdfPage} into specific one-based position.
     *
     * @param index one-base index of the page
     * @param pdfPage {@see PdfPage}
     */
    public void addPage(int index, PdfPage pdfPage) {
        --index;
        if (index > pageRefs.size())
            throw new IndexOutOfBoundsException("index");
        if (index == pageRefs.size()) {
            addPage(pdfPage);
            return;
        }
        loadPage(index);
        pdfPage.makeIndirect(document);
        int parentIndex = findPageParent(index);
        PdfPages parentPages = parents.get(parentIndex);
        parentPages.addPage(index, pdfPage);
        pdfPage.parentPages = parentPages;
        correctPdfPagesFromProperty(parentIndex + 1, +1);
        pageRefs.add(index, pdfPage.getPdfObject());
        pages.add(index, pdfPage);
    }

    /**
     * Removes the page at the specified position in this tree.
     * Shifts any subsequent elements to the left (subtracts one from their
     * indices).
     *
     * @param pageNum the one-based index of the PdfPage to be removed
     * @return the page that was removed from the list
     */
    public PdfPage removePage(int pageNum) {
        PdfPage pdfPage = getPage(pageNum);
        if (internalRemovePage(--pageNum)) {
            if (!pdfPage.getPdfObject().isFlushed()) {
                pdfPage.getPdfObject().remove(PdfName.Parent);
            }
            pdfPage.getPdfObject().getIndirectReference().setFree();
            return pdfPage;
        } else {
            return null;
        }
    }

    /**
     * Removes the first occurrence of the specified page from this list,
     * if it is present. Returns <tt>true</tt> if this list
     * contained the specified element (or equivalently, if this list
     * changed as a result of the call).
     *
     * @param pdfPage page to be removed from this list, if present
     * @return <tt>true</tt> if this list contained the specified page
     */
    public boolean removePage(PdfPage pdfPage) {
        int pageNum = getPageNumber(pdfPage) - 1;
        if (pageNum < 0)
            return false;
        if (!pdfPage.getPdfObject().isFlushed()) {
            pdfPage.getPdfObject().remove(PdfName.Parent);
        }
        pdfPage.getPdfObject().getIndirectReference().setFree();
        internalRemovePage(pageNum);
        return true;
    }

    /**
     * Generate PdfPages tree.
     *
     * @return root {@see PdfPages}
     * @throws PdfException in case empty document
     */
    protected  PdfObject generateTree() {
        if (pageRefs.isEmpty())
            throw new PdfException(PdfException.DocumentHasNoPages);
        if (generated)
            throw new PdfException(PdfException.PdfPagesTreeCouldBeGeneratedOnlyOnce);

        if (root == null) {
            while (parents.size() != 1) {
                List<PdfPages> nextParents = new ArrayList<>();
                //dynamicLeafSize helps to avoid PdfPages leaf with only one page
                int dynamicLeafSize = leafSize;
                PdfPages current = null;
                for (int i = 0; i < parents.size(); i++) {
                    PdfPages pages = parents.get(i);
                    int pageCount = pages.getCount();
                    if (i % dynamicLeafSize == 0) {
                        if (pageCount <= 1) {
                            dynamicLeafSize++;
                        } else {
                            current = new PdfPages(-1, document);
                            nextParents.add(current);
                            dynamicLeafSize = leafSize;
                        }
                    }
                    assert current != null;
                    current.addPages(pages);
                }
                parents = nextParents;
            }
            root = parents.get(0);
        }
        generated = true;
        return root.getPdfObject();
    }

    protected void clearPageRefs() {
        pageRefs = null;
        pages = null;
    }

    protected List<PdfPages> getParents() {
        return parents;
    }

    protected PdfPages getRoot() {
        return root;
    }

    protected PdfPages findPageParent(PdfPage pdfPage) {
        int pageNum = getPageNumber(pdfPage) - 1;
        int parentIndex = findPageParent(pageNum);
        return parents.get(parentIndex);
    }

    private void loadPage(int pageNum) {
        PdfDictionary targetPage = pageRefs.get(pageNum);
        if (targetPage != null)
            return;
        //if we go here, we have to split PdfPages that contains pageNum
        int parentIndex = findPageParent(pageNum);
        PdfPages parent = parents.get(parentIndex);
        PdfArray kids = parent.getKids();
        if (kids == null) {
            throw new PdfException(PdfException.InvalidPageStructure1).setMessageParams(pageNum+1);
        }
        int kidsCount = parent.getCount();
        // we should handle separated pages, it means every PdfArray kids must contain either PdfPage or PdfPages,
        // mix of PdfPage and PdfPages not allowed.
        boolean findPdfPages = false;
        // NOTE optimization? when we already found needed index
        for (int i = 0; i < kids.size(); i++) {
            PdfDictionary page = kids.getAsDictionary(i);
            if (page == null) {                                             // null values not allowed in pages tree.
                throw new PdfException(PdfException.InvalidPageStructure1).setMessageParams(pageNum+1);
            }
            PdfObject pageKids = page.get(PdfName.Kids);
            if (pageKids != null) {
                if (pageKids.getType() == PdfObject.Array) {
                    findPdfPages = true;
                } else {                                                    // kids must be of type array
                    throw new PdfException(PdfException.InvalidPageStructure1).setMessageParams(pageNum+1);
                }
            }
        }
        if (findPdfPages) {
            // handle mix of PdfPage and PdfPages.
            // handle count property!
            List<PdfPages> newParents = new ArrayList<>(kids.size());
            PdfPages lastPdfPages = null;
            for (int i = 0; i < kids.size() && kidsCount > 0; i++) {
                PdfDictionary pdfPagesObject = kids.getAsDictionary(i);
                if (pdfPagesObject.getAsArray(PdfName.Kids) == null) {      // pdfPagesObject is PdfPage
                    if (lastPdfPages == null) {                             // possible if only first kid is PdfPage
                        lastPdfPages = new PdfPages(parent.getFrom(), document, parent);
                        kids.set(i, lastPdfPages.getPdfObject());
                        newParents.add(lastPdfPages);
                    }
                    lastPdfPages.addPage(pdfPagesObject);
                    kids.remove(i);
                    i--;
                    kidsCount--;
                } else {                                                    // pdfPagesObject is PdfPages
                    int from = lastPdfPages == null
                            ? parent.getFrom()
                            : lastPdfPages.getFrom() + lastPdfPages.getCount();
                    lastPdfPages = new PdfPages(from, kidsCount, pdfPagesObject, parent);
                    newParents.add(lastPdfPages);
                    kidsCount -= lastPdfPages.getCount();
                }
            }
            parents.remove(parentIndex);
            for (int i = newParents.size() - 1; i >= 0; i--) {
                parents.add(parentIndex, newParents.get(i));
            }
            // recursive call, to load needed pageRef.
            // NOTE optimization? add to loadPage startParentIndex.
            loadPage(pageNum);
        } else {
            int from = parent.getFrom();
            // Possible exception in case kids.getSize() < parent.getCount().
            // In any case parent.getCount() has higher priority.
            // NOTE optimization? when we already found needed index
            for (int i = 0; i < parent.getCount(); i++) {
                pageRefs.set(from + i, kids.getAsDictionary(i));
            }
        }
    }

    // zero-based index
    private boolean internalRemovePage(int pageNum) {
        int parentIndex = findPageParent(pageNum);
        PdfPages pdfPages = parents.get(parentIndex);
        if (pdfPages.removePage(pageNum)) {
            if (pdfPages.getCount() == 0) {
                parents.remove(parentIndex);
                pdfPages.removeFromParent();
                --parentIndex;
            }
            if (parents.size() == 0) {
                root = null;
                parents.add(new PdfPages(0, document));
            } else {
                correctPdfPagesFromProperty(parentIndex + 1, -1);
            }
            pageRefs.remove(pageNum);
            pages.remove(pageNum);
            return true;
        } else {
            return false;
        }
    }

    // zero-based index
    private int findPageParent(int pageNum) {
        int low = 0;
        int high = parents.size() - 1;
        while (low != high) {
            int middle = (low + high + 1) / 2;
            if (parents.get(middle).compareTo(pageNum) > 0) {
                high = middle - 1;
            } else {
                low = middle;
            }
        }
        return low;
    }

    private void correctPdfPagesFromProperty(int index, int correction) {
        for (int i = index; i < parents.size(); i++) {
            if (parents.get(i) != null) {
                parents.get(i).correctFrom(correction);
            }
        }
    }
}