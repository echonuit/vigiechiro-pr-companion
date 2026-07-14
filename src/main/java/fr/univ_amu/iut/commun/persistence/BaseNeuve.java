package fr.univ_amu.iut.commun.persistence;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/// **Repartir d'une base vide** (#1419, EPIC #1154) : supprime le fichier de base, ses journaux, puis
/// rejoue la migration. Le workspace se retrouve dans l'état d'une première ouverture — schéma à jour,
/// référentiel de taxons semé, aucune donnée.
///
/// **Ce que ce n'est pas** : une réinitialisation « logique » table par table. Un `DELETE FROM` laisserait
/// des séquences d'auto-incrément, des réglages et des vues sauvegardées derrière lui, et l'objet ici est
/// justement de n'avoir *rien* derrière soi.
///
/// **Pourquoi c'est faisable à chaud.** Les connexions du socle sont **de courte durée** (ouvertes puis
/// fermées par opération, cf. [SourceDeDonnees]) : il n'y a aucun pool à vider, aucune connexion longue à
/// fermer. La `SourceDeDonnees` ne retient qu'une URL JDBC — la prochaine connexion ouvrira simplement le
/// fichier neuf. C'est déjà le mécanisme sur lequel repose [ServiceSauvegarde#restaurer(Path)], qui
/// remplace le fichier de base sous l'application depuis #148.
///
/// En revanche, une **application graphique déjà ouverte** garde en mémoire des écrans peuplés par
/// l'ancienne base : ils afficheraient des fantômes. C'est l'appelant qui doit exiger un redémarrage —
/// ce service, lui, ne connaît pas d'IHM.
///
/// Un **filet de sécurité** est posé malgré tout (`vigiechiro.db.avant-reset`), au cas où la base neuve
/// aurait été demandée par erreur : c'est le même geste que la restauration, pour la même raison.
public class BaseNeuve {

    private static final String SUFFIXE_FILET = ".avant-reset";

    private final SourceDeDonnees source;

    @Inject
    public BaseNeuve(SourceDeDonnees source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /// Efface la base et la recrée vide (schéma migré, référentiel semé). Renvoie le **filet** : la copie
    /// de la base d'avant, qu'un `restaurer` saurait relire. Action **délibérée et destructrice** — à
    /// n'appeler qu'après une sauvegarde complète et une confirmation explicite.
    ///
    /// @return le chemin de la base mise de côté, ou vide s'il n'y avait pas encore de base
    /// @throws DataAccessException si le fichier ne peut être ni copié ni supprimé
    public Path repartirDeZero() {
        Path base = source.workspace().cheminBaseDeDonnees();
        Path filet = base.resolveSibling(base.getFileName() + SUFFIXE_FILET);
        try {
            if (Files.exists(base)) {
                Files.copy(base, filet, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.delete(base);
            }
            // Les journaux d'une base disparue masqueraient la neuve : SQLite les rejouerait par-dessus.
            purgerJournal(base, "-wal");
            purgerJournal(base, "-shm");
            purgerJournal(base, "-journal");
        } catch (IOException echec) {
            throw new DataAccessException("Impossible de repartir d'une base neuve (" + base + ")", echec);
        }
        new MigrationSchema(source).migrer();
        return filet;
    }

    private static void purgerJournal(Path base, String suffixe) throws IOException {
        Files.deleteIfExists(base.resolveSibling(base.getFileName() + suffixe));
    }
}
