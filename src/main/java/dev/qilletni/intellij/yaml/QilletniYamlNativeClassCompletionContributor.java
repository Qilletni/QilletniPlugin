package dev.qilletni.intellij.yaml;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.util.Processor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLScalar;

import java.util.*;

public class QilletniYamlNativeClassCompletionContributor extends CompletionContributor {
    private static final int MAX_RESULTS = 200;
    private static final int MAX_PACKAGES = 120;

    public QilletniYamlNativeClassCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement()
                        .withLanguage(YAMLLanguage.INSTANCE)
                        .withParent(YAMLScalar.class),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        var position = parameters.getPosition();
                        var parent = position.getParent();
                        if (!(parent instanceof YAMLScalar scalar) || !YamlNativeClassesUtil.isInNativeClasses(scalar)) return;
                        var project = position.getProject();
                        if (DumbService.isDumb(project)) return;

                        // Compute caret-aware prefix within the scalar's value and strip IntelliJ dummy identifier tokens
                        var editor = parameters.getEditor();
                        var doc = editor.getDocument();
                        int caret = parameters.getOffset();
                        var elemRange = scalar.getTextRange();
                        if (caret < elemRange.getStartOffset()) return;
                        String rawPrefix = doc.getText(new TextRange(elemRange.getStartOffset(), Math.min(caret, elemRange.getEndOffset())));
                        // Remove dummy identifiers possibly injected into PSI/document during completion
                        rawPrefix = rawPrefix.replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "");
                        rawPrefix = rawPrefix.replace(CompletionUtilCore.DUMMY_IDENTIFIER, "");
                        // If quoted scalar, drop the first quote char
                        if (!rawPrefix.isEmpty()) {
                            char c0 = rawPrefix.charAt(0);
                            if (c0 == '\'' || c0 == '"') {
                                rawPrefix = rawPrefix.substring(1);
                            }
                        }
                        var prefix = rawPrefix.trim();
                        if (prefix.isEmpty()) {
                            return; // require at least 1 char at the very start
                        }

                        var scope = GlobalSearchScope.projectScope(project);
                        var pfi = ProjectFileIndex.getInstance(project);

                        int lastDot = prefix.lastIndexOf('.');
                        String basePkg = lastDot >= 0 ? prefix.substring(0, lastDot) : "";
                        String namePart = lastDot >= 0 ? prefix.substring(lastDot + 1) : prefix;

                        // Collect classes and packages separately, then emit classes first (higher priority)
                        LinkedHashSet<String> classFqns = new LinkedHashSet<>();
                        LinkedHashSet<String> packageFqns = new LinkedHashSet<>();

                        // Package suggestions
                        if (basePkg.isEmpty()) {
                            // Top-level packages whose simple name starts with the prefix
                            PsiPackage rootPkg = JavaPsiFacade.getInstance(project).findPackage("");
                            if (rootPkg != null) {
                                int count = 0;
                                for (PsiPackage sub : rootPkg.getSubPackages(scope)) {
                                    var n = sub.getName();
                                    if (n != null && n.startsWith(prefix) && hasProjectDirs(sub, pfi, scope)) {
                                        var qn = sub.getQualifiedName();
                                        packageFqns.add(qn);
                                        collapseLinearPackage(sub, pfi, scope).ifPresent(packageFqns::add);
                                        if (++count >= MAX_PACKAGES) break;
                                    }
                                }
                            }
                        } else {
                            PsiPackage base = JavaPsiFacade.getInstance(project).findPackage(basePkg);
                            if (base != null) {
                                for (PsiPackage sub : base.getSubPackages(scope)) {
                                    var n = sub.getName();
                                    if (n != null && n.startsWith(namePart) && hasProjectDirs(sub, pfi, scope)) {
                                        var qn = sub.getQualifiedName();
                                        packageFqns.add(qn);
                                        collapseLinearPackage(sub, pfi, scope).ifPresent(packageFqns::add);
                                    }
                                    if (packageFqns.size() >= MAX_PACKAGES) break;
                                }
                            }
                        }

                        // Class suggestions
                        if (basePkg.isEmpty()) {
                            // Global scan, filter by FQN prefix
                            collectProjectClassesByPrefix(project, scope, pfi, prefix, classFqns);
                        } else {
                            // Focused within base package
                            PsiPackage base = JavaPsiFacade.getInstance(project).findPackage(basePkg);
                            if (base != null) {
                                for (PsiClass cls : base.getClasses(scope)) {
                                    var name = cls.getName();
                                    if (name == null) continue;
                                    if (namePart.isEmpty() || name.startsWith(namePart)) {
                                        var qn = cls.getQualifiedName();
                                        if (qn != null && isInProjectContent(cls, pfi)) {
                                            classFqns.add(qn);
                                            if (classFqns.size() >= MAX_RESULTS) break;
                                        }
                                    }
                                }
                                // If no classes found and namePart not empty, we can still include global matches (fallback)
                                if (classFqns.isEmpty() && !namePart.isEmpty()) {
                                    collectProjectClassesByPrefix(project, scope, pfi, prefix, classFqns);
                                }
                            }
                        }

                        // Emit results: classes first (higher priority), then packages
                        int emitted = 0;
                        for (String qn : classFqns) {
                            var element = LookupElementBuilder.create(qn)
                                    .withTailText("  (project)", true);
                            // Higher priority for classes
                            result.addElement(PrioritizedLookupElement.withPriority(element, 1000.0));
                            if (++emitted >= MAX_RESULTS) {
                                result.stopHere();
                                return;
                            }
                        }
                        // Suggest packages afterwards, with lower priority
                        for (String pkg : packageFqns) {
                            LookupElement el = LookupElementBuilder.create(pkg)
                                    .withIcon(AllIcons.Nodes.Package)
                                    .withTailText("  (package)", true);
                            result.addElement(PrioritizedLookupElement.withPriority(el, 100.0));
                            if (++emitted >= MAX_RESULTS) {
                                result.stopHere();
                                return;
                            }
                        }
                    }
                });
    }

    /** Adds project classes whose FQN starts with {@code prefix}, up to {@link #MAX_RESULTS}. */
    private static void collectProjectClassesByPrefix(@NotNull Project project,
                                                      @NotNull GlobalSearchScope scope,
                                                      @NotNull ProjectFileIndex pfi,
                                                      @NotNull String prefix,
                                                      @NotNull Set<String> out) {
        AllClassesSearch.search(scope, project).forEach((Processor<PsiClass>) cls -> {
            var qn = cls.getQualifiedName();
            if (qn == null || !qn.startsWith(prefix) || !isInProjectContent(cls, pfi)) return true;
            out.add(qn);
            return out.size() < MAX_RESULTS;
        });
    }

    private static boolean isInProjectContent(@NotNull PsiClass cls, @NotNull ProjectFileIndex pfi) {
        var file = cls.getContainingFile();
        var vf = file != null ? file.getVirtualFile() : null;
        return vf != null && pfi.isInContent(vf);
    }

    private static boolean hasProjectDirs(@NotNull PsiPackage pkg,
                                          @NotNull ProjectFileIndex pfi,
                                          @NotNull GlobalSearchScope scope) {
        for (PsiDirectory dir : pkg.getDirectories(scope)) {
            var vf = dir.getVirtualFile();
            if (vf != null && pfi.isInContent(vf)) return true;
        }
        return false;
    }

    /** Walks down a package path while it is a linear chain with no classes, and returns the deepest leaf if deeper than start. */
    private static Optional<String> collapseLinearPackage(@NotNull PsiPackage start,
                                                          @NotNull ProjectFileIndex pfi,
                                                          @NotNull GlobalSearchScope scope) {
        PsiPackage current = start;
        String last = start.getQualifiedName();
        while (true) {
            var subs = current.getSubPackages(scope);
            var classes = current.getClasses(scope);
            if (classes.length > 0) break;
            if (subs.length != 1) break;
            var next = subs[0];
            // ensure next has project content dirs
            if (!hasProjectDirs(next, pfi, scope)) break;
            last = next.getQualifiedName();
            current = next;
        }
        if (!last.equals(start.getQualifiedName())) return Optional.of(last);
        return Optional.empty();
    }
}
