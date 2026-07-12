package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.viewmodel.EtatUnite;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Table de dépôt (#983) côté ViewModel : traduction de l'état persisté (`depot_unite`, #981) en lignes
/// observables, ciblage des événements du moteur (#982) par identifiant, et drapeau « reste à
/// reprendre » qui bascule le bouton en « Retenter les échecs ». Purement observable, sans toolkit.
class SuiviLignesDepotTest {

    private static final String MAINTENANT = "2026-07-11T14:00:00";

    @Test
    @DisplayName(
            "planifier() traduit chaque statut persisté : à déposer/interrompu → attente, déposé → terminée, échec → échec")
    void planifier_traduit_les_statuts_persistes() {
        SuiviLignesDepot suivi = new SuiviLignesDepot();

        suivi.planifier(List.of(
                unite(1L, "a.zip", StatutDepotUnite.A_DEPOSER, null),
                unite(2L, "b.zip", StatutDepotUnite.EN_COURS, null),
                unite(3L, "c.zip", StatutDepotUnite.DEPOSE, null),
                unite(4L, "d.zip", StatutDepotUnite.ECHEC, "HTTP 503")));

        assertThat(suivi.lignes())
                .extracting(LigneDepot::identifiant)
                .containsExactly("a.zip", "b.zip", "c.zip", "d.zip");
        assertThat(suivi.lignes().get(0).etatProperty().get()).isEqualTo(EtatUnite.EN_ATTENTE);
        // Interrompue sans confirmation : re-tentée à la reprise, donc affichée « en attente ».
        assertThat(suivi.lignes().get(1).etatProperty().get()).isEqualTo(EtatUnite.EN_ATTENTE);
        assertThat(suivi.lignes().get(2).etatProperty().get()).isEqualTo(EtatUnite.TERMINEE);
        assertThat(suivi.lignes().get(3).etatProperty().get()).isEqualTo(EtatUnite.ECHEC);
        assertThat(suivi.lignes().get(3).raisonEchecProperty().get()).isEqualTo("HTTP 503");
        assertThat(suivi.resteAReprendreProperty().get()).isTrue();
    }

    @Test
    @DisplayName("les événements du moteur ciblent la ligne par identifiant : démarrée → déposée / échouée")
    void evenements_cibles_par_identifiant() {
        SuiviLignesDepot suivi = new SuiviLignesDepot();
        suivi.planifier(List.of(
                unite(1L, "a.zip", StatutDepotUnite.A_DEPOSER, null),
                unite(2L, "b.zip", StatutDepotUnite.A_DEPOSER, null)));

        suivi.demarree("a.zip");
        assertThat(suivi.lignes().get(0).etatProperty().get()).isEqualTo(EtatUnite.EN_COURS);

        suivi.deposee("a.zip");
        suivi.echouee("b.zip", "coupure réseau");

        assertThat(suivi.lignes().get(0).etatProperty().get()).isEqualTo(EtatUnite.TERMINEE);
        assertThat(suivi.lignes().get(1).etatProperty().get()).isEqualTo(EtatUnite.ECHEC);
        assertThat(suivi.lignes().get(1).raisonEchecProperty().get()).isEqualTo("coupure réseau");
        assertThat(suivi.resteAReprendreProperty().get()).isTrue();
    }

    @Test
    @DisplayName("#984 : compteur honnête — déposées / en cours / échecs / total suivent les états des lignes")
    void compteurs_honnetes_suivent_les_etats() {
        SuiviLignesDepot suivi = new SuiviLignesDepot();
        suivi.planifier(List.of(
                unite(1L, "a.zip", StatutDepotUnite.A_DEPOSER, null),
                unite(2L, "b.zip", StatutDepotUnite.A_DEPOSER, null),
                unite(3L, "c.zip", StatutDepotUnite.A_DEPOSER, null)));
        assertThat(suivi.totalProperty().get()).isEqualTo(3);
        assertThat(suivi.deposeesProperty().get()).isZero();
        assertThat(suivi.enCoursProperty().get()).isZero();
        assertThat(suivi.echecsProperty().get()).isZero();

        suivi.demarree("a.zip");
        suivi.demarree("b.zip");
        assertThat(suivi.enCoursProperty().get())
                .as("2 unités en vol (dépôt parallèle)")
                .isEqualTo(2);

        suivi.deposee("a.zip");
        suivi.echouee("b.zip", "HTTP 503");
        assertThat(suivi.deposeesProperty().get()).isEqualTo(1);
        assertThat(suivi.echecsProperty().get()).isEqualTo(1);
        assertThat(suivi.enCoursProperty().get()).as("plus rien en vol").isZero();
        assertThat(suivi.totalProperty().get()).isEqualTo(3);
    }

    @Test
    @DisplayName("#984 : progresse() alimente la barre déterminée de la ligne, sans reculer")
    void progresse_alimente_la_barre() {
        SuiviLignesDepot suivi = new SuiviLignesDepot();
        suivi.planifier(List.of(unite(1L, "a.zip", StatutDepotUnite.A_DEPOSER, null)));
        suivi.demarree("a.zip");

        suivi.progresse("a.zip", 0.42);
        assertThat(suivi.lignes().get(0).fractionProperty().get()).isEqualTo(0.42);

        // Monotone : le socle ne recule pas la barre (une fraction inférieure est ignorée).
        suivi.progresse("a.zip", 0.10);
        assertThat(suivi.lignes().get(0).fractionProperty().get()).isEqualTo(0.42);
    }

    @Test
    @DisplayName("resteAReprendre : faux sans plan, vrai avec du reste, faux quand tout est déposé")
    void reste_a_reprendre_suit_les_lignes() {
        SuiviLignesDepot suivi = new SuiviLignesDepot();
        assertThat(suivi.resteAReprendreProperty().get())
                .as("aucun dépôt entamé")
                .isFalse();

        suivi.planifier(List.of(unite(1L, "a.zip", StatutDepotUnite.A_DEPOSER, null)));
        assertThat(suivi.resteAReprendreProperty().get()).isTrue();

        suivi.deposee("a.zip");
        assertThat(suivi.resteAReprendreProperty().get()).isFalse();

        suivi.reinitialiser();
        assertThat(suivi.resteAReprendreProperty().get()).isFalse();
        assertThat(suivi.lignes()).isEmpty();
    }

    private static DepotUnite unite(Long id, String identifiant, StatutDepotUnite statut, String erreur) {
        return new DepotUnite(id, 42L, identifiant, TypeDepotUnite.ZIP, statut, null, erreur, MAINTENANT);
    }
}
