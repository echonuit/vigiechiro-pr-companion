package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.model.Progression;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// Suivi de la **progression déterminée** de la génération des archives de dépôt (#769) : fraction
/// `[0, 1]` pour la barre + libellé d'étape complété d'une **estimation du temps restant** (ETA).
///
/// L'estimation part du **début de l'opération** (posé par [#demarrer]) : sans référence temporelle,
/// pas d'ETA (évite un temps restant aberrant calculé depuis l'origine de `System.nanoTime()`). VM
/// agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seul `javafx.beans` est importé.
public final class ProgressionLot {

    private final ReadOnlyDoubleWrapper fraction = new ReadOnlyDoubleWrapper(this, "fraction", 0.0);
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    /// Horodatage (nanos) du début de l'opération courante, pour l'ETA. `0` = pas d'opération en cours.
    private long debutNanos;

    /// Démarre le suivi : fraction à 0, libellé initial, et **pose la référence temporelle** de l'ETA.
    /// À appeler sur le fil JavaFX au lancement de la génération.
    public void demarrer(String messageInitial) {
        fraction.set(0.0);
        message.set(messageInitial);
        debutNanos = System.nanoTime();
    }

    /// Applique un point de progression : met à jour la fraction et le libellé d'étape (complété de
    /// l'ETA). À appeler sur le fil JavaFX (le callback du service s'exécute hors-thread).
    ///
    /// La compression étant **parallèle** (#814), les points de plusieurs archives peuvent arriver dans le
    /// désordre ; on garde donc la fraction **monotone** ([Math#max]) pour que la barre n'avance jamais à
    /// reculons (l'ETA se fonde sur cet avancement consolidé).
    public void appliquer(Progression point) {
        long ecoule = debutNanos == 0L ? 0L : System.nanoTime() - debutNanos;
        double avancement = Math.max(fraction.get(), point.fraction());
        fraction.set(avancement);
        message.set(avecTempsRestant(point.libelle(), avancement, ecoule));
    }

    /// Remet le suivi à zéro (fin ou erreur) : fraction à 0 et libellé vide.
    public void reinitialiser() {
        fraction.set(0.0);
        message.set("");
    }

    /// Fraction de progression `[0, 1]` (barre déterminée).
    public ReadOnlyDoubleProperty fractionProperty() {
        return fraction.getReadOnlyProperty();
    }

    /// Libellé d'étape en cours (« Compression X/N », avec ETA).
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    /// Joint au `libelle` une estimation du temps restant par **extrapolation linéaire**
    /// (restant ≈ écoulé × (1−fraction)/fraction). N'ajoute rien tant que l'avancement est nul, déjà
    /// terminé, ou trop récent pour estimer. Pur (temps écoulé en paramètre), donc testable directement.
    static String avecTempsRestant(String libelle, double fraction, long ecouleNanos) {
        if (fraction <= 0.0 || fraction >= 1.0 || ecouleNanos <= 0) {
            return libelle;
        }
        long restantSecondes = Math.round((ecouleNanos / 1_000_000_000.0) * (1.0 - fraction) / fraction);
        return libelle + " · " + formaterDuree(restantSecondes) + " restant";
    }

    /// Formate une durée en `~X s` ou `~X min Y s` (estimation, d'où le `~`).
    static String formaterDuree(long secondes) {
        if (secondes < 60) {
            return "~" + secondes + " s";
        }
        long minutes = secondes / 60;
        long reste = secondes % 60;
        return reste == 0 ? "~" + minutes + " min" : "~" + minutes + " min " + reste + " s";
    }
}
