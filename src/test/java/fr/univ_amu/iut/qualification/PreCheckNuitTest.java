package fr.univ_amu.iut.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.ResultatVerification;
import fr.univ_amu.iut.qualification.model.PreCheckNuit;
import fr.univ_amu.iut.qualification.model.PreCheckNuit.Diagnostic;
import fr.univ_amu.iut.qualification.model.PreCheckNuit.Feu;
import fr.univ_amu.iut.qualification.model.PreCheckNuit.Mesures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests purs (sans base) du moteur [PreCheckNuit] : on alimente directement les [Mesures]
/// et on vérifie les trois feux ainsi que la conversion en [ResultatVerification].
class PreCheckNuitTest {

    private final PreCheckNuit preCheck = new PreCheckNuit();

    @Test
    @DisplayName("Nuit saine : trois feux verts, résultat conforme et non bloquant")
    void nuit_saine_tout_au_vert() {
        Diagnostic diagnostic = preCheck.evaluer(new Mesures(200, 0, 0, false));

        assertThat(diagnostic.couvertureHoraire()).isEqualTo(Feu.VERT);
        assertThat(diagnostic.nombreFichiers()).isEqualTo(Feu.VERT);
        assertThat(diagnostic.coherenceRenommage()).isEqualTo(Feu.VERT);
        assertThat(diagnostic.toutAuVert()).isTrue();
        assertThat(diagnostic.versResultatVerification().estConforme()).isTrue();
    }

    @Test
    @DisplayName("Nombre de fichiers : 0 → rouge, creux → orange, suffisant → vert")
    void feu_nombre_de_fichiers() {
        assertThat(preCheck.evaluer(new Mesures(0, 0, 0, false)).nombreFichiers())
                .isEqualTo(Feu.ROUGE);
        assertThat(preCheck.evaluer(new Mesures(30, 0, 0, false)).nombreFichiers())
                .isEqualTo(Feu.ORANGE);
        assertThat(preCheck.evaluer(new Mesures(120, 0, 0, false)).nombreFichiers())
                .isEqualTo(Feu.VERT);
    }

    @Test
    @DisplayName("Cohérence du renommage : un seul fichier divergent → rouge")
    void feu_renommage() {
        assertThat(preCheck.evaluer(new Mesures(120, 0, 0, false)).coherenceRenommage())
                .isEqualTo(Feu.VERT);
        assertThat(preCheck.evaluer(new Mesures(120, 1, 0, false)).coherenceRenommage())
                .isEqualTo(Feu.ROUGE);
    }

    @Test
    @DisplayName("Couverture horaire : écart > 30 min → orange, moitié de nuit manquante → rouge")
    void feu_couverture() {
        assertThat(preCheck.evaluer(new Mesures(120, 0, 20, false)).couvertureHoraire())
                .isEqualTo(Feu.VERT);
        assertThat(preCheck.evaluer(new Mesures(120, 0, 45, false)).couvertureHoraire())
                .isEqualTo(Feu.ORANGE);
        assertThat(preCheck.evaluer(new Mesures(120, 0, 400, true)).couvertureHoraire())
                .isEqualTo(Feu.ROUGE);
    }

    @Test
    @DisplayName("Conversion ResultatVerification : feux non verts → alertes soft, jamais bloquantes")
    void conversion_en_resultat_verification() {
        // Couverture orange + renommage rouge + nombre vert ⇒ deux alertes (consultatives).
        Diagnostic diagnostic = preCheck.evaluer(new Mesures(120, 2, 60, false));

        ResultatVerification resultat = diagnostic.versResultatVerification();
        assertThat(resultat.estConforme()).isFalse();
        assertThat(resultat.estBloquant())
                .as("le pré-check est consultatif (R13)")
                .isFalse();
        assertThat(resultat.messages()).hasSize(2);
    }

    @Test
    @DisplayName("Des mesures négatives sont refusées")
    void mesures_negatives_refusees() {
        assertThatThrownBy(() -> new Mesures(-1, 0, 0, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("#1506 : la couverture explique la mesure et l'écart (plages + minutes)")
    void detail_couverture_dit_les_plages_et_l_ecart() {
        Mesures orange = new Mesures(120, 0, 45, false, "20:25 à 07:47", "20:57 à 07:12");
        Diagnostic diagnostic = preCheck.evaluer(orange);

        assertThat(diagnostic.couvertureHoraire()).isEqualTo(Feu.ORANGE);
        assertThat(diagnostic.detailCouverture())
                .contains("enregistrements de 20:25 à 07:47")
                .contains("nuit de 20:57 à 07:12")
                .contains("écart de 45 min");
    }

    @Test
    @DisplayName("#1506 : moitié de nuit manquante → « une partie est diurne »")
    void detail_couverture_moitie_manquante() {
        Diagnostic diagnostic = preCheck.evaluer(new Mesures(120, 0, 400, true, "12:00 à 14:00", "20:57 à 07:12"));

        assertThat(diagnostic.couvertureHoraire()).isEqualTo(Feu.ROUGE);
        assertThat(diagnostic.detailCouverture()).contains("diurne");
    }

    @Test
    @DisplayName("#1506 : le nombre de fichiers dit le compte observé et le seuil attendu")
    void detail_nombre_dit_le_compte_et_le_seuil() {
        Diagnostic diagnostic = preCheck.evaluer(new Mesures(6, 0, 0, false));

        assertThat(diagnostic.nombreFichiers()).isEqualTo(Feu.ORANGE);
        assertThat(diagnostic.detailNombre())
                .contains("6 originaux")
                .contains(Integer.toString(PreCheckNuit.SEUIL_FICHIERS_CREUX));
    }

    @Test
    @DisplayName("#1506 : le renommage dit combien de fichiers divergent du préfixe")
    void detail_renommage_compte_les_fichiers_hors_prefixe() {
        Diagnostic diagnostic = preCheck.evaluer(new Mesures(120, 3, 0, false));

        assertThat(diagnostic.coherenceRenommage()).isEqualTo(Feu.ROUGE);
        assertThat(diagnostic.detailRenommage()).contains("3 fichiers").contains("préfixe");
    }

    @Test
    @DisplayName("#1506 : le résumé de la barre de statut nomme les feux en cause (rouges)")
    void resume_nomme_les_feux_en_cause() {
        // Nombre = 0 (rouge) + renommage divergent (rouge) + couverture verte.
        Diagnostic diagnostic = preCheck.evaluer(new Mesures(0, 2, 0, false));

        assertThat(diagnostic.resumeAnomalie())
                .contains("nombre de fichiers")
                .contains("cohérence du renommage")
                .doesNotContain("couverture horaire");
    }

    @Test
    @DisplayName("#1506 : aucun feu rouge → résumé vide")
    void resume_vide_sans_anomalie() {
        assertThat(preCheck.evaluer(new Mesures(200, 0, 0, false)).resumeAnomalie())
                .isEmpty();
    }
}
