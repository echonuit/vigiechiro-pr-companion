package fr.univ_amu.iut.commun.viewmodel;

import fr.univ_amu.iut.commun.model.Progression;
import java.time.Duration;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// Suivi de la **progression déterminée** d'une opération longue : fraction `[0, 1]` pour la barre +
/// libellé d'étape complété d'une **estimation du temps restant** (ETA). Socle **partagé** entre les
/// features (import #33/#146, génération des archives de dépôt #769), qui dupliquaient chacune ce holder.
///
/// L'estimation part du **début de l'opération** (posé par [#demarrer]) : sans référence temporelle,
/// pas d'ETA (évite un temps restant aberrant calculé depuis l'origine de `System.nanoTime()`). VM
/// agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seul `javafx.beans` est importé.
///
/// Deux façons d'appliquer un point, et le choix n'est pas indifférent : [#appliquer(Progression)] lit
/// l'écoulé à l'horloge - c'est ce que veut une opération réelle -, tandis que
/// [#appliquer(Progression, Duration)] le reçoit de l'appelant. Les outils de capture doivent employer
/// la seconde, faute de quoi l'image qu'ils produisent dépend de la vitesse de la machine (#3483) ; la
/// règle ArchUnit `capture_pose_son_temps_ecoule` le vérifie.
public final class ProgressionOperation {

    private final ReadOnlyDoubleWrapper fraction = new ReadOnlyDoubleWrapper(this, "fraction", 0.0);
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper(this, "message", "");

    /// Horodatage (nanos) du début de l'opération courante, pour l'ETA. `0` = pas d'opération en cours.
    private long debutNanos;

    /// Démarre le suivi : fraction à 0, libellé initial, et **pose la référence temporelle** de l'ETA.
    /// À appeler sur le fil JavaFX au lancement de l'opération.
    public void demarrer(String messageInitial) {
        fraction.set(0.0);
        message.set(messageInitial);
        debutNanos = System.nanoTime();
    }

    /// Applique un point de progression : met à jour la fraction et le libellé d'étape (complété de
    /// l'ETA). À appeler sur le fil JavaFX (le callback du service s'exécute hors-thread).
    ///
    /// Le temps écoulé est **lu à l'horloge**, depuis la référence posée par [#demarrer] : c'est le bon
    /// comportement pour une opération réelle, dont l'estimation doit suivre le temps qui passe.
    /// Une **capture de documentation**, elle, doit passer par [#appliquer(Progression, Duration)] :
    /// sinon l'image emporte la vitesse de la machine qui l'a rendue (#3483).
    public void appliquer(Progression point) {
        appliquer(point, debutNanos == 0L ? Duration.ZERO : Duration.ofNanos(System.nanoTime() - debutNanos));
    }

    /// Applique un point de progression avec un temps écoulé **posé par l'appelant**, au lieu d'être lu à
    /// l'horloge (#3483).
    ///
    /// Un outil de capture pose un état : le libellé, la fraction… et l'écoulé, qui n'est pas moins un
    /// paramètre de l'image que les deux autres. Le laisser à l'horloge revient à laisser la **vitesse de
    /// la machine** décider du contenu du PNG - `apercu-import-decompression-volume.png` annonçait
    /// « ~13 s restant » sur l'intégration continue et « ~15 s » sur un poste, pour le même état.
    ///
    /// Ce n'est pas un fac-similé : la chaîne affichée reste calculée par [#avecTempsRestant], le code de
    /// production. Seule la mesure du temps, que la capture ne peut pas reproduire, devient une donnée
    /// d'entrée. Un `ecoule` négatif est traité comme nul (aucune estimation).
    ///
    /// Quand le travail amont est **parallèle** (#814), les points de plusieurs unités peuvent arriver
    /// dans le désordre ; on garde donc la fraction **monotone** ([Math#max]) pour que la barre n'avance
    /// jamais à reculons (l'ETA se fonde sur cet avancement consolidé). Sans effet sur les opérations
    /// séquentielles, dont les fractions croissent déjà.
    public void appliquer(Progression point, Duration ecoule) {
        long ecouleNanos = ecoule.isNegative() ? 0L : ecoule.toNanos();
        double avancement = Math.max(fraction.get(), point.fraction());
        fraction.set(avancement);
        message.set(avecTempsRestant(point.libelle(), avancement, ecouleNanos));
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

    /// Libellé d'étape en cours (« Copie X/N », « Compression X/N », avec ETA).
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
