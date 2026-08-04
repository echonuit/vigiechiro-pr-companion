package fr.univ_amu.iut.audit.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.audit.model.CategorieConstat;
import fr.univ_amu.iut.audit.model.ConstatAudit;
import fr.univ_amu.iut.commun.model.Severite;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le catalogue de critères de l'**Audit de cohérence** (#3100), cinquième écran posé sur le socle.
///
/// Tests **purs** : la recherche texte et les valeurs présentes ne demandent aucun toolkit JavaFX. Ce
/// qui touche aux éditeurs est déjà gardé par les tests du socle.
class CriteresAuditTest {

    private static final ConstatAudit DISQUE_MANQUANT = new ConstatAudit(
            Severite.ERREUR, CategorieConstat.DISQUE_MANQUANT, 42L, "PaRec_1.wav", "Le fichier est absent du disque.");

    private static final ConstatAudit SANS_PASSAGE = new ConstatAudit(
            Severite.AVERTISSEMENT, CategorieConstat.SERVEUR_INJOIGNABLE, null, "Z1", "Aucun passage lié.");

    @Test
    @DisplayName("#3100 : la recherche couvre la cible ET le détail, insensible à la casse")
    void la_recherche_couvre_la_cible_et_le_detail() {
        // Ce sont les deux colonnes en texte libre de la table : le reste se filtre par des puces. Une
        // recherche qui ne couvrirait que la cible laisserait l'essentiel du constat hors de portée.
        assertThat(CriteresAudit.rechercheTexte().test(DISQUE_MANQUANT, "parec"))
                .isTrue();
        assertThat(CriteresAudit.rechercheTexte().test(DISQUE_MANQUANT, "absent"))
                .isTrue();
        assertThat(CriteresAudit.rechercheTexte().test(DISQUE_MANQUANT, "marseille"))
                .isFalse();
    }

    @Test
    @DisplayName("#3100 : un constat sans champ ne fait pas échouer la recherche")
    void un_constat_sans_champ_ne_casse_pas_la_recherche() {
        ConstatAudit creux = new ConstatAudit(Severite.INFO, CategorieConstat.DISQUE_ORPHELIN, null, null, null);

        assertThat(CriteresAudit.rechercheTexte().test(creux, "quoi que ce soit"))
                .isFalse();
    }

    @Test
    @DisplayName("#3100 : les passages offerts sont ceux présents, distincts et triés")
    void les_passages_offerts_sont_ceux_presents() {
        ConstatAudit autrePassage =
                new ConstatAudit(Severite.INFO, CategorieConstat.DISQUE_ORPHELIN, 7L, "PaRec_2.wav", "Détail.");

        List<String> passages =
                CriteresAudit.passagesPresents(List.of(DISQUE_MANQUANT, autrePassage, DISQUE_MANQUANT, SANS_PASSAGE));

        assertThat(passages)
                .as("un constat sans passage n'entre pas dans la liste : il n'y a rien à y désigner")
                .containsExactly("7", "42");
    }

    @Test
    @DisplayName("#3100 : les énumérations se nomment en français, pas par leur name()")
    void les_enumerations_se_nomment_en_francais() {
        // Trouvé en préparant les critères : la table affichait « PREFIXE_NON_CONFORME » et
        // « AVERTISSEMENT », des identifiants de code au milieu d'une interface française. Poser des
        // puces sur ces valeurs aurait propagé le défaut dans les menus.
        assertThat(CategorieConstat.PREFIXE_NON_CONFORME.libelle()).isEqualTo("Préfixe non conforme");
        assertThat(Severite.AVERTISSEMENT.libelle()).isEqualTo("Avertissement");

        for (CategorieConstat categorie : CategorieConstat.values()) {
            assertThat(categorie.libelle())
                    .as("%s doit avoir un libellé lisible, sans tiret bas ni majuscules", categorie)
                    .isNotBlank()
                    .doesNotContain("_")
                    .isNotEqualTo(categorie.name());
        }
    }
}
