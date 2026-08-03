package fr.univ_amu.iut.commun.di;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.persistence.VerrouWorkspace;
import java.nio.file.Files;

/// Phase d'amorçage de l'application : **migrer, puis composer** (ADR 1038).
///
/// La composition de l'injecteur lit les drapeaux de fonctionnalités en base
/// ([Fonctionnalites#filtreActives()]). Si elle précédait les migrations, une migration portant sur
/// une clé `feature.*` s'appliquerait **trop tard** : le choix de l'utilisateur serait ignoré, sans
/// message, pendant tout un lancement (#2187). Migrer d'abord garantit que les drapeaux sont lus dans
/// une base à jour.
///
/// Deux entrées, parce que les deux surfaces n'ont pas le même contrat sur une base absente :
///
/// - l'**application graphique** a toujours besoin d'une base ouvrable : elle migre **toujours**, ce
///   qui la crée au besoin ;
/// - la **CLI** ne doit créer aucun fichier pour une simple aide (`vigiechiro --help` sur une
///   installation neuve) : elle ne migre que si la base **existe déjà**. Sur une base absente il n'y a
///   de toute façon ni schéma à mettre à jour ni drapeau persisté à périmer.
public final class Amorcage {

    private Amorcage() {}

    /// Verrou tenu par l'application graphique pour toute la durée de son exécution (#2731).
    private static VerrouWorkspace verrou;

    /// Réserve le dossier de travail pour cette instance, et **refuse** de démarrer s'il est déjà
    /// occupé (#2731).
    ///
    /// Refus plutôt que bascule en lecture seule : ce mode n'existe nulle part dans le produit, il
    /// faudrait gater chaque écriture de chaque fonctionnalité. Le refus est immédiat, sans
    /// ambiguïté, et réversible : passer plus tard en lecture seule ne remettrait pas en cause le
    /// verrou, seulement ce qu'on fait quand il est déjà pris
    /// ([ADR 2731](../../../../../../../dev-docs/decisions/2731-un-seul-processus-par-workspace.md)).
    ///
    /// @throws DataAccessException si un autre processus occupe le dossier de travail
    public static synchronized void reserverLeWorkspace() {
        Workspace workspace = Workspace.resolu();
        verrou = VerrouWorkspace.prendre(workspace)
                .orElseThrow(
                        () -> new DataAccessException(messageDossierOccupe(VerrouWorkspace.occupant(workspace)), null));
    }

    /// La phrase servie à qui trouve le dossier de travail occupé.
    ///
    /// Elle vit ici, et non à l'intérieur du refus, pour que l'aperçu documentaire la rende **telle
    /// quelle** avec un occupant figé : le vrai occupant porte un PID et un horodatage, et un aperçu
    /// qui les afficherait changerait à chaque régénération. Même compromis que l'aperçu « À propos »,
    /// qui fige le dossier de travail pour la même raison.
    public static String messageDossierOccupe(String occupant) {
        return "Ce dossier de travail est déjà ouvert par une autre fenêtre de l'application ("
                + occupant + "). Deux fenêtres qui écrivent la même base la corrompent : fermez"
                + " l'autre fenêtre, puis relancez.";
    }

    /// Rend le dossier de travail. Sans cela, un verrou mal rendu transformerait un incident en
    /// blocage définitif.
    public static synchronized void libererLeWorkspace() {
        if (verrou != null) {
            verrou.close();
            verrou = null;
        }
    }

    /// Réserve le dossier de travail, migre la base (la créant si besoin) puis compose l'injecteur.
    /// Pour l'application graphique.
    public static Injector migrerPuisComposer() {
        reserverLeWorkspace();
        migrer(Workspace.resolu());
        return RacineInjecteur.creer();
    }

    /// Met la base à jour **seulement si elle existe déjà**, sans jamais la créer. Pour la CLI, appelée
    /// avant la composition de l'injecteur (qui, elle, lira les drapeaux dans la base migrée).
    public static void migrerSiPresente() {
        Workspace workspace = Workspace.resolu();
        if (Files.exists(workspace.cheminBaseDeDonnees())) {
            migrer(workspace);
        }
    }

    private static void migrer(Workspace workspace) {
        new MigrationSchema(new SourceDeDonnees(workspace)).migrer();
    }
}
