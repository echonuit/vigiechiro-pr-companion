package fr.univ_amu.iut.commun.di;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
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

    /// Migre la base (la créant si besoin) puis compose l'injecteur. Pour l'application graphique.
    public static Injector migrerPuisComposer() {
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
