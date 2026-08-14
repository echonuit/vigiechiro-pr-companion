package fr.univ_amu.iut.analyse.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.ClasseActivite;
import fr.univ_amu.iut.commun.model.ContexteActivite;
import fr.univ_amu.iut.commun.model.ReferentielActivite;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie la **synthèse d'une nuit** (#2351) : le comptage, la distinction contacts / fichiers, la
/// bascule « validées seulement », et ce que le tableau écrit quand il n'a pas de classe à donner.
class AgregationSyntheseTest {

    private static ReferentielActivite referentiel() throws IOException {
        return ReferentielActivite.lire(new StringReader(String.join(
                "\n",
                "Pipkuh;national;toutes;10;100;1000;9000;Tres bonne",
                "Barbar;national;toutes;2;16;181;4770;Tres bonne")));
    }

    private static LigneObservationAudio contact(String taxon, String fichier, StatutObservation statut) {
        return new LigneObservationAudio(
                1L, // idObservation
                1L, // idSequence
                1L, // idPassage
                1, // numeroPassage
                "2026-07-03",
                "130711",
                "Z41",
                "Test",
                taxon, // taxonTadarida
                0.9,
                null, // taxonObservateur : le taxon retenu est donc celui de Tadarida
                null,
                statut,
                false,
                null,
                45,
                taxon == null ? null : taxon + " (nom)", // nomEspece
                null, // nomTadarida
                null, // latinTadarida
                "Chiroptères",
                fichier,
                0.0,
                5.0,
                LocalDateTime.of(2026, 7, 3, 22, 0),
                false,
                null,
                null,
                null,
                null,
                0,
                null);
    }

