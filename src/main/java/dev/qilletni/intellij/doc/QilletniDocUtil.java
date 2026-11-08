package dev.qilletni.intellij.doc;

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.psi.*;

import java.util.ArrayList;
import java.util.List;

import static dev.qilletni.intellij.QilletniSyntaxHighlighter.NATIVE_TYPES_LIST;

/**
  * Utilities for extracting and rendering Qilletni DOC_COMMENTs.
  * Normal approach: walk to the preceding leaf before a declaration and pick a DOC_COMMENT token if present.
  */
public final class QilletniDocUtil {
    private QilletniDocUtil() {}

    /**
     * Render documentation HTML for a given PSI element, if a DOC_COMMENT applies.
     */
    public static String renderHtmlDocFor(PsiElement element) {
        if (element == null) return null;

        // Map leaf/name elements to their declaration containers
        PsiElement container = findDocContainer(element);
        if (container == null) return null;

        // Find DOC_COMMENT immediately preceding the declaration container
        PsiElement docToken = findPrecedingDocComment(container);
        if (docToken == null) return null;

        String raw = docToken.getText();
        String md = stripCommentDelimiters(raw);

        // Parse according to our docs grammar and render structured HTML
        DocComment doc = parseDoc(md);
        return renderDocToHtml(doc, container);
    }

    private static PsiElement findDocContainer(PsiElement element) {
        // If already a container node, return it
        if (element instanceof QilletniFunctionDef
                || element instanceof QilletniEntityDef
                || element instanceof QilletniEntityPropertyDeclaration
                || element instanceof QilletniEntityConstructor) {
            return element;
        }
        // Map names to their containers
        if (element instanceof QilletniFunctionName) {
            return PsiTreeUtil.getParentOfType(element, QilletniFunctionDef.class);
        }
        if (element instanceof QilletniEntityName) {
            return PsiTreeUtil.getParentOfType(element, QilletniEntityDef.class);
        }
        if (element instanceof QilletniPropertyName) {
            return PsiTreeUtil.getParentOfType(element, QilletniEntityPropertyDeclaration.class);
        }
        if (element instanceof QilletniConstructorName) {
            return PsiTreeUtil.getParentOfType(element, QilletniEntityConstructor.class);
        }

        // For other elements (e.g., reference targets), try to normalize to a name element first
        QilletniFunctionName fn = PsiTreeUtil.getParentOfType(element, QilletniFunctionName.class);
        if (fn != null) return PsiTreeUtil.getParentOfType(fn, QilletniFunctionDef.class);
        QilletniEntityName en = PsiTreeUtil.getParentOfType(element, QilletniEntityName.class);
        if (en != null) return PsiTreeUtil.getParentOfType(en, QilletniEntityDef.class);
        QilletniPropertyName pn = PsiTreeUtil.getParentOfType(element, QilletniPropertyName.class);
        if (pn != null) return PsiTreeUtil.getParentOfType(pn, QilletniEntityPropertyDeclaration.class);
        QilletniConstructorName cn = PsiTreeUtil.getParentOfType(element, QilletniConstructorName.class);
        if (cn != null) return PsiTreeUtil.getParentOfType(cn, QilletniEntityConstructor.class);
        return null;
    }

    /**
     * Find a DOC_COMMENT leaf immediately preceding the declaration container.
     * Walks backward from the container's first leaf, skipping whitespace and non-doc comments until a boundary.
     */
    private static PsiElement findPrecedingDocComment(PsiElement container) {
        PsiElement anchor = PsiTreeUtil.getDeepestFirst(container);
        PsiElement prev = PsiTreeUtil.prevLeaf(anchor, true);
        while (prev != null) {
            if (prev.getNode() == null) break;
            var type = prev.getNode().getElementType();
            // Skip whitespace and non-doc comments while we are between the doc and the declaration
            if (type == TokenType.WHITE_SPACE
                    || type == QilletniTypes.LINE_COMMENT
                    || type == QilletniTypes.BLOCK_COMMENT) {
                prev = PsiTreeUtil.prevLeaf(prev, true);
                continue;
            }
            // Accept only DOC_COMMENT as documentation
            if (type == QilletniTypes.DOC_COMMENT) {
                return prev;
            }
            // Any other token means no leading doc comment
            break;
        }
        return null;
    }

