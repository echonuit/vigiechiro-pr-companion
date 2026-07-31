package fr.univ_amu.iut.cliquet;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Cliquet des injecteurs assemblés à la main** (#2669) : les outils qui énumèrent leurs modules Guice
/// au lieu de composer depuis `RacineInjecteur.modules()`.
///
/// ## Le défaut, constaté quatre fois
///
/// Les outils de capture assemblent des injecteurs **partiels** : ils ne chargent que les modules dont
/// l'écran a besoin. Quatre d'entre eux avaient oublié `CampagneModule`, et **rien n'a rougi** : la
/// fonctionnalité est optionnelle, le ViewModel reçoit un `Optional` vide, le contrôleur masque sa
/// surface. L'injecteur ment donc *correctement*.
///
/// | Outil | Ce que la capture montrait |
/// |---|---|
/// | `CapturePassage` | la modale « Modifier le passage » **sans** sa ligne Campagne |
/// | `CaptureMultisite` | la colonne « Campagne » **vide** |
/// | `CaptureImport` | la section Rattachement **sans** la liste Campagne |
/// | `CaptureSaison` | la barre **sans** le sélecteur de campagne |
///
/// Une capture absente se remarque, un garde-fou l'exige déjà. Une capture qui montre un produit
/// **amputé** se croit : la documentation utilisateur a décrit pendant des mois un rattachement que la
/// capture d'à côté ne montrait pas.
///
/// ## Ce que ce cliquet compte, et ce qu'il ne prouve pas
///
/// Il compte les fichiers qui **construisent** un injecteur sans partir de la racine de composition.
/// C'est la cause du défaut, pas le défaut lui-même : il ne dit pas qu'un module manque, il dit qu'il
/// **peut** en manquer un sans que personne le sache.
///
/// Le remède n'est donc pas d'allonger les listes de modules, mais de composer depuis
/// `RacineInjecteur.modules()` - au besoin avec `Modules.override(...)` pour figer une horloge ou
/// neutraliser un service. Cinq fichiers le font déjà, et sont immunisés par construction : on ne peut
/// pas oublier ce qu'on n'énumère pas.
class CliquetInjecteurALaMainTest {

    /// Les outils qui énumèrent encore leurs modules.
    ///
    /// **Cette liste ne doit que rétrécir.** Pour en retirer un : composer son injecteur depuis
    /// `RacineInjecteur.modules()`, puis supprimer sa ligne ici.
    private static final List<String> ENUMERENT_LEURS_MODULES = List.of(
            "fr/univ_amu/iut/analyse/outils/CaptureSynthese.java",
            "fr/univ_amu/iut/analyse/outils/CaptureSyntheseSansReferentiel.java",
            "fr/univ_amu/iut/audio/outils/CaptureValidationTadarida.java",
            "fr/univ_amu/iut/audio/outils/GraineSonsValidation.java",
            "fr/univ_amu/iut/audit/outils/CaptureAudit.java");

    @Test
    @DisplayName("La dette des injecteurs assemblés à la main ne peut que rétrécir : un outil neuf compose"
            + " depuis la racine")
    void la_dette_ne_peut_que_retrecir() {
        Cliquet.verifier(
                Cliquet.fichiersOu(Cliquet.SOURCES, CliquetInjecteurALaMainTest::assembleALaMain),
                ENUMERENT_LEURS_MODULES,
                "les outils qui assemblent leur injecteur à la main",
                "RacineInjecteur.modules(), au besoin enveloppé dans Modules.override(...)",
                "ENUMERENT_LEURS_MODULES, dans ce fichier");
    }

    /// Ce fichier **construit-il un injecteur sans partir de la racine** ?
    ///
    /// La lecture se fait sur le code **sans ses commentaires**. Sans cette précaution,
    /// `RacineInjecteur` sortait de la liste tout seul : sa Javadoc cite `RacineInjecteur.modules()` en
    /// exemple d'usage, alors que son code appelle `modules()` sans préfixe. Il aurait été innocenté par
    /// une **mention**, et pour la mauvaise raison - la bonne étant qu'il *est* la racine.
    private static boolean assembleALaMain(Cliquet.Fichier fichier) {
        if (fichier.chemin().equals(RACINE_DE_COMPOSITION)) {
            // La destination de la migration, exclue dès la pose du cliquet (ADR 2867) : la racine de
            // composition assemble évidemment ses modules à la main, c'est sa définition.
            return false;
        }
        String code = Cliquet.sansCommentaires(fichier.source());
        return CREE_UN_INJECTEUR.matcher(code).find()
                && !COMPOSE_DEPUIS_LA_RACINE.matcher(code).find();
    }

    private static final Path RACINE_DE_COMPOSITION =
            Cliquet.SOURCES.resolve(Path.of("fr", "univ_amu", "iut", "commun", "di", "RacineInjecteur.java"));

    private static final Pattern CREE_UN_INJECTEUR = Pattern.compile("Guice\\s*\\.\\s*createInjector\\s*\\(");

    private static final Pattern COMPOSE_DEPUIS_LA_RACINE = Pattern.compile("RacineInjecteur\\s*\\.\\s*modules\\s*\\(");
}
