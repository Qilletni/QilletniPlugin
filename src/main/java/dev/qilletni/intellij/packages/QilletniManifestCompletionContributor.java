package dev.qilletni.intellij.packages;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.concurrency.AppExecutorUtil;
import dev.qilletni.intellij.QilletniBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Completion in the {@code dependencies:} section of {@code qilletni_info.yml}: package names from the registry
 * (inserting {@code scope/name: ^latest}) and, after the colon, that package's published versions.
 */
public final class QilletniManifestCompletionContributor extends CompletionContributor {
    public QilletniManifestCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
                var position = parameters.getPosition();
                if (!QilletniManifestDependencies.isManifest(position)) return;
                var section = QilletniManifestDependencies.findDependenciesSection(position);
                if (section == null) return;

                var entry = QilletniManifestDependencies.findDependencyEntry(position);
                if (entry != null && entry.getValue() != null && PsiTreeUtil.isAncestor(entry.getValue(), position, false)) {
                    addVersions(entry.getKeyText(), result);
                } else {
                    addPackages(result);
                }
            }
        });
    }

    private static void addPackages(CompletionResultSet result) {
        result.addLookupAdvertisement(QilletniBundle.message("registry.completion.advertisement"));
        for (var pkg : fetch(() -> QilletniRegistryClient.getInstance().listPackages())) {
            result.addElement(LookupElementBuilder.create(pkg.id())
                    .withIcon(AllIcons.Nodes.PpLib)
                    .withTypeText(pkg.latest(), true)
                    .withInsertHandler((ctx, item) -> {
                        var document = ctx.getDocument();
                        int tail = ctx.getTailOffset();
                        var rest = document.getText().substring(tail, Math.min(document.getTextLength(), tail + 2));
                        if (!rest.startsWith(":") && !pkg.latest().isBlank()) {
                            var suffix = ": ^" + pkg.latest();
                            document.insertString(tail, suffix);
                            ctx.getEditor().getCaretModel().moveToOffset(tail + suffix.length());
                        }
                    }));
        }
    }

    /** Runs a registry call off the completion thread so typing can still cancel completion while it waits. */
    private static <T> List<T> fetch(Supplier<List<T>> call) {
        var future = CompletableFuture.supplyAsync(call, AppExecutorUtil.getAppExecutorService());
        try {
            return ProgressIndicatorUtils.awaitWithCheckCanceled(future);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static void addVersions(String id, CompletionResultSet result) {
        var versions = fetch(() -> QilletniRegistryClient.getInstance().listVersions(id));
        for (int i = 0; i < versions.size(); i++) {
            var version = versions.get(i);
            // Keep the registry's order (newest last) by giving later versions higher priority
            result.addElement(PrioritizedLookupElement.withPriority(
                    LookupElementBuilder.create("^" + version).withTypeText(QilletniBundle.message("registry.completion.compatible")), i));
            result.addElement(PrioritizedLookupElement.withPriority(
                    LookupElementBuilder.create(version).withTypeText(QilletniBundle.message("registry.completion.exact")), i - 0.5));
        }
    }
}
