package dev.qilletni.intellij.packages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.messages.Topic;

/**
 * Notified when a Spotify sign-in or package registry login may have changed, so open settings pages and the
 * Qilletni tool window can re-read the account status.
 */
public interface QilletniAccountsListener {
    Topic<QilletniAccountsListener> TOPIC = Topic.create("Qilletni accounts", QilletniAccountsListener.class);

    void accountsChanged();

    /** Publishes on the EDT, in any modality so a modal Settings dialog updates too. */
    static void fire() {
        var application = ApplicationManager.getApplication();
        application.invokeLater(() -> application.getMessageBus().syncPublisher(TOPIC).accountsChanged(), ModalityState.any());
    }
}