    private static String stripCommentDelimiters(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        // /** ... */ style
        if (s.startsWith("/**") && s.endsWith("*/")) {
            s = s.substring(3, s.length() - 2);
            // remove leading * on lines
            s = s.replaceAll("(?m)^\\s*\\*\\s?", "");
            return s.trim();
        }
        // /// line doc style
        if (s.startsWith("///")) {
            s = s.replaceAll("(?m)^\\s*///\\s?", "");
            return s.trim();
        }
        // Fallback: remove /* */ if present
        if (s.startsWith("/*") && s.endsWith("*/")) {
            s = s.substring(2, s.length() - 2);
            return s.trim();
        }
        return s;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * If the HTML is a single <p>...</p> block, strip that wrapper to allow inline composition.
     * Otherwise return the input as-is.
     */
    private static String stripOuterParagraph(String html) {
        if (html == null) return "";
        String s = html.trim();
        if (s.startsWith("<p>") && s.endsWith("</p>")) {
            return s.substring(3, s.length() - 4).trim();
        }
        return s;
    }

    // ===== Grammar-compatible parsing and rendering =====

    private enum Section {
        DESCRIPTION, PARAM, RETURNS, TYPE, ON, ERRORS
    }

    private enum MetaKind {
        PARAM, JAVA, TYPE, UNKNOWN
    }

    private static final class InlineMeta {
        final MetaKind kind;
        final boolean isJava; // true if an explicit @java marker applies
        final String text;    // free-form text after markers

        InlineMeta(MetaKind kind, boolean isJava, String text) {
            this.kind = kind;
            this.isJava = isJava;
            this.text = text;
        }
    }

    private static final class TypeInfo {
        final String text;
        final boolean isJava;

        TypeInfo(String text, boolean isJava) {
            this.text = text;
            this.isJava = isJava;
        }
    }

    private static final class ParamDoc {
        final String name;
        final StringBuilder description = new StringBuilder();
        InlineMeta meta; // may be null

        ParamDoc(String name) {
            this.name = name;
        }
    }

    private static final class DocComment {
        final StringBuilder description = new StringBuilder();
        final List<ParamDoc> params = new ArrayList<>();
        final StringBuilder returns = new StringBuilder();
        TypeInfo type;  // from @type
        InlineMeta returnsMeta; // from inline_brackets on @returns
        final StringBuilder on = new StringBuilder();
        InlineMeta onMeta; // from inline_brackets on @on
        final StringBuilder errors = new StringBuilder();
    }

    private static String ltrim(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return (i == 0) ? s : s.substring(i);
    }

    private static final class BracketParse {
        final InlineMeta meta;
        final int consumed; // number of characters consumed from input, including closing bracket

        BracketParse(InlineMeta meta, int consumed) {
            this.meta = meta;
            this.consumed = consumed;
        }
    }

    /**
     * Parse inline_brackets as defined by the grammar:
     * inline_brackets: '[' (PARAM | JAVA | TYPE) isJava=JAVA? BRACKETS_TEXT ']';
     * Returns null if the input doesn't start with '['.
     */
    private static BracketParse parseInlineBrackets(String s) {
        String input = ltrim(s);
        if (input.isEmpty() || input.charAt(0) != '[') return null;

        int close = input.indexOf(']');
        if (close <= 1) return null; // malformed, ignore
        String inside = input.substring(1, close).trim();

        // Tokenize inside: first tag, optional @java, then the rest
        // We keep it permissive to stay compatible with lexer mode.
        String tag = null;
        boolean isJava = false;
        String rest = "";

        int firstSpace = inside.indexOf(' ');
        String firstToken = firstSpace < 0 ? inside : inside.substring(0, firstSpace);
        String remainder = firstSpace < 0 ? "" : ltrim(inside.substring(firstSpace + 1));

        if (firstToken.startsWith("@")) {
            tag = firstToken.substring(1).toLowerCase();
        } else {
            tag = ""; // unknown
            remainder = inside; // treat everything as rest
        }

        if (remainder.startsWith("@java")) {
            isJava = true;
            remainder = ltrim(remainder.substring("@java".length()));
        }

        rest = remainder;

        MetaKind kind;
        switch (tag) {
            case "param": kind = MetaKind.PARAM; break;
            case "java":  kind = MetaKind.JAVA;  break;
            case "type":  kind = MetaKind.TYPE;  break;
            default:      kind = MetaKind.UNKNOWN;
        }

        InlineMeta meta = new InlineMeta(kind, isJava, rest);
        return new BracketParse(meta, close + 1 /* include ']' */ + (input.length() < s.length() ? (s.length() - input.length()) : 0));
    }

    /**
     * Parse the stripped doc text into a structured DocComment according to the provided grammar.
     * The grammar enforces ordering: description? params* returns? type? on? errors?
     * This parser is tolerant to whitespace and lets sections span multiple lines until the next tag.
     */
    private static DocComment parseDoc(String src) {
        DocComment doc = new DocComment();
        if (src == null || src.isEmpty()) return doc;

        Section current = Section.DESCRIPTION;
        ParamDoc currentParam = null;
        StringBuilder currentBuffer = doc.description; // default to description
        String[] lines = src.split("\\R", -1); // keep empty lines

        for (String rawLine : lines) {
            String trimmed = ltrim(rawLine);

            if (trimmed.startsWith("@param")) {
                // Flush and switch to PARAM
                current = Section.PARAM;
                currentParam = parseParamLine(trimmed.substring("@param".length()));
                doc.params.add(currentParam);
                currentBuffer = currentParam.description;
                continue;
            }
            if (trimmed.startsWith("@returns")) {
                current = Section.RETURNS;
                currentParam = null;
                // Parse optional inline brackets + description
                String rest = ltrim(trimmed.substring("@returns".length()));
                BracketParse bp = null;
                if (rest.startsWith("[")) {
                    bp = parseInlineBrackets(rest);
                    if (bp != null) {
                        doc.returnsMeta = bp.meta;
                        rest = ltrim(rest.substring(bp.consumed));
                    }
                }
                if (!rest.isEmpty()) {
                    doc.returns.append(rest);
                }
                currentBuffer = doc.returns;
                continue;
            }
            if (trimmed.startsWith("@type")) {
                current = Section.TYPE;
                currentParam = null;
                String rest = ltrim(trimmed.substring("@type".length()));
                boolean isJava = false;
                if (rest.startsWith("@java")) {
                    isJava = true;
                    rest = ltrim(rest.substring("@java".length()));
                }
                doc.type = new TypeInfo(rest, isJava);
                // @type is usually a single logical line; still allow continuations
                currentBuffer = null;
                continue;
            }
            if (trimmed.startsWith("@on")) {
                current = Section.ON;
                currentParam = null;
                String rest = ltrim(trimmed.substring("@on".length()));
                BracketParse bp = null;
                if (rest.startsWith("[")) {
                    bp = parseInlineBrackets(rest);
                    if (bp != null) {
                        doc.onMeta = bp.meta;
                        rest = ltrim(rest.substring(bp.consumed));
                    }
                }
                if (!rest.isEmpty()) {
                    doc.on.append(rest);
                }
                currentBuffer = doc.on;
                continue;
            }
            if (trimmed.startsWith("@errors")) {
                current = Section.ERRORS;
                currentParam = null;
                String rest = ltrim(trimmed.substring("@errors".length()));
                if (!rest.isEmpty()) {
                    doc.errors.append(rest);
                }
                currentBuffer = doc.errors;
                continue;
            }

            // Non-tag lines: continuation of the current section
            if (current == Section.DESCRIPTION) {
                doc.description.append(rawLine).append('\n');
            } else if (current == Section.PARAM) {
                currentParam.description.append(rawLine).append('\n');
            } else if (current == Section.RETURNS) {
                if (!doc.returns.isEmpty()) doc.returns.append('\n');
                doc.returns.append(rawLine);
            } else if (current == Section.ON) {
                if (!doc.on.isEmpty()) doc.on.append('\n');
                doc.on.append(rawLine);
            } else if (current == Section.ERRORS) {
                if (!doc.errors.isEmpty()) doc.errors.append('\n');
                doc.errors.append(rawLine);
            } else {
                // For @type we don't strictly collect continuations; handle conservatively if any
                if (currentBuffer != null) {
                    if (currentBuffer.length() > 0) currentBuffer.append('\n');
                    currentBuffer.append(rawLine);
                }
            }
        }

        // Trim trailing whitespace in buffers
        trimBuilder(doc.description);
        trimBuilder(doc.returns);
        trimBuilder(doc.on);
        trimBuilder(doc.errors);
        for (ParamDoc p : doc.params) trimBuilder(p.description);

        return doc;
    }

    private static void trimBuilder(StringBuilder sb) {
        if (sb == null || sb.isEmpty()) return;
        int start = 0;
        int end = sb.length() - 1;
        while (start <= end && Character.isWhitespace(sb.charAt(start))) start++;
        while (end >= start && Character.isWhitespace(sb.charAt(end))) end--;
        if (start == 0 && end == sb.length() - 1) return;
        String mid = (start <= end) ? sb.substring(start, end + 1) : "";
        sb.setLength(0);
        sb.append(mid);
    }

    private static ParamDoc parseParamLine(String rest) {
        String s = ltrim(rest);
        InlineMeta meta = null;

        if (s.startsWith("[")) {
            BracketParse bp = parseInlineBrackets(s);
            if (bp != null) {
                meta = bp.meta;
                s = ltrim(s.substring(bp.consumed));
            }
        }

        // PARAM_NAME: any run of non-space and not '[' or '\' per grammar
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '[' || Character.isWhitespace(c) || c == '\\' || c == '\r' || c == '\n') break;
            i++;
        }
        String name = s.substring(0, i);
        String desc = ltrim(s.substring(i));

