package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Issues d une demande de lancement du traitement (#1261) : la question que pose l IHM comme la CLI est
/// « le serveur travaille-t-il, oui ou non ? » — et un refus « deja en cours » repond oui.
class ResultatLancementTest {

    @Test
    @DisplayName("traitementEnRoute : vrai si accepte OU deja lance (dans les deux cas, il n y a qu a attendre)")
    void traitement_en_route() {
        assertThat(ResultatLancement.accepte().traitementEnRoute()).isTrue();
        assertThat(ResultatLancement.dejaLance(enCours()).traitementEnRoute()).isTrue();
    }

    @Test
    @DisplayName("traitementEnRoute : faux pour un refus, une relance bloquee ou un serveur injoignable")
    void traitement_pas_en_route() {
        assertThat(ResultatLancement.refuse(403, "interdit").traitementEnRoute())
                .isFalse();
        assertThat(ResultatLancement.relanceBloquee(enCours()).traitementEnRoute())
                .isFalse();
        assertThat(ResultatLancement.injoignable().traitementEnRoute()).isFalse();
    }

    @Test
    @DisplayName("refuse : le detail porte le statut HTTP et le corps, pour un message exploitable")
    void refus_detaille() {
        ResultatLancement refus = ResultatLancement.refuse(422, "champ invalide");

        assertThat(refus.issue()).isEqualTo(IssueLancement.REFUSE);
        assertThat(refus.détail()).contains("422", "champ invalide");
    }

    @Test
    @DisplayName("les issues sans etat relu portent un traitement absent, jamais null")
    void etat_absent_plutot_que_null() {
        assertThat(ResultatLancement.accepte().traitement()).isEqualTo(Traitement.absent());
        assertThat(ResultatLancement.injoignable().traitement()).isEqualTo(Traitement.absent());
        assertThat(ResultatLancement.refuse(500, "boum").traitement()).isEqualTo(Traitement.absent());
    }

    private static Traitement enCours() {
        return new Traitement(EtatTraitement.EN_COURS, null, null, null, null, null);
    }
}
