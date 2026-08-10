package fr.univ_amu.iut.commun.persistence;

import fr.univ_amu.iut.commun.model.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Comment un dossier de son sauvegardé reprend sa place : **on copie à côté, on vérifie, puis on
/// bascule** (#3514).
///
/// La restauration copiait chaque racine directement à destination, l'une après l'autre, après avoir
/// déjà remplacé la base. Une panne au troisième dossier sur cinq laissait un workspace mixte - deux
/// dossiers venus de la sauvegarde, trois d'avant, et des `root_path` jamais réécrits - que rien ne
/// décrivait.
///
/// ⚠️ **Ce n'est pas de l'atomicité, et il ne faut pas le dire.** Basculer trois dossiers, ce sont
/// trois renommages. Ce qu'on gagne est de ramener la fenêtre d'une **copie complète** - des minutes,
/// des gigaoctets - à une **suite de renommages**, des millisecondes ; et de rendre l'échec réparable
/// plutôt que muet.
final class BasculeRacines {

    /// Suffixe du dossier de bascule, frère de sa destination.
    private static final String SUFFIXE_EN_COURS = ".en-cours";

    private static final Logger LOG = Logger.getLogger(BasculeRacines.class.getName());

    private final Workspace workspace;

    BasculeRacines(Workspace workspace) {
        this.workspace = workspace;
    }

    /// Copie une racine **à côté** de sa destination, sans rien remplacer encore.
    ///
    /// Le temporaire est un frère de la destination, donc sur le **même système de fichiers** : c'est
    /// la condition d'un renommage. Le poser dans un dossier temporaire du système ferait de la
    /// bascule une seconde copie, et rendrait le remède inutile.
    EnAttente preparer(Path source, String cheminOrigine) {
        Path destination = destinationPour(Path.of(cheminOrigine));
        Path temporaire = destination.resolveSibling(destination.getFileName() + SUFFIXE_EN_COURS);
        try {
            // Un `.en-cours` laissé par une tentative morte contaminerait la copie : elle écrase les
            // fichiers homonymes, elle ne retire pas les surnuméraires.
            ArborescenceFichiers.supprimerRecursivement(temporaire);
            ArborescenceFichiers.copier(source, temporaire);
        } catch (IOException echec) {
            throw new DataAccessException("Impossible de préparer le dossier de son de " + destination, echec);
        }
        return new EnAttente(cheminOrigine, temporaire, destination);
    }

    /// Où remettre une racine : **à son emplacement d'origine s'il est encore là**, sinon dans le
    /// workspace, sous son nom de dossier.
    ///
    /// Le critère est que le dossier d'origine **existe déjà** et soit inscriptible, et non que son
    /// parent soit créable. La nuance évite un piège coûteux : `/mnt/disque-a` est un point de
    /// montage vide quand le disque n'est pas branché ; le juger « créable » y déverserait des
    /// gigaoctets sur le disque système, que le montage du vrai disque masquerait ensuite. Mieux
    /// vaut un dossier déplacé et annoncé qu'un dossier écrit dans un trou.
    ///
    /// Conséquence assumée : restaurer une nuit qu'on vient de supprimer la remet dans le workspace,
    /// et non à sa place, puisque sa place n'existe plus. Le compte rendu le dit, et la base pointe
    /// vers l'endroit réel.
    /// Où une racine atterrira. Visible du paquet : [BesoinDePlace] en a besoin **avant** toute copie,
    /// pour savoir sur quel système de fichiers la place doit être cherchée (#3563).
    Path destinationPour(Path origine) {
        if (Files.isDirectory(origine) && Files.isWritable(origine)) {
            return origine;
        }
        Path nom = origine.getFileName();
        return workspace.racine().resolve(nom == null ? "session" : nom.toString());
    }

    /// Refuse quand la destination porte quelque chose que la sauvegarde n'a pas.
    ///
    /// La bascule **remplace** la destination entière : ce qui s'y trouvait en plus disparaîtrait.
    /// Avant #3514 la copie s'écrasait par-dessus, donc le surnuméraire survivait - mais l'inventaire
    /// ne correspondait alors plus, et la restauration échouait **après** avoir remplacé la base.
    /// L'intention se garde, le moment change : on refuse **avant** d'avoir rien touché, ce qui rend
    /// le refus actionnable (code de sortie 2) au lieu d'être une panne au milieu du gué.
    ///
    /// ⚠️ Supprimer ces fichiers en silence aurait été un effet de bord : restaurer ne doit pas
    /// devenir un moyen détourné d'effacer ce que l'utilisateur avait posé là.
    void refuserSiLaDestinationPorteAutreChose(EnAttente attente) {
        Path destination = attente.destination();
        if (Files.exists(destination) && !Files.isDirectory(destination)) {
            throw new RefusAvantEcriture(
                    "« " + destination + " » est un fichier, là où le dossier de la nuit doit être remis."
                            + " Le remettre en place l'effacerait : déplacez-le ailleurs, puis relancez."
                            + " Rien n'a été touché.",
                    null);
        }
        if (!Files.isDirectory(destination)) {
            return;
        }
        try {
            Set<String> attendus = fichiersRelatifs(attente.temporaire());
            for (String present : fichiersRelatifs(destination)) {
                if (!attendus.contains(present)) {
                    throw new RefusAvantEcriture(
                            "Le dossier « " + destination + " » contient « " + present
                                    + " », que la sauvegarde n'a pas. Le remettre en place l'effacerait :"
                                    + " déplacez-le ailleurs, puis relancez. Rien n'a été touché.",
                            null);
                }
            }
        } catch (IOException echec) {
            throw new DataAccessException("Impossible d'inspecter le dossier de destination " + destination, echec);
        }
    }

    /// Les chemins des **fichiers** d'un dossier, relatifs à lui. Les dossiers intermédiaires ne
    /// comptent pas : un dossier vide de plus n'emporte aucune donnée, et refuser sur lui ferait
    /// échouer des restaurations légitimes pour rien.
    private static Set<String> fichiersRelatifs(Path racine) throws IOException {
        try (Stream<Path> arbre = Files.walk(racine)) {
            return arbre.filter(Files::isRegularFile)
                    .map(chemin -> racine.relativize(chemin).toString())
                    .collect(Collectors.toCollection(HashSet::new));
        }
    }

    /// Une racine copiée et vérifiée, qui n'attend plus que de prendre sa place.
    record EnAttente(String origine, Path temporaire, Path destination) {

        /// Remplace la destination par le temporaire. L'ancien contenu ne disparaît qu'ici, et le
        /// nouveau le suit immédiatement.
        PlacementRacine basculer() {
            try {
                ArborescenceFichiers.supprimerRecursivement(destination);
                Files.move(temporaire, destination);
            } catch (IOException echec) {
                throw new DataAccessException("Impossible de remettre le dossier de son dans " + destination, echec);
            }
            return new PlacementRacine(origine, destination.toString());
        }

        /// Efface le temporaire. Un échec de nettoyage ne doit pas masquer la panne qui l'a causé :
        /// on laisse alors le dossier plutôt que de perdre l'exception d'origine.
        void abandonner() {
            try {
                ArborescenceFichiers.supprimerRecursivement(temporaire);
            } catch (IOException echec) {
                LOG.log(Level.WARNING, echec, () -> "Temporaire de restauration non nettoyé : " + temporaire);
            }
        }
    }
}
