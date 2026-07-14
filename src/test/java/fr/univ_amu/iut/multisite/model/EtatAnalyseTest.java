package fr.univ_amu.iut.multisite.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// Tests unitaires de [EtatAnalyse] (#1338) : la déduction qui répond, en une colonne, à « lesquelles de
/// mes nuits sont prêtes à être importées ? ».
class EtatAnalyseTest {

    private static final long ID = 7L;
    private static final String RELEVE_LE = "2026-07-14T09:00:00Z";

    private static Optional<ReleveTraitement> releve(EtatTraitement etat) {
        return Optional.of(new ReleveTraitement(
                ID, "participation-1", new Traitement(etat, null, null, null, null, null), RELEVE_LE));
    }

    @ParameterizedTest
    @EnumSource(
            value = StatutWorkflow.class,
            names = {"DEPOSE"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Une nuit non déposée n'a pas d'analyse à suivre : sans objet, quel que soit le reste")
    void non_depose_sans_objet(StatutWorkflow statut) {
        assertThat(EtatAnalyse.deduire(statut, releve(EtatTraitement.FINI), false))
                .isEqualTo(EtatAnalyse.SANS_OBJET);
        assertThat(EtatAnalyse.SANS_OBJET.libelle())
                .as("cellule vide dans le tableau : un libellé ferait du bruit sur la majorité des lignes")
                .isEmpty();
    }

    @Test
    @DisplayName("Déposée mais jamais interrogée : on ne SAIT pas, et on le dit (≠ « jamais lancée »)")
    void jamais_releve() {
        assertThat(EtatAnalyse.deduire(StatutWorkflow.DEPOSE, Optional.empty(), false))
                .isEqualTo(EtatAnalyse.JAMAIS_RELEVE);
    }

    @Test
    @DisplayName("Le serveur répond, et n'a jamais calculé la nuit : jamais lancée")
    void jamais_lancee() {
        Optional<ReleveTraitement> jamaisCalculee =
                Optional.of(new ReleveTraitement(ID, "participation-1", Traitement.absent(), RELEVE_LE));

        assertThat(EtatAnalyse.deduire(StatutWorkflow.DEPOSE, jamaisCalculee, false))
                .isEqualTo(EtatAnalyse.JAMAIS_LANCEE);
    }

    @ParameterizedTest
    @EnumSource(
            value = EtatTraitement.class,
            names = {"PLANIFIE", "EN_COURS", "RETRY"})
    @DisplayName("Planifiée, en cours ou relancée : le serveur travaille, il n'y a qu'à attendre")
    void serveur_au_travail(EtatTraitement etat) {
        assertThat(EtatAnalyse.deduire(StatutWorkflow.DEPOSE, releve(etat), false))
                .isEqualTo(EtatAnalyse.EN_COURS);
    }

    @Test
    @DisplayName("Analyse en échec définitif")
    void en_echec() {
        assertThat(EtatAnalyse.deduire(StatutWorkflow.DEPOSE, releve(EtatTraitement.ERREUR), false))
                .isEqualTo(EtatAnalyse.EN_ECHEC);
    }

    @Test
    @DisplayName("#1338 : FINI + résultats absents = À IMPORTER — la seule ligne qui demande une action")
    void fini_sans_resultats_est_a_importer() {
        EtatAnalyse etat = EtatAnalyse.deduire(StatutWorkflow.DEPOSE, releve(EtatTraitement.FINI), false);

        assertThat(etat).isEqualTo(EtatAnalyse.A_IMPORTER);
        assertThat(etat.aImporter())
                .as("c'est le prédicat de la vue mémorisée « Résultats à importer »")
                .isTrue();
    }

    @Test
    @DisplayName("#1338 : FINI + résultats DÉJÀ en base = importée — sans ce croisement, la vue mentirait")
    void fini_avec_resultats_nest_plus_a_importer() {
        EtatAnalyse etat = EtatAnalyse.deduire(StatutWorkflow.DEPOSE, releve(EtatTraitement.FINI), true);

        assertThat(etat)
                .as("« analyse terminée » et « observations déjà importées » sont deux questions distinctes :"
                        + " sans les croiser, la vue proposerait indéfiniment des nuits déjà traitées")
                .isEqualTo(EtatAnalyse.IMPORTEE);
        assertThat(etat.aImporter()).isFalse();
    }

    @Test
    @DisplayName("Tout état affichable porte un badge et une infobulle (aucun état muet)")
    void chaque_etat_sait_se_presenter() {
        for (EtatAnalyse etat : EtatAnalyse.values()) {
            assertThat(etat.classeBadge()).as("classe de badge de %s", etat).isNotBlank();
            assertThat(etat.infobulle()).as("infobulle de %s", etat).isNotBlank();
        }
    }
}
