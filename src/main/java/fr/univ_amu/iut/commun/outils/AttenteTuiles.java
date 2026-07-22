package fr.univ_amu.iut.commun.outils;

import javafx.application.Platform;

/// Attente des tuiles OpenStreetMap avant une capture d'écran comportant une carte.
///
/// Le besoin est né deux fois de façon indépendante, sur la carte des points d'écoute (#152) puis
/// sur la modale de saisie GPS (#153), et a produit deux fois le même code. La provenance est
/// conservée ici parce qu'elle dit ce que l'attente sert : n'importe quelle capture affichant un
/// fond cartographique, quel que soit l'écran.
///
/// Les tuiles se téléchargent en arrière-plan (réseau) puis se peignent sur le fil JavaFX. Sans
/// attente, la capture fige la carte avant l'arrivée du fond. On laisse donc au fond de carte le
/// temps d'apparaître, en pompant les évènements FX par une **boucle d'évènements imbriquée**
/// ([Platform#enterNestedEventLoop]) : bloquer le fil JavaFX empêcherait justement le peinturage
/// qu'on attend.
///
/// **Best-effort** : hors-ligne, l'attente s'écoule pour rien mais la capture reste lisible (carrés,
/// points ou marqueurs sur fond clair), seul le fond photographique manque. Aucun échec n'est levé.
///
/// À passer comme `preparation` de [ApercuFx#capturerApresPreparation] : cette méthode montre le
/// Stage **avant** d'exécuter l'attente, ce qui rend la boucle imbriquée sans danger. Appelée hors
/// de ce cadre, elle laisserait le toolkit dans un état où un `new Stage()` ultérieur échoue sous la
/// Headless Platform de JavaFX 26 (même défaut que celui documenté sur [AttenteAudio]).
public final class AttenteTuiles {

    /// Délai laissé aux tuiles, en millisecondes. Valeur calibrée sur le temps observé entre la
    /// demande réseau et le peinturage effectif ; elle ne dépend pas de l'écran capturé, ce qui est
    /// la raison d'être de cette classe : elle vivait en quatre exemplaires, dont un sans
    /// justification, et une modification en aurait oublié trois.
    private static final long DELAI_MS = 6000;

    private AttenteTuiles() {}

    /// Attend [#DELAI_MS] en laissant tourner le fil JavaFX, puis rend la main. À appeler **sur le
    /// thread JavaFX**.
    public static void attendre() {
        Object cle = new Object();
        Thread minuteur = new Thread(() -> {
            try {
                Thread.sleep(DELAI_MS);
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
            }
            Platform.runLater(() -> Platform.exitNestedEventLoop(cle, null));
        });
        minuteur.setDaemon(true);
        minuteur.start();
        Platform.enterNestedEventLoop(cle);
    }
}