    @Test
    @DisplayName("Une ligne par espèce, contacts cumulés et fichiers DISTINCTS comptés à part")
    void contacts_et_fichiers_distincts() throws IOException {
        // Trois contacts de Pipkuh, mais sur deux fichiers seulement : l'activité est concentrée. Compter
        // les fichiers une fois chacun est ce qui distingue un individu devant le micro d'une activité
        // diffuse toute la nuit.
        List<LigneSynthese> synthese = AgregationSynthese.de(
                List.of(
                        contact("Pipkuh", "seqA.wav", StatutObservation.VALIDEE),
                        contact("Pipkuh", "seqA.wav", StatutObservation.VALIDEE),
                        contact("Pipkuh", "seqB.wav", StatutObservation.VALIDEE),
                        contact("Barbar", "seqC.wav", StatutObservation.VALIDEE)),
                false,
                referentiel(),
                ContexteActivite.NATIONAL);

        assertThat(synthese).hasSize(2);
        assertThat(synthese.get(0).codeTaxon())
                .as("le plus contacté en premier")
                .isEqualTo("Pipkuh");
        assertThat(synthese.get(0).contacts()).isEqualTo(3);
        assertThat(synthese.get(0).fichiers())
                .as("deux fichiers pour trois contacts")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("« Validées seulement » RECALCULE : une espèce non validée disparaît, les autres baissent")
    void validees_seulement_recalcule() throws IOException {
        List<LigneObservationAudio> lignes = List.of(
                contact("Pipkuh", "seqA.wav", StatutObservation.VALIDEE),
                contact("Pipkuh", "seqB.wav", StatutObservation.NON_TOUCHEE),
                contact("Barbar", "seqC.wav", StatutObservation.NON_TOUCHEE));

        List<LigneSynthese> tout = AgregationSynthese.de(lignes, false, referentiel(), ContexteActivite.NATIONAL);
        List<LigneSynthese> validees = AgregationSynthese.de(lignes, true, referentiel(), ContexteActivite.NATIONAL);

        assertThat(tout).hasSize(2);
        assertThat(validees)
                .as("la Barbastelle, jamais validée, sort du tableau : elle n'est pas seulement masquée")
                .hasSize(1);
        assertThat(validees.get(0).contacts())
                .as("et la Pipistrelle retombe à son seul contact validé")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Une séquence non identifiée n'est pas une espèce : elle n'a pas de ligne")
    void sequence_non_identifiee_ecartee() throws IOException {
        List<LigneSynthese> synthese = AgregationSynthese.de(
                List.of(
                        contact(null, "seqA.wav", StatutObservation.NON_TOUCHEE),
                        contact("Pipkuh", "seqB.wav", StatutObservation.NON_TOUCHEE)),
                false,
                referentiel(),
                ContexteActivite.NATIONAL);

        assertThat(synthese).extracting(LigneSynthese::codeTaxon).containsExactly("Pipkuh");
    }

    @Test
    @DisplayName("La classe se calcule sur les contacts, et les quantiles l'accompagnent")
    void classe_et_quantiles() throws IOException {
        List<LigneObservationAudio> lignes = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) {
            lignes.add(contact("Pipkuh", "seq" + i + ".wav", StatutObservation.VALIDEE));
        }

        LigneSynthese ligne = AgregationSynthese.de(lignes, false, referentiel(), ContexteActivite.NATIONAL)
                .get(0);

        assertThat(ligne.classe()).as("150 contacts, Q75 = 100, Q98 = 1000").contains(ClasseActivite.FORTE);
        assertThat(ligne.seuils())
                .as("les quantiles s'affichent à côté : une classe seule est un verdict")
                .isPresent();
        assertThat(ligne.libelleClasse()).isEqualTo("Forte");
    }

    @Test
    @DisplayName("Hors référentiel, la cellule DIT pourquoi plutôt que de rester vide")
    void hors_referentiel_explicite() throws IOException {
        // Une cellule blanche se lirait comme une donnée manquante. Ici l'absence a un sens, et il
        // diffère selon le cas : taxon non couvert, ou couvert sans seuil pour ce contexte.
        List<LigneSynthese> synthese = AgregationSynthese.de(
                List.of(contact("Tetvir", "seqA.wav", StatutObservation.VALIDEE)),
                false,
                referentiel(),
                ContexteActivite.NATIONAL);

        LigneSynthese orthoptere = synthese.get(0);
        assertThat(orthoptere.classe()).isEmpty();
        assertThat(orthoptere.couvertParLeReferentiel()).isFalse();
        assertThat(orthoptere.libelleClasse()).isEqualTo("Non couvert par le référentiel");
    }

    @Test
    @DisplayName("Une nuit sans aucune identification validée rend un tableau VIDE, pas une erreur")
    void nuit_sans_validation() throws IOException {
        List<LigneSynthese> synthese = AgregationSynthese.de(
                List.of(contact("Pipkuh", "seqA.wav", StatutObservation.NON_TOUCHEE)),
                true,
                referentiel(),
                ContexteActivite.NATIONAL);

        assertThat(synthese).isEmpty();
    }

    @Test
    @DisplayName("Le tri par contacts décroissants prime sur l'ordre d'arrivée des espèces")
    void tri_par_contacts_decroissants_meme_arrivee_en_second() throws IOException {
        // La Barbastelle arrive EN PREMIER dans le flux mais avec moins de contacts : si le tri
        // disparaissait, l'ordre resterait celui d'insertion (Barbar, Pipkuh) et ce test ne le
        // distinguerait pas d'un simple contenu correct.
        List<LigneSynthese> synthese = AgregationSynthese.de(
                List.of(
                        contact("Barbar", "seqC.wav", StatutObservation.VALIDEE),
                        contact("Pipkuh", "seqA.wav", StatutObservation.VALIDEE),
                        contact("Pipkuh", "seqA.wav", StatutObservation.VALIDEE),
                        contact("Pipkuh", "seqB.wav", StatutObservation.VALIDEE)),
                false,
                referentiel(),
                ContexteActivite.NATIONAL);

        assertThat(synthese)
                .extracting(LigneSynthese::codeTaxon)
                .as("Pipkuh (3 contacts) doit passer devant Barbar (1 contact) malgré son arrivée plus tardive")
                .containsExactly("Pipkuh", "Barbar");
    }

    @Test
    @DisplayName("Sans nom vernaculaire connu, la ligne retombe sur le code taxon")
    void nom_espece_absent_retombe_sur_le_code_taxon() throws IOException {
        List<LigneSynthese> synthese = AgregationSynthese.de(
                List.of(contactSansNomEspece("Tetvir", "seqA.wav", StatutObservation.VALIDEE)),
                false,
                referentiel(),
                ContexteActivite.NATIONAL);

        assertThat(synthese.get(0).nomEspece())
                .as("aucune cellule vide : à défaut de nom vernaculaire, le code taxon sert d'étiquette")
                .isEqualTo("Tetvir");
    }

    private static LigneObservationAudio contactSansNomEspece(String taxon, String fichier, StatutObservation statut) {
        return new LigneObservationAudio(
                1L,
                1L,
                1L,
                1,
                "2026-07-03",
                "130711",
                "Z41",
                "Test",
                taxon,
                0.9,
                null,
                null,
                statut,
                false,
                null,
                45,
                null, // nomEspece : justement ce que ce test met à l'épreuve
                null,
                null,
                "Chiroptères",
                fichier,
                0.0,
                5.0,
                LocalDateTime.of(2026, 7, 3, 22, 0),
                false,
                null,
                null,
                null,
                null,
                0,
                null);
    }
}
