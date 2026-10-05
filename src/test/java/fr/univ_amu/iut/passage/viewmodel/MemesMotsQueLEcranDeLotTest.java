package fr.univ_amu.iut.passage.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.lot.viewmodel.TraitementViewModel;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/// **La vue d'un passage et l'écran de lot disent la même chose du même relevé** (#5862).
///
/// Le même relevé est restitué aux deux : à la carte « Traitement Vigie-Chiro » par son ViewModel, à la vue
/// du passage par son geste. Ce banc ne recopie aucune phrase : il confronte ce que l'un affiche à ce que
/// l'autre affiche. Une phrase réécrite d'un seul côté le fait rougir.
class MemesMotsQueLEcranDeLotTest {

    private static final Long ID_PASSAGE = 42L;
    private static final String COMPTE_RENDU = "Observations importées depuis Vigie-Chiro : 1284 observation(s).";
    private static final Horloge LE_30_SEPTEMBRE = Horloge.figeeAu(LocalDate.of(2026, 9, 30));

    private final SuiviTraitement suivi = mock(SuiviTraitement.class);
    private final ImportObservations importation = mock(ImportObservations.class);

    static Stream<Traitement> releves() {
        return Stream.of(
                new Traitement(EtatTraitement.PLANIFIE, "2026-09-30T14:07:45+00:00", null, null, null, null),
                new Traitement(EtatTraitement.EN_COURS, null, "2026-09-30T14:10:00+00:00", null, null, null),
                new Traitement(EtatTraitement.RETRY, null, "2026-09-30T14:10:00+00:00", null, null, 2),
                new Traitement(EtatTraitement.FINI, null, null, "2026-09-30T14:52:10+00:00", null, null),
                new Traitement(EtatTraitement.ERREUR, null, null, "2026-09-30T14:52:10+00:00", null, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releves")
    @DisplayName("le bandeau de la vue porte la phrase d'état de la carte, puis sa ligne d'import")
    void le_bandeau_porte_les_phrases_de_la_carte(Traitement traitement) {
        when(suivi.relever(ID_PASSAGE)).thenReturn(traitement);
        when(importation.importer(ID_PASSAGE, false)).thenReturn(COMPTE_RENDU);

        String carte = phrasesDeLaCarte();
        String bandeau = new VerificationDuTraitement(Optional.of(suivi), Optional.of(importation))
                .verifier(ID_PASSAGE)
                .texte();

        assertThat(bandeau).isEqualTo(carte);
    }

    @Test
    @DisplayName("une nuit déjà importée : la même phrase des deux côtés")
    void deja_importee_la_meme_phrase() {
        when(suivi.relever(ID_PASSAGE)).thenReturn(terminee());
        when(importation.aDejaSesObservations(ID_PASSAGE)).thenReturn(true);

        String carte = phrasesDeLaCarte();
        String bandeau = new VerificationDuTraitement(Optional.of(suivi), Optional.of(importation))
                .verifier(ID_PASSAGE)
                .texte();

        assertThat(bandeau).isEqualTo(carte).contains("déjà importées");
    }

    @Test
    @DisplayName("un import en échec : la même phrase, chaque écran y nommant son propre bouton")
    void import_en_echec_la_meme_phrase_au_bouton_pres() {
        when(suivi.relever(ID_PASSAGE)).thenReturn(terminee());
        when(importation.importer(ID_PASSAGE, false)).thenThrow(new RegleMetierException("HTTP 403"));

        String carte = phrasesDeLaCarte();
        String bandeau = new VerificationDuTraitement(Optional.of(suivi), Optional.of(importation))
                .verifier(ID_PASSAGE)
                .texte();

        assertThat(carte).contains("« Actualiser »");
        assertThat(bandeau)
                .isEqualTo(carte.replace("« Actualiser »", "« " + VerificationDuTraitement.GESTE + " »"))
                .contains("« Vérifier le traitement »");
    }

    /// Ce que la carte de l'écran de lot affiche pour le relevé courant : son état, puis sa ligne d'import.
    private String phrasesDeLaCarte() {
        TraitementViewModel carte =
                new TraitementViewModel(Optional.of(suivi), Optional.of(importation), LE_30_SEPTEMBRE);
        carte.appliquer(carte.releverEtImporter(ID_PASSAGE));
        return (carte.messageProperty().get() + " "
                        + carte.importObservationsProperty().get())
                .strip();
    }

    private static Traitement terminee() {
        return new Traitement(EtatTraitement.FINI, null, null, "2026-09-30T14:52:10+00:00", null, null);
    }
}
