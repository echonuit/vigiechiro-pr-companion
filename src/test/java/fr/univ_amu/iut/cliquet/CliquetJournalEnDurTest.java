package fr.univ_amu.iut.cliquet;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Cliquet du journal en dur** (#2868) : les fichiers qui écrivent un journal `LogPR` littéral.
///
/// ## Pourquoi ça compte
///
/// Le format du journal (`JJ/MM/AA - HH:MM:SS PR<série> …`) est celui que produit un vrai enregistreur.
/// Le recopier, c'est autant d'endroits à retoucher le jour où un champ bouge, et autant d'occasions
/// d'en oublier un, sans que rien ne le dise.
///
/// La destination est `fr.univ_amu.iut.fixture.JournalDeCapteur`, extrait de `GenerateurCartesSD` par
/// #2904 - le générateur de recette s'en sert désormais lui aussi, une seule source pour deux usages.
///
/// ## Ce qui est hors mesure, et pourquoi c'est écrit
///
/// `AnalyseurLogPRTest` et `InspecteurDossierTest` testent les **analyseurs** de ce format : leur donner
/// un générateur reviendrait à tester le générateur contre lui-même. Le paquet `recette` est la
/// **source** de la brique, pas sa dette.
///
/// Le paquet `fixture` non plus : `JournalDeCapteur` compose évidemment un nom de journal, c'est son
/// métier et c'est la **destination** de la migration. L'exclusion manquait à la pose de ce cliquet, où
/// le paquet n'écrivait encore aucun journal - la destination n'existait pas. Elle est écrite maintenant,
/// plutôt que laissée à la chance.
class CliquetJournalEnDurTest {

    /// Le **préambule d'un journal, écrit à la main** : la ligne de démarrage ou celle des paramètres
    /// d'acquisition, horodatées comme les écrit un vrai enregistreur.
    ///
    /// Ce qui se duplique n'est ni un nom de fichier ni une date, mais le **préambule que produit la
    /// brique**. Chercher `"LogPR…"` comptait neuf fichiers qui n'écrivent aucun journal, et chercher un
    /// horodatage en comptait deux de plus, dont le libellé d'anomalie portait la date (ADR 2867, la
    /// confusion usage / mention). Une ligne d'anomalie écrite par un test reste chez lui : c'est son sujet,
    /// et [JournalDeCapteur#ecrireAvec] est là pour ça.
    private static final Pattern JOURNAL_COMPOSE =
            Pattern.compile("\\d\\d/\\d\\d/\\d\\d - \\d\\d:\\d\\d:\\d\\d PR[^\"]*"
                    + "(Démarrage Passive Recorder|Paramètres\\s*:\\s*Acquisi\\.)");

    /// La dette épinglée : **elle est vide**, et le cliquet reste pour empêcher qu'elle renaisse.
    ///
    /// Troisième axe du chantier #1771 à atteindre zéro, après les writers WAV (#2864) et les captures de
    /// sortie (#2866).
    private static final List<String> ECRIVENT_UN_JOURNAL_EN_DUR = List.of();

    @Test
    @DisplayName("La dette du journal en dur ne peut que rétrécir : aucun nouveau littéral")
    void la_dette_ne_peut_que_retrecir() {
        Cliquet.verifier(
                Cliquet.fichiersOu(CliquetJournalEnDurTest::ecritUnJournalEnDur),
                ECRIVENT_UN_JOURNAL_EN_DUR,
                "les tests qui écrivent un journal LogPR en dur",
                "fr.univ_amu.iut.fixture.JournalDeCapteur",
                "ECRIVENT_UN_JOURNAL_EN_DUR, dans ce fichier");
    }

    private static boolean ecritUnJournalEnDur(Cliquet.Fichier fichier) {
        if (fichier.dansLePaquet("cliquet") || fichier.dansLePaquet("recette") || fichier.dansLePaquet("fixture")) {
            return false;
        }
        String nom = fichier.chemin().getFileName().toString();
        if (nom.equals("AnalyseurLogPRTest.java") || nom.equals("InspecteurDossierTest.java")) {
            return false;
        }
        return JOURNAL_COMPOSE.matcher(fichier.source()).find();
    }
}