        ParamDoc p = new ParamDoc(name);
        p.meta = meta;
        if (!desc.isEmpty()) p.description.append(desc);
        return p;
    }

    private static String renderDocToHtml(DocComment doc, PsiElement container) {
        HtmlBuilder b = new HtmlBuilder();

        // Definition (top signature/title)
        b.appendRaw(DocumentationMarkup.DEFINITION_START)
         .appendRaw(buildDefinitionHtml(container))
         .appendRaw(DocumentationMarkup.DEFINITION_END);

        // Main content (top description)
        b.appendRaw(DocumentationMarkup.CONTENT_START);
        if (!doc.description.isEmpty()) {
            b.appendRaw(renderMarkdownWithInlineMeta(doc.description.toString()));
        }
        b.appendRaw(DocumentationMarkup.CONTENT_END);

        // Sections
        boolean hasParams = !doc.params.isEmpty();
        boolean hasReturns = (!doc.returns.isEmpty()) || (doc.returnsMeta != null);
        boolean hasType = (doc.type != null && doc.type.text != null && !doc.type.text.isEmpty());
        boolean hasOn = (!doc.on.isEmpty()) || (doc.onMeta != null);
        boolean hasErrors = (!doc.errors.isEmpty());

        if (hasParams || hasReturns || hasType || hasOn || hasErrors) {
            b.appendRaw(DocumentationMarkup.SECTIONS_START);

            // Parameters (single right column; each entry inline)
            if (hasParams) {
                b.appendRaw(DocumentationMarkup.SECTION_HEADER_START)
                 .append(HtmlChunk.text("Parameters"))
                 .appendRaw(DocumentationMarkup.SECTION_SEPARATOR);

                StringBuilder content = new StringBuilder();
                for (ParamDoc p : doc.params) {
                    StringBuilder line = new StringBuilder();
                    // name and optional type
                    line.append("<code>").append(escapeHtml(p.name)).append("</code>");
                    String typeLabel = (p.meta != null) ? buildTypeLabelFromMeta(p.meta) : null;
                    if (typeLabel != null && !typeLabel.isEmpty()) {
                        line.append(" : ").append(typeLabel);
                    }
                    // description
                    if (!p.description.isEmpty()) {
                        String descHtml = renderMarkdownWithInlineMeta(p.description.toString());
                        line.append("&nbsp;&ndash;&nbsp;").append(stripOuterParagraph(descHtml));
                    }
                    content.append("<div>").append(line).append("</div>");
                }
                b.appendRaw(content.toString())
                 .appendRaw(DocumentationMarkup.SECTION_END);
            }

            // Returns (type + description inline)
            if (hasReturns) {
                b.appendRaw(DocumentationMarkup.SECTION_HEADER_START)
                 .append(HtmlChunk.text("Returns"))
                 .appendRaw(DocumentationMarkup.SECTION_SEPARATOR);

                StringBuilder content = new StringBuilder();
                StringBuilder line = new StringBuilder();
                if (doc.returnsMeta != null) {
                    String typeLabel = buildTypeLabelFromMeta(doc.returnsMeta);
                    if (typeLabel != null && !typeLabel.isEmpty()) {
                        line.append(typeLabel);
                    }
                }
                if (!doc.returns.isEmpty()) {
                    String descHtml = renderMarkdownWithInlineMeta(doc.returns.toString());
                    if (!line.isEmpty()) line.append("&nbsp;&ndash;&nbsp;");
                    line.append(stripOuterParagraph(descHtml));
                }
                if (!line.isEmpty()) {
                    content.append("<div>").append(line).append("</div>");
                }
                b.appendRaw(content.toString())
                 .appendRaw(DocumentationMarkup.SECTION_END);
            }

            // Type
            if (hasType) {
                b.appendRaw(DocumentationMarkup.SECTION_HEADER_START)
                 .append(HtmlChunk.text("Type"))
                 .appendRaw(DocumentationMarkup.SECTION_SEPARATOR);

                b.appendRaw("<div>")
                 .appendRaw(renderTypeAsHtml(doc.type.text, doc.type.isJava))
                 .appendRaw("</div>")
                 .appendRaw(DocumentationMarkup.SECTION_END);
            }

            // On
            if (hasOn) {
                b.appendRaw(DocumentationMarkup.SECTION_HEADER_START)
                 .append(HtmlChunk.text("On"))
                 .appendRaw(DocumentationMarkup.SECTION_SEPARATOR);

                StringBuilder content = new StringBuilder();
                StringBuilder line = new StringBuilder();
                if (doc.onMeta != null) {
                    String typeLabel = buildTypeLabelFromMeta(doc.onMeta);
                    if (typeLabel != null && !typeLabel.isEmpty()) {
                        line.append(typeLabel);
                    }
                }
                if (!doc.on.isEmpty()) {
                    String onHtml = renderMarkdownWithInlineMeta(doc.on.toString());
                    if (!line.isEmpty()) line.append("&nbsp;&ndash;&nbsp;");
                    line.append(stripOuterParagraph(onHtml));
                }
                if (!line.isEmpty()) {
                    content.append("<div>").append(line).append("</div>");
                }
                b.appendRaw(content.toString())
                 .appendRaw(DocumentationMarkup.SECTION_END);
            }

            // Errors
            if (hasErrors) {
                b.appendRaw(DocumentationMarkup.SECTION_HEADER_START)
                 .append(HtmlChunk.text("Errors"))
                 .appendRaw(DocumentationMarkup.SECTION_SEPARATOR);

                String html = renderMarkdownWithInlineMeta(doc.errors.toString());
                b.appendRaw("<div>").appendRaw(stripOuterParagraph(html)).appendRaw("</div>")
                 .appendRaw(DocumentationMarkup.SECTION_END);
            }

            b.appendRaw(DocumentationMarkup.SECTIONS_END);
        }

        return b.toString();
    }

    private static String buildTypeLabelFromMeta(InlineMeta meta) {
        if (meta == null) return null;
        return switch (meta.kind) {
            case TYPE -> renderTypeAsHtml(meta.text, meta.isJava);
            case JAVA ->
                // Brackets beginning with @java imply the rest is a Java type
                    renderTypeAsHtml(meta.text, true);
            case PARAM -> {
                // Treat [@param name] as a reference to a parameter name.
                if (meta.text != null && !meta.text.isEmpty()) {
                    yield "<code>" + escapeHtml(meta.text) + "</code>";
                }
                yield null;
            }
            default -> {
                // Unknown bracket tag; expose as plain code to avoid data loss.
                if (meta.text != null && !meta.text.isEmpty()) {
                    yield "<code>" + escapeHtml(meta.text) + "</code>";
                }
                yield null;
            }
        };
    }

    // ===== Inline bracket processing for prose =====

    /**
     * Convert inline bracket forms into Markdown-safe constructs so the Markdown renderer can produce final HTML.
     * Supported:
     *  - [@java T] or [@type @java T]  -> [`T`](javadoc-url)
     *  - [@type T]                     -> [`T`](psi_element://type:T) if linkable, else `T`
     *  - [@param name]                 -> `name`
     */
    private static String transformInlineBracketsToMarkdown(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '[') {
                int close = text.indexOf(']', i + 1);
                if (close > i + 1) {
                    String inside = text.substring(i + 1, close).trim();
                    InlineMeta meta = parseInlineMetaInside(inside);
                    if (meta != null) {
                        String md = inlineMetaToMarkdown(meta);
                        out.append(md);
                        i = close + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * Parse the content inside brackets and return InlineMeta if it matches the grammar, otherwise null.
     */
    private static InlineMeta parseInlineMetaInside(String inside) {
        if (inside == null || inside.isEmpty()) return null;
        String trimmed = inside.trim();
        int sp = trimmed.indexOf(' ');
        String first = (sp < 0) ? trimmed : trimmed.substring(0, sp);
        String rest = (sp < 0) ? "" : ltrim(trimmed.substring(sp + 1));
        if (!first.startsWith("@")) return null;
        String tag = first.substring(1).toLowerCase();
        boolean isJava = false;
        if (rest.startsWith("@java")) {
            isJava = true;
            rest = ltrim(rest.substring("@java".length()));
        }
        MetaKind kind = switch (tag) {
            case "param" -> MetaKind.PARAM;
            case "java" -> { isJava = true; yield MetaKind.JAVA; }
            case "type" -> MetaKind.TYPE;
            default -> MetaKind.UNKNOWN;
        };
        return new InlineMeta(kind, isJava, rest);
    }

    /**
     * Convert InlineMeta to Markdown.
     */
    private static String inlineMetaToMarkdown(InlineMeta meta) {
        if (meta == null || meta.text == null || meta.text.isEmpty()) return "";
        String label = meta.text;
        switch (meta.kind) {
            case JAVA:
                // explicit java
                String url = MarkdownParser.buildJavaDocUrl(label);
                return url != null && !url.isEmpty() ? "[`" + label + "`](" + url + ")" : "`" + label + "`";
            case TYPE:
                if (meta.isJava) {
                    String jurl = MarkdownParser.buildJavaDocUrl(label);
                    return jurl != null && !jurl.isEmpty() ? "[`" + label + "`](" + jurl + ")" : "`" + label + "`";
                } else {
                    String href = buildPsiHrefForType(label);
                    return href != null ? "[`" + label + "`](" + href + ")" : "`" + label + "`";
                }
            case PARAM:
                return "`" + label + "`";
            case UNKNOWN:
            default:
                return "`" + label + "`";
        }
    }

    /**
     * Render Markdown after converting inline bracket constructs into Markdown-friendly forms.
     */
    private static String renderMarkdownWithInlineMeta(String textWithInline) {
        String md = transformInlineBracketsToMarkdown(textWithInline);
        return MarkdownParser.getInstance().renderMarkdown(md);
    }

    // === Colorization helpers ===

    private static void appendColored(HtmlBuilder hb, TextAttributesKey key, String text) {
        StringBuilder sb = new StringBuilder();
        HtmlSyntaxInfoUtil.appendStyledSpan(sb, key, text, 1.0f);
        hb.appendRaw(sb.toString());
    }

    private static String coloredSpan(TextAttributesKey key, String text) {
        StringBuilder sb = new StringBuilder();
        HtmlSyntaxInfoUtil.appendStyledSpan(sb, key, text, 1.0f);
        return sb.toString();
    }

    // === PSI link helpers ===

    /**
     * Build a PSI link target for the supplied element. The href is resolved by your
     * DocumentationProvider via the protocol.
     * TODO: Replace with a stable ID (e.g., qualified name) and implement resolution.
     */
    private static String buildPsiHrefForElement(PsiElement element) {
        String kind = switch (element) {
            case QilletniFunctionDef qilletniFunctionDef -> "function";
            case QilletniEntityDef qilletniEntityDef -> "entity";
            case QilletniEntityConstructor qilletniEntityConstructor -> "constructor";
            case QilletniEntityPropertyDeclaration qilletniEntityPropertyDeclaration -> "property";
            case null, default -> "symbol";
        };
        String name = safeName(element);
        return DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + kind + ":" + name;
    }

    /**
     * Build a PSI link target for a language type name.
     * TODO: Resolve the type to a PSI element and use a stable ID here.
     */
    private static String buildPsiHrefForType(String typeName) {
        if (typeName == null || typeName.isBlank()) return null;
        return DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + "type:" + typeName.trim();
    }

    private static String renderTypeAsHtml(String typeText, boolean isJava) {
        String safe = typeText == null ? "" : typeText.trim();
        if (safe.isEmpty()) return "";

        if (isJava) {
            String url = MarkdownParser.buildJavaDocUrl(safe);
            if (url != null && !url.isEmpty()) {
                HtmlChunk.Element a = HtmlChunk.link(url, HtmlChunk.text(safe));
                return HtmlChunk.tag("code").child(a).toString();
            }
        } else {
            // Build an internal PSI link for language types (to be resolved by your DocumentationProvider)
            String href = buildPsiHrefForType(safe); // TODO: implement proper resolution
            if (href != null) {
                HtmlChunk.Element a = HtmlChunk.link(href, HtmlChunk.text(safe));
                return HtmlChunk.tag("code").child(a).toString();
            }
        }
        // Fallback
        return HtmlChunk.tag("code").addText(safe).toString();
    }

    /**
     * Build the definition (title/signature) block for the documentation popup.
     * Colors are taken from the current editor scheme; names are wrapped with PSI links.
     */
    private static String buildDefinitionHtml(PsiElement container) {
        HtmlBuilder hb = new HtmlBuilder();

        switch (container) {
            case QilletniFunctionDef functionDef -> {
                if (functionDef.getNative() != null) {
                    appendColored(hb, DefaultLanguageHighlighterColors.KEYWORD, "native ");
                }

                if (functionDef.getStatic() != null) {
                    appendColored(hb, DefaultLanguageHighlighterColors.KEYWORD, "static ");
                }

                appendColored(hb, DefaultLanguageHighlighterColors.KEYWORD, "fun");
                hb.append(HtmlChunk.nbsp());

                String coloredName = coloredSpan(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION, functionDef.getFunctionName().getName());
                hb.append(HtmlChunk.link(buildPsiHrefForElement(functionDef), HtmlChunk.raw(coloredName)));

                hb.append(HtmlChunk.text("("));
                List<String> params = getFunctionParameterDisplay(functionDef);
                for (int i = 0; i < params.size(); i++) {
                    if (i > 0) hb.append(HtmlChunk.text(", "));
                    String p = params.get(i) == null ? "" : params.get(i);
                    hb.append(HtmlChunk.raw(coloredSpan(DefaultLanguageHighlighterColors.PARAMETER, p)));
                }
                hb.append(HtmlChunk.text(")"));
            }
            case QilletniEntityConstructor entityConstructor -> {
                String coloredName = coloredSpan(DefaultLanguageHighlighterColors.FUNCTION_DECLARATION, entityConstructor.getConstructorName().getName());
                hb.append(HtmlChunk.link(buildPsiHrefForElement(entityConstructor), HtmlChunk.raw(coloredName)));

                hb.append(HtmlChunk.text("("));
                List<String> params = getConstructorParameterDisplay(entityConstructor);
                for (int i = 0; i < params.size(); i++) {
                    if (i > 0) hb.append(HtmlChunk.text(", "));
                    String p = params.get(i) == null ? "" : params.get(i);
                    hb.append(HtmlChunk.raw(coloredSpan(DefaultLanguageHighlighterColors.PARAMETER, p)));
                }
                hb.append(HtmlChunk.text(")"));
            }
            case QilletniEntityPropertyDeclaration prop -> {

                var typeChild = prop.getFirstChild();
                if (NATIVE_TYPES_LIST.contains(typeChild.getNode().getElementType())) {
                    String coloredName = coloredSpan(DefaultLanguageHighlighterColors.KEYWORD, typeChild.getText());
                    hb.append(HtmlChunk.link(buildPsiHrefForElement(typeChild), HtmlChunk.raw(coloredName)));
                } else {
                    hb.append(HtmlChunk.link(buildPsiHrefForElement(typeChild), typeChild.getText()));
                }

                if (prop.getExpr() != null) {
                    hb.append(HtmlChunk.text(" %s = ".formatted(prop.getChildren()[0].getText())));
                    hb.append(prop.getExpr().getText());
                } else {
                    hb.append(HtmlChunk.text(" %s =".formatted(prop.getChildren()[0].getText())));
                }
            }
            case QilletniEntityDef entityDef -> {
                // entity <name>
                appendColored(hb, DefaultLanguageHighlighterColors.KEYWORD, "entity");
                hb.append(HtmlChunk.nbsp());

                String coloredName = coloredSpan(DefaultLanguageHighlighterColors.CLASS_NAME, entityDef.getEntityName().getName());
                hb.append(HtmlChunk.link(buildPsiHrefForElement(entityDef), HtmlChunk.raw(coloredName)));
            }
            case null, default -> {
                // Fallback
                String name = safeName(container);
                hb.append(HtmlChunk.raw(coloredSpan(DefaultLanguageHighlighterColors.IDENTIFIER, name)));
            }
        }

        return hb.toString();
    }

    private static String safeName(PsiElement element) {
        if (element instanceof PsiNamedElement) {
            String n = ((PsiNamedElement) element).getName();
            if (n != null) return n;
        }
        return "";
    }

    /**
     * Return an empty list if parameters are not available.
     */
    private static List<String> getFunctionParameterDisplay(QilletniFunctionDef functionDef) {
        return functionDef.getFunctionDefParams().getParamNameList().stream().map(QilletniParamName::getName).toList();
    }

    private static List<String> getConstructorParameterDisplay(QilletniEntityConstructor entityConstructor) {
        return entityConstructor.getFunctionDefParams().getParamNameList().stream().map(QilletniParamName::getName).toList();
    }
}
