package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import fr.univ_amu.iut.passage.model.DecompteAudio;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;

/// Disponibilité de l'audio pour la vue audio unifiée (#1301) : ré-observation du décompte à
/// l'ouverture de l'écran, texte du bandeau « passage archivé / audio partiel », et présence du
/// fichier de la séquence sélectionnée. Extrait d'[AudioViewModel] pour qu'il garde la seule
/// orchestration de la revue (PMD GodClass).
final class DisponibiliteEcoute {

    private final ServiceDisponibiliteAudio disponibilite;

    /// Présence d'un fichier sur disque (`Files::exists` en production) : la seule question posée au
    /// système de fichiers, injectée pour rester testable avec des chemins factices.
    private final Predicate<Path> fichierPresent;

    DisponibiliteEcoute(ServiceDisponibiliteAudio disponibilite, Predicate<Path> fichierPresent) {
        this.disponibilite = Objects.requireNonNull(disponibilite, "disponibilite");
        this.fichierPresent = Objects.requireNonNull(fichierPresent, "fichierPresent");
    }

    /// Disponibilité **ré-observée** de l'audio du passage (#1298), pour la source `ParPassage`
    /// seulement (`null` sinon) : l'écran doit refléter le disque au moment où on l'ouvre (archivage
    /// entre-temps, disque rebranché...), d'où l'invalidation du cache avant lecture. Balayage
    /// groupé : un accès disque par dossier, pas par séquence.
    DecompteAudio decompte(SourceObservations source) {
        Long idPassage = ResolveurSourceAudio.idPassage(source);
        if (idPassage == null) {
            return null;
        }
        disponibilite.invalider(idPassage);
        return disponibilite.decompte(idPassage);
    }

    /// Le fichier de la séquence sélectionnée est-il **parti** ? (`false` quand aucun chemin : rien à
    /// écouter n'est un état normal, pas une absence à expliquer.)
    boolean manquant(Path chemin) {
        return chemin != null && !fichierPresent.test(chemin);
    }

    /// Texte du bandeau de disponibilité (#1301) : vide quand tout est là (ou source multi-passages),
    /// sinon l'explication et la voie de retour. Le décompte `presentes/total` en `PARTIELLE` est le
    /// signal « il manque un bout de disque », pas « l'application est cassée ».
    static String texteBandeau(DecompteAudio decompte) {
        if (decompte == null) {
            return "";
        }
        return switch (decompte.disponibilite()) {
            case COMPLETE -> "";
            case PARTIELLE ->
                "Audio partiel : " + decompte.presentes() + " séquence(s) sur " + decompte.total()
                        + " présente(s) sur disque. L'écoute reste possible là où le fichier existe ;"
                        + " ailleurs, réimportez les fichiers d'origine.";
            case ABSENTE ->
                "Passage archivé : l'audio n'est pas conservé localement. Les observations et"
                        + " validations restent consultables ; pour réécouter un jour, réimportez les"
                        + " fichiers d'origine.";
        };
    }
}
