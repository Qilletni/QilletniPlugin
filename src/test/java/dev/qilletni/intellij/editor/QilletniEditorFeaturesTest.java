package dev.qilletni.intellij.editor;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class QilletniEditorFeaturesTest extends BasePlatformTestCase {

    public void testNamesValidator() {
        var validator = new QilletniNamesValidator();
        assertTrue(validator.isIdentifier("myVar", getProject()));
        assertFalse(validator.isIdentifier("fun", getProject()));
        assertTrue(validator.isKeyword("entity", getProject()));
        assertFalse(validator.isIdentifier("two words", getProject()));
        assertFalse(validator.isIdentifier("", getProject()));
    }

    public void testLineCommentToggle() {
        myFixture.configureByText("a.ql", "<caret>print(\"hi\")\n");
        myFixture.performEditorAction("CommentByLineComment");
        myFixture.checkResult("//print(\"hi\")\n");
    }

    public void testFoldsFunctionBody() {
        var file = myFixture.configureByText("a.ql", "import \"a.ql\"\nimport \"b.ql\"\n\nfun f() {\n    print(1)\n}\n");
        var regions = new QilletniFoldingBuilder().buildFoldRegions(file, myFixture.getEditor().getDocument(), false);
        // The function body and the import block
        assertEquals(2, regions.length);
    }

    public void testStructureViewListsEntityMembers() {
        myFixture.configureByText("a.ql", "entity Box {\n    int size = 1\n    Box()\n    fun open() {\n    }\n}\nfun main() {\n}\n");
        myFixture.testStructureView(view -> {
            var root = view.getTreeModel().getRoot();
            var children = root.getChildren();
            assertEquals(2, children.length);
            var entity = (com.intellij.ide.util.treeView.smartTree.TreeElement) children[0];
            assertEquals(3, entity.getChildren().length);
        });
    }
}
