package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/// **Ce qu'on depose, et sous quelle forme** : la politique ZIP / WAV du depot (#984), extraite de
/// [ServiceLot] (#1994).
///
/// Elle y formait un ilot : quatre methodes qui ne parlaient qu'entre elles (le choix du mode,
/// l'anticipation disque, l'estimation compressee, la liste des archives) au milieu d'un service
/// dedie au cycle de vie du lot. Les sortir rend la regle lisible d'un seul tenant, et ramene
/// [ServiceLot] sous le plafond `GodClass` du portail qualite.
///
/// ## La regle
///
/// Les **archives ZIP** sont privilegiees, comme le front web. Le **repli WAV** n'intervient que si le
/// disque ne permet pas de les creer.
///
/// Quand le mode ZIP s'applique, la source rendue est **regenerable**
/// ([SourceArchivesRegenerables]) : ses identifiants viennent de la partition et non du contenu du
/// dossier, donc des archives effacees ne font plus basculer le depot en mode WAV ni perdre la
/// progression deja acquise.
///
/// ⚠️ Ce choix n'est pas qu'une question de place : en mode ZIP, la plateforme detruit l'archive apres
/// extraction et ne remonte pas les WAV sur S3 (#1244), donc l'audio n'est pas recuperable cote serveur
/// et la participation ne peut pas etre relancee. #1997 rendra ce choix explicite au lieu de le laisser
/// decider par l'espace disponible.
final class ChoixSourceDepot {

    private final RepertoireDepot repertoireDepot;
    private final Supplier<CompacteurDepot> compacteur;

    ChoixSourceDepot(RepertoireDepot repertoireDepot, Supplier<CompacteurDepot> compacteur) {
        this.repertoireDepot = Objects.requireNonNull(repertoireDepot, "repertoireDepot");
        this.compacteur = Objects.requireNonNull(compacteur, "compacteur");
    }

    /// La source a deposer pour ce lot.
    ///
    /// @param lot etat du lot (dossier de session, volume des sequences)
    /// @param sequences sequences transformees a deposer, dans l'ordre
    /// @param racineSession racine du dossier de session (son nom est le prefixe des archives, R22)
    /// @throws RegleMetierException si aucune sequence n'est deposable
    SourceDepot pour(EtatLot lot, List<Path> sequences, Path racineSession) {
        if (sequences.isEmpty()) {
            throw new RegleMetierException("Aucune séquence transformée à déposer pour ce passage.");
        }
        if (!archivesPresentes(lot) && !disquePermetArchives(lot)) {
            return SourceDepot.desFichiers(sequences); // repli WAV : le disque ne permet pas les archives
        }
        return new SourceArchivesRegenerables(
                sequences,
                racineSession.getFileName().toString(), // R22 : nom du dossier = préfixe R6
                repertoireDepot.dossier(racineSession.toString()),
                compacteur.get());
    }

    /// Les chemins a deposer, forme historique de [#pour] conservee pour les appelants qui raisonnent
    /// encore en liste (verification, audit). Le mode ZIP exige ici que les archives **existent** : sans
    /// resolution paresseuse, on ne peut pas promettre un chemin qu'il faudrait produire.
    ///
    /// @throws RegleMetierException si rien n'est deposable, ou si les archives restent a generer
    List<Path> fichiers(EtatLot lot, List<Path> sequences) {
        List<Path> archives = archivesDe(lot);
        if (!archives.isEmpty()) {
            return archives;
        }
        if (sequences.isEmpty()) {
            throw new RegleMetierException("Aucune séquence transformée à déposer pour ce passage.");
        }
        if (disquePermetArchives(lot)) {
            throw new RegleMetierException("Générez d'abord les archives de dépôt (étape 2) : le dépôt ZIP"
                    + " est privilégié et l'espace disque le permet.");
        }
        return sequences; // repli WAV : l'espace disque ne permet pas de créer les archives ZIP
    }

    /// `true` si l'espace disque du dossier de session permet de generer les archives ZIP (estimation
    /// compression comprise <= disponible). Faux si session / volume / disque inconnus (impossible de
    /// creer des archives -> repli WAV assume).
    boolean disquePermetArchives(EtatLot lot) {
        if (lot.cheminDossier() == null || lot.volumeSequencesOctets() == null) {
            return false;
        }
        long disponible = repertoireDepot.espaceDisponible(lot.cheminDossier());
        return disponible > 0 && CompacteurDepot.estimationTailleDepot(lot.volumeSequencesOctets()) <= disponible;
    }

    /// Archives ZIP presentes sur le disque pour ce lot (vide si le lot n'a pas de dossier).
    List<Path> archivesDe(EtatLot lot) {
        if (lot.cheminDossier() == null) {
            return List.of();
        }
        return repertoireDepot.lister(lot.cheminDossier()).stream()
                .map(ArchiveDepot::chemin)
                .toList();
    }

    private boolean archivesPresentes(EtatLot lot) {
        return !archivesDe(lot).isEmpty();
    }
}
