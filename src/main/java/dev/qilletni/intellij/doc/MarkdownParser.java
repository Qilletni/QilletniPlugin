package dev.qilletni.intellij.doc;

import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.HtmlWriter;
import com.vladsch.flexmark.html.renderer.NodeRenderer;
import com.vladsch.flexmark.html.renderer.NodeRendererContext;
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.util.Collections;
import java.util.Set;

public class MarkdownParser {

    public static final String JAVA_DOC_BASE_URL = "https://docs.oracle.com/en/java/javase/21/docs/api/";

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownParser(Parser parser, HtmlRenderer renderer) {
        this.parser = parser;
        this.renderer = renderer;
    }

    /**
     * Build a Java SE API URL for a fully-qualified Java type name.
     * This is a naive implementation and will not handle nested classes, arrays, or generics anchors.
     * TODO: Enhance to support nested classes (Inner), arrays, generics, and specific member anchors.
     */
    public static String buildJavaDocUrl(String fqn) {
        if (fqn == null || fqn.isBlank()) return null;
        String type = fqn.trim();

        // Strip generic parameters for URL path
        int genericStart = type.indexOf('<');
        if (genericStart >= 0) {
            type = type.substring(0, genericStart).trim();
        }

        // Replace '$' with '.' if inner classes are provided in binary name format
        type = type.replace('$', '.');

        // Basic path mapping: java.lang.String -> java/lang/String.html
        String path = type.replace('.', '/') + ".html";
        return JAVA_DOC_BASE_URL + path;
    }

    private static final class Holder {
        static final MarkdownParser INSTANCE = createMarkdownParser();
    }

    public static MarkdownParser getInstance() {
        return Holder.INSTANCE;
    }

    private static MarkdownParser createMarkdownParser() {
        var options = new MutableDataSet();

        options.set(Parser.HEADING_PARSER, false); // Disables headers like # Header
        options.set(HtmlRenderer.ESCAPE_HTML_BLOCKS, true);
        options.set(HtmlRenderer.ESCAPE_INLINE_HTML, true);
        options.set(HtmlRenderer.HARD_BREAK, "<br/>");
        options.set(HtmlRenderer.SOFT_BREAK, "<br/>");

        var parser = Parser.builder(options).build();
        var renderer = HtmlRenderer.builder(options)
                .nodeRendererFactory($ -> new TargetBlankLinkRenderer())
                .build();

        return new MarkdownParser(parser, renderer);
    }

    public String renderMarkdown(String markdown) {
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }

    static class TargetBlankLinkRenderer implements NodeRenderer {
        @Override
        public Set<NodeRenderingHandler<?>> getNodeRenderingHandlers() {
            return Collections.singleton(new NodeRenderingHandler<>(Link.class, this::renderLink));
        }

        private void renderLink(Link node, NodeRendererContext context, HtmlWriter html) {
            var url = node.getUrl().toString();

            // Add target="_blank" only for only javadoc links
            if (url.contains(JAVA_DOC_BASE_URL)) {
                html.attr("href", url);
                html.attr("target", "_blank"); // Add target="_blank"
                html.srcPos(node.getChars()).withAttr().tag("a");
                context.renderChildren(node); // Render the link text
                html.tag("/a");
            } else {
                // Default rendering for other links
                context.delegateRender();
            }
        }
    }

}
