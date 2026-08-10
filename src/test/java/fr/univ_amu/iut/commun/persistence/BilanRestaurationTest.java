package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce que le compte rendu d'une restauration **donne à lire** (#3148).
///
/// Le défaut qui a motivé ces tests ne s'est vu qu'en **ouvrant l'aperçu** : la liste des nuits
/// déplacées mettait la flèche et sa destination sur des lignes séparées, et avec deux nuits elle se
/// lisait comme quatre lignes sans lien. Aucun test ne rougissait, parce qu'ils vérifiaient tous la
/// présence d'une phrase, jamais la **forme** de la liste.
///
/// Les chemins de destination sont longs et tous identiques au dossier près : les répéter à chaque
/// ligne était ce qui forçait le retour à la ligne.
class BilanRestaurationTest {

    private static final String TRAVAIL = "/home/naturaliste/Documents/VigieChiro-Companion";

    @Test
    @DisplayName("une nuit déplacée tient sur UNE ligne : nom de la nuit, puis d'où elle venait")
    void une_nuit_deplacee_tient_sur_une_ligne() {
        String texte = deuxNuitsDeplacees().enClair();

        assertThat(lignesDePuce(texte))
                .as("une puce par nuit : la flèche seule sur sa ligne cassait l'appariement dès qu'il"
                        + " y avait deux nuits")
                .hasSize(2)
                .allSatisfy(ligne -> assertThat(ligne).contains("venait de"));
    }

    @Test
    @DisplayName("le dossier d'arrivée est nommé UNE fois, pas répété à chaque nuit")
    void dossier_d_arrivee_nomme_une_fois() {
        String texte = deuxNuitsDeplacees().enClair();

        assertThat(texte.split(java.util.regex.Pattern.quote(TRAVAIL), -1).length - 1)
                .as("il est le même pour toutes : le répéter allongeait chaque ligne au point de la"
                        + " faire revenir à la ligne")
                .isEqualTo(1);
        assertThat(texte)
                .as("mais il doit rester lisible : sans lui, l'utilisateur ne sait pas où chercher")
                .contains(TRAVAIL);
    }

    @Test
    @DisplayName("chaque nuit reste identifiable par son nom de dossier")
    void chaque_nuit_reste_identifiable() {
        String texte = deuxNuitsDeplacees().enClair();

        assertThat(texte).contains("Car640380-2026-Pass2-Z1").contains("Car130711-2026-Pass1-A1");
    }

    @Test
    @DisplayName("sans déplacement, le compte rendu reste court")
    void sans_deplacement_le_compte_rendu_reste_court() {
        BilanRestauration bilan = new BilanRestauration(
                true,
                List.of(new PlacementRacine("/media/disque/Nuit-01", "/media/disque/Nuit-01")),
                List.of(),
                RegimeRestauration.ENSEMBLE);

        assertThat(bilan.enClair())
                .as("rien n'a bougé : l'annoncer serait du bruit")
                .doesNotContain("venait de");
    }

    @Test
    @DisplayName("le régime dégradé appelle un regard, même si rien n'a bougé ni manqué")
    void le_regime_degrade_appelle_un_regard() {
        BilanRestauration bilan = new BilanRestauration(
                true,
                List.of(new PlacementRacine("/media/disque/Nuit-01", "/media/disque/Nuit-01")),
                List.of(),
                RegimeRestauration.RACINE_PAR_RACINE);

        assertThat(bilan.appelleUnRegard())
                .as("sinon le paragraphe qui dit la garantie moindre s'affiche sous un titre rassurant,"
                        + " et personne ne le lit")
                .isTrue();
    }

    private static BilanRestauration deuxNuitsDeplacees() {
        return new BilanRestauration(
                true,
                List.of(
                        new PlacementRacine(
                                "/media/disque-terrain/Car640380-2026-Pass2-Z1", TRAVAIL + "/Car640380-2026-Pass2-Z1"),
                        new PlacementRacine(
                                "/media/disque-terrain/Car130711-2026-Pass1-A1", TRAVAIL + "/Car130711-2026-Pass1-A1")),
                List.of(),
                RegimeRestauration.ENSEMBLE);
    }

    private static List<String> lignesDePuce(String texte) {
        return texte.lines().map(String::strip).filter(l -> l.startsWith("- ")).toList();
    }
}
