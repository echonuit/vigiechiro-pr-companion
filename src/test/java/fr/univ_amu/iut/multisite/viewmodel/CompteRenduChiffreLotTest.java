package fr.univ_amu.iut.multisite.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.IssueTraitement;
import fr.univ_amu.iut.commun.model.ResultatTraitementGroupe;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Traduction d'un traitement en lot en compte rendu chiffré (#2757).
class CompteRenduChiffreLotTest {

    @Test
    @DisplayName("lot impeccable : une seule part, et le résultat ne s'encombre pas d'un « sur »")
    void lot_impeccable() {
        CompteRenduChiffre rendu = traduire(false, IssueTraitement.reussi(cible(1)), IssueTraitement.reussi(cible(2)));

        assertThat(rendu.titre()).isEqualTo("Préparer le dépôt");
        assertThat(rendu.resultat()).isEqualTo("2 traités");
        assertThat(rendu.severite()).isEqualTo(Severite.SUCCES);
        assertThat(rendu.ventilation().segments())
                .singleElement()
                .satisfies(part -> assertThat(part.libelle()).isEqualTo("Traités"));
        assertThat(rendu.motifs()).isEmpty();
    }

    @Test
    @DisplayName("un écart et un échec ne se fondent plus dans un « non traité(s) » commun")
    void un_ecart_et_un_echec_sont_distingues() {
        CompteRenduChiffre rendu = traduire(
                false,
                IssueTraitement.reussi(cible(1)),
                IssueTraitement.ecarte(cible(2), "dépôt déjà préparé"),
                IssueTraitement.echec(cible(3), "la plateforme a refusé"));

        // C'est la perte que la phrase infligeait : les deux appellent des conduites opposées, l'un est
        // déjà fait, l'autre demande qu'on y revienne.
        assertThat(rendu.ventilation().segments())
                .extracting(Segment::libelle, Segment::teinte)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Traités", Teinte.RETENU),
                        org.assertj.core.groups.Tuple.tuple("Écartés", Teinte.ECARTE),
                        org.assertj.core.groups.Tuple.tuple("En échec", Teinte.REFUSE));
        assertThat(rendu.severite())
                .as("un échec demande une reprise : le compte rendu doit le porter")
                .isEqualTo(Severite.AVERTISSEMENT);
    }

    @Test
    @DisplayName("un lot ARRÊTÉ ventile aussi ce qui n'a jamais été atteint, sinon la barre est refusée")
    void un_lot_arrete_nomme_ce_qui_n_a_pas_ete_atteint() {
        // Le quatrième statut n'existe que sur un lot arrêté. Sans lui, la somme des parts ne fait pas le
        // total et le constructeur de Ventilation refuse - ce test échouerait par exception, pas par
        // assertion. C'est ainsi que l'oubli s'est vu.
        CompteRenduChiffre rendu = traduire(
                true,
                IssueTraitement.reussi(cible(1)),
                IssueTraitement.nonTraite(cible(2)),
                IssueTraitement.nonTraite(cible(3)));

        assertThat(rendu.titre()).isEqualTo("Préparer le dépôt - interrompu");
        assertThat(rendu.ventilation().total()).isEqualTo(3);
        assertThat(rendu.ventilation().segments())
                .extracting(Segment::libelle, Segment::quantite, Segment::teinte)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Traités", 1L, Teinte.RETENU),
                        org.assertj.core.groups.Tuple.tuple("Non traités", 2L, Teinte.REFERENCE));
        assertThat(rendu.avertissements())
                .anySatisfy(avis -> assertThat(avis.texte()).contains("2 passage(s) restants"));
    }

    @Test
    @DisplayName("les motifs regroupent leurs sujets au lieu de répéter la même phrase")
    void les_motifs_regroupent_leurs_sujets() {
        CompteRenduChiffre rendu = traduire(
                false,
                IssueTraitement.ecarte(cible(1), "dépôt déjà préparé"),
                IssueTraitement.ecarte(cible(2), "dépôt déjà préparé"),
                IssueTraitement.echec(cible(3), "la plateforme a refusé"));

        assertThat(rendu.motifs())
                .extracting(Motif::libelle)
                .as("le préfixe de statut est conservé : sans lui, un écart et un échec se liraient pareil")
                .containsExactly("écarté : dépôt déjà préparé", "échec : la plateforme a refusé");
        assertThat(rendu.motifs().get(0).sujets()).hasSize(2);
    }

    private static CompteRenduChiffre traduire(boolean interrompu, IssueTraitement... issues) {
        return CompteRenduChiffreLot.de(
                new ResultatTraitementGroupe("Préparer le dépôt", List.of(issues), interrompu), List.of());
    }

    private static CiblePassage cible(long id) {
        return new CiblePassage(id, "640380 / A1 / 2026 n°" + id);
    }
}
