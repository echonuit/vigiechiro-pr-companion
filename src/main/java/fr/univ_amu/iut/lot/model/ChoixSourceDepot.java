package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

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
/// Le mode vient du **reglage** `depot.mode` ([ModeDepot], #1997) : archives ZIP par defaut, ou
/// sequences WAV. Ce n'est plus la place disque qui tranche - elle ne fait plus que **refuser** un
/// depot ZIP qu'elle ne peut pas honorer, sans jamais changer de mode a la place de l'utilisateur.
///
/// Quand le mode ZIP s'applique, la source rendue est **regenerable**
/// ([SourceArchivesRegenerables]) : ses identifiants viennent de la partition et non du contenu du
/// dossier, donc des archives effacees ne font plus basculer le depot en mode WAV ni perdre la
/// progression deja acquise.
///
/// ⚠️ Ce choix n'est pas qu'une question de place : en mode ZIP, la plateforme detruit l'archive apres
/// extraction et ne remonte pas les WAV sur S3 (#1244), donc l'audio n'est pas recuperable cote serveur
/// et la participation ne peut pas etre relancee. C'est pourquoi il se choisit desormais.
final class ChoixSourceDepot {

    private final RepertoireDepot repertoireDepot;
    private final Supplier<CompacteurDepot> compacteur;

    /// Espace disque disponible dans le dossier de session, isole pour rendre le seuil **testable** sans
    /// dependre de l'etat reel de la machine - meme raison que [EspaceDisque], dont c'est
    /// ici le pendant au niveau du lot.
    private final ToLongFunction<String> espaceDisponible;

    /// Mode choisi par l'utilisateur (reglage `depot.mode`, #1997). **Fournisseur** et non valeur : le
    /// reglage est relu a chaque depot, comme le plafond d'archive.
    private final Supplier<ModeDepot> mode;

    ChoixSourceDepot(RepertoireDepot repertoireDepot, Supplier<CompacteurDepot> compacteur, Supplier<ModeDepot> mode) {
        this(
                repertoireDepot,
                compacteur,
                mode,
                Objects.requireNonNull(repertoireDepot, "repertoireDepot")::espaceDisponible);
    }

    ChoixSourceDepot(
            RepertoireDepot repertoireDepot,
            Supplier<CompacteurDepot> compacteur,
            Supplier<ModeDepot> mode,
            ToLongFunction<String> espaceDisponible) {
        this.repertoireDepot = Objects.requireNonNull(repertoireDepot, "repertoireDepot");
        this.compacteur = Objects.requireNonNull(compacteur, "compacteur");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.espaceDisponible = Objects.requireNonNull(espaceDisponible, "espaceDisponible");
    }

    /// La source a deposer pour ce lot.
    ///
    /// @param lot etat du lot (dossier de session, volume des sequences)
    /// @param sequences sequences transformees a deposer, dans l'ordre
    /// @param racineSession racine du dossier de session (son nom est le prefixe des archives, R22)
    /// @throws RegleMetierException si aucune sequence n'est deposable
    SourceDepot pour(EtatLot lot, List<Path> sequences, Path racineSession) {
        return pour(lot, sequences, racineSession, mode.get());
    }

    /// La source a deposer sous un mode **impose**, sans consulter le reglage : c'est ce que forcent les
    /// options `--archives` / `--wav` de la CLI. Le reglage n'est qu'un defaut, pas une fatalite.
    ///
    /// Le mode ZIP passe par la meme [SourceArchivesRegenerables] que le defaut : forcer les archives ne
    /// doit pas ramener la liste des ZIP **presents**, qui echouait des qu'une archive avait ete liberee
    /// (#1994).
    SourceDepot pour(EtatLot lot, List<Path> sequences, Path racineSession, ModeDepot modeImpose) {
        if (sequences.isEmpty()) {
            throw new RegleMetierException("Aucune séquence transformée à déposer pour ce passage.");
        }
        if (modeImpose == ModeDepot.SEQUENCES_WAV) {
            return SourceDepot.desFichiers(sequences);
        }
        // Mode ZIP demande, mais le disque ne tient meme pas la fenetre du pipeline : on **refuse** au
        // lieu de basculer en WAV dans le dos de l'utilisateur. Depuis que le mode se choisit (#1997), un
        // repli silencieux changerait ce qu'il advient de son audio cote serveur sans qu'il l'ait voulu.
        if (!archivesPresentes(lot) && !disquePermetArchives(lot)) {
            throw new RegleMetierException("Espace disque insuffisant pour générer les archives de dépôt."
                    + " Libérez de la place, ou choisissez « Séquences WAV » dans Réglages ▸ Dépôt (plus"
                    + " lent, mais sans archive à écrire).");
        }
        return new SourceArchivesRegenerables(
                sequences,
                racineSession.getFileName().toString(), // R22 : nom du dossier = préfixe R6
                repertoireDepot.dossier(racineSession.toString()),
                compacteur.get());
    }

    /// `true` si le disque permet de **deposer en ZIP**, c'est-a-dire de materialiser la fenetre du
    /// pipeline (#1996). Faux si session / volume / disque inconnus (impossible de creer des archives ->
    /// repli WAV assume).
    ///
    /// **Le seuil a change de nature.** Il exigeait la place pour la **totalite** des archives, parce que
    /// le depot les generait toutes avant d'en televerser une seule. Le pipeline n'en materialise jamais
    /// plus que sa fenetre : exiger le volume total refuserait maintenant des depots qui passent tres
    /// bien. C'est la complexite que le sequencement creait lui-meme.
    ///
    /// A ne pas confondre avec le garde-fou de la **generation** ([CompacteurDepot#compacter], etape ②) :
    /// celle-la ecrit reellement tout le lot d'un coup, son seuil reste donc le volume total et
    /// [AnticipationEspaceDisque] continue de l'anticiper ainsi. Les deux seuils different parce que les
    /// deux operations different.
    boolean disquePermetArchives(EtatLot lot) {
        if (lot.cheminDossier() == null || lot.volumeSequencesOctets() == null) {
            return false;
        }
        long disponible = espaceDisponible.applyAsLong(lot.cheminDossier());
        if (disponible <= 0) {
            // Zero signale un disque **illisible**, pas un disque plein : on ne parie pas. PIT signale la
            // mutation en `< 0` comme survivante, mais elle est **equivalente** - a zero disponible, le
            // seuil qui suit refuse de toute facon, puisqu'il est toujours strictement positif.
            return false;
        }
        // La fenetre, ou le lot entier s'il est plus petit qu'elle : inutile d'exiger 1,4 Go pour une
        // nuit qui tient dans une seule archive de 200 Mo.
        long requis = Math.min(
                compacteur.get().espaceRequisPourLaFenetre(),
                CompacteurDepot.estimationTailleDepot(lot.volumeSequencesOctets()));
        return requis <= disponible;
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
